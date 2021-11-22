package de.cxp.ocs.elasticsearch.prodset;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.elasticsearch.ResultMapper;
import de.cxp.ocs.elasticsearch.Searcher;
import de.cxp.ocs.elasticsearch.query.MasterVariantQuery;
import de.cxp.ocs.model.params.DynamicProductSet;
import de.cxp.ocs.model.params.ProductSet;
import de.cxp.ocs.model.params.StaticProductSet;
import de.cxp.ocs.model.result.ResultHit;
import de.cxp.ocs.model.result.SearchResult;
import de.cxp.ocs.model.result.SearchResultSlice;
import de.cxp.ocs.util.InternalSearchParams;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Hero products that should be displayed at the top of the result, come with
 * several uncomfortable requirements. This class helps to handle all of them:
 * <ul>
 * <li>hero products should also be reflected by the facets, so follow up
 * requests with filters should also remove non-matching hero products.
 * Result counts and facet counts of course should reflect this behavior.</li>
 * <li>hero products should also be under the control of result-pages. This is
 * especially important if the page-size/result limit is lower than the amount
 * of hero products!</li>
 * <li>If several sets of hero products are defined, make sure the same product
 * does not appear in more that one.</li>
 * <li>Multiple hero product sets should return in the order as they have been
 * requested</li>
 * <li>Static product sets and dynamic product sets with sorting should return
 * the single hits in the order they have been requested.
 * (not yet guaranteed TODO)</li>
 * <li></li>
 * </ul>
 * 
 * @author Rudolf Batt
 */
@Slf4j
public class HeroProductHandler {

	@NonNull
	private final static Map<String, ProductSetResolver> resolvers = new HashMap<>(2);
	static {
		resolvers.put(new DynamicProductSet().type, new DynamicProductSetResolver());
		resolvers.put(new StaticProductSet().type, new StaticProductSetResolver());
	}

	/**
	 * Resolve the given product sets into static product sets. For given static
	 * product sets, the specified IDs are verified. For dynamic product sets,
	 * the matching IDs are fetched.
	 * 
	 * @param productSets
	 * @param searcher
	 * @param searchContext
	 * @return
	 */
	public static StaticProductSet[] resolve(ProductSet[] productSets, Searcher searcher, SearchContext searchContext) {
		StaticProductSet[] resolvedSets = new StaticProductSet[productSets.length];
		@SuppressWarnings("unchecked")
		CompletableFuture<Void>[] futures = new CompletableFuture[productSets.length];
		int nextPos = 0;
		// fetch extra products for dynamic product sets, in case there are
		// overlapping IDs
		final int[] extraBuffer = new int[] { 0 };
		for (ProductSet set : productSets) {
			int position = nextPos++;

			final ProductSetResolver resolver = resolvers.get(set.getType());

			if (resolver == null) {
				log.error("No resolver found for product set type '{}'", set.getType());
				resolvedSets[position] = new StaticProductSet().setIds(new String[0]).setName(set.getName());
				futures[position] = CompletableFuture.completedFuture(null);
			}
			// only run async, if there are more than 1 sets
			else if (resolver.runAsync() && productSets.length > 1) {
				futures[position] = CompletableFuture.supplyAsync(() -> resolver.resolve(set, extraBuffer[0], searcher, searchContext))
						.thenAccept(resolvedIds -> resolvedSets[position] = resolvedIds);
				extraBuffer[0] += set.getSize();
			}
			else {
				resolvedSets[position] = resolver.resolve(set, extraBuffer[0], searcher, searchContext);
				extraBuffer[0] += set.getSize();
				futures[position] = CompletableFuture.completedFuture(null);
			}
		}
		try {
			CompletableFuture.allOf(futures).join();

			if (productSets.length > 1) {
				// deduplicate ids from the different sets
				deduplicate(productSets, resolvedSets);
			}
		}
		catch (Exception e) {
			log.error("resolving product sets failed", e);
		}

		return resolvedSets;
	}

	private static void deduplicate(ProductSet[] productSets, StaticProductSet[] resolvedSets) {
		Set<String> foundHeroProductIds = new HashSet<>();
		for (int i = 0; i < productSets.length; i++) {
			StaticProductSet resolvedSet = resolvedSets[i];
			if (i > 0) {
				List<String> deduplicatedIDs = new ArrayList<>(Math.min(productSets[i].getSize(), resolvedSet.ids.length));
				int k_offset = 0;
				for (int k = 0; k < productSets[i].getSize() && k + k_offset < resolvedSet.ids.length; k++) {
					while (foundHeroProductIds.add(resolvedSet.ids[k + k_offset]) == false && k + k_offset < resolvedSet.ids.length) {
						k_offset++;
					}
					deduplicatedIDs.add(resolvedSet.ids[k + k_offset]);
				}
				if (k_offset > 0 || deduplicatedIDs.size() != resolvedSet.ids.length) {
					resolvedSet.setIds(deduplicatedIDs.toArray(new String[deduplicatedIDs.size()]));
				}
			}
			else {
				for (String id : resolvedSet.ids) {
					foundHeroProductIds.add(id);
				}
			}
		}
	}

	/**
	 * Extend MasterVariantQuery to inject the hero products and boost them to
	 * the top.
	 * 
	 * @param searchQuery
	 * @param internalParams
	 */
	public static void extendQuery(MasterVariantQuery searchQuery, InternalSearchParams internalParams) {
		StaticProductSet[] productSets = internalParams.heroProductSets;
		if (productSets.length > 0) {
			BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
			float boost = 100f * (float) Math.pow(10, productSets.length);
			for (int i = 0; i < productSets.length; i++) {
				// since this is "just" another should clause, the product sets
				// are still influenced by the matches of the generic user
				// query.
				boolQuery.should(
						QueryBuilders.queryStringQuery(idsAsOrderedBoostQuery(productSets[i].ids))
								.boost(boost)
								.defaultField("_id")
								.defaultOperator(Operator.OR));
				boost /= 10;
			}
			searchQuery.setMasterLevelQuery(boolQuery.should(searchQuery.getMasterLevelQuery()));
		}
	}

	private static String idsAsOrderedBoostQuery(@NonNull String[] ids) {
		long boost = 10 * ids.length;
		// rough approx. of string-length
		StringBuilder idsOrderedBoostQuery = new StringBuilder(ids.length * (ids[0].length() + 2 + String.valueOf(boost).length()));
		for (String id : ids) {
			idsOrderedBoostQuery
					.append(id)
					.append('^')
					.append(String.valueOf(boost))
					.append(' ');
			boost -= 10;
		}
		return idsOrderedBoostQuery.toString();
	}

	/**
	 * <p>
	 * Get minimum hitCount to accept the natural search to have matched
	 * anything. This is necessary to follow the query-relaxation chain until we
	 * have the original result, as if there wouldn't be any hero products.
	 * </p>
	 * 
	 * @param internalParams
	 * @return the expected minimum hit count
	 */
	public static int getCorrectedMinHitCount(InternalSearchParams internalParams) {
		return internalParams.heroProductSets.length == 0 ? 0 : Arrays.stream(internalParams.heroProductSets).mapToInt(ProductSet::getSize).sum();
	}

	/**
	 * <p>
	 * Extract the hero products into separate ordered slices, that are attached
	 * to the searchResult.
	 * </p>
	 * <p>
	 * In case the result was sorted or filtered, this method most likely won't
	 * extract slices anymore, because it expects them in the defined order at
	 * the top of the result.
	 * </p>
	 * 
	 * @param searchResponse
	 * @param internalParams
	 * @param searchResult
	 * @return set of ids, that are already part of primary slices
	 */
	public static Set<String> extractSlices(SearchResponse searchResponse, InternalSearchParams internalParams, SearchResult searchResult) {
		if (internalParams.getSortings().size() == 0) {
			StaticProductSet[] productSets = internalParams.heroProductSets;
			Map<String, Integer> productSetAssignment = new HashMap<>();
			for (int i = 0; i < productSets.length; i++) {
				for (int k = 0; k < productSets[i].ids.length; k++) {
					productSetAssignment.putIfAbsent(productSets[i].ids[k], i);
				}

				if (productSets[i].ids.length > 0) {
					SearchResultSlice slice = new SearchResultSlice();
					slice.setLabel(productSets[i].getName());
					slice.setMatchCount(productSets[i].getSize());
					slice.setHits(new ArrayList<>());
					searchResult.slices.add(slice);
				}
				else {
					// add empty slice as placeholder so the slices-indexes are
					// equal to i
					searchResult.slices.add(new SearchResultSlice().setLabel("_empty"));
				}
			}

			SearchHit[] hits = searchResponse.getHits().getHits();
			for (int h = 0; h < hits.length; h++) {
				Integer sliceIndex = productSetAssignment.get(hits[h].getId());
				if (sliceIndex != null) {
					ResultHit resultHit = ResultMapper.mapSearchHit(hits[h], Collections.emptyMap());
					searchResult.slices.get(sliceIndex).getHits().add(resultHit);
				}
			}

			searchResult.slices.removeIf(s -> "_empty".equals(s.getLabel()));

			return productSetAssignment.keySet();
		}
		return Collections.emptySet();
	}

}
