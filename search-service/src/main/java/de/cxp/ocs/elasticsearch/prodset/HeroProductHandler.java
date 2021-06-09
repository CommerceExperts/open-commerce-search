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

@Slf4j
public class HeroProductHandler {

	@NonNull
	private final static Map<String, ProductSetResolver> resolvers = new HashMap<>(2);
	static {
		resolvers.put(new DynamicProductSet().type, new DynamicProductSetResolver());
		resolvers.put(new StaticProductSet().type, new StaticProductSetResolver());
	}

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
				boolQuery.should(
						QueryBuilders.idsQuery().addIds(productSets[i].ids).boost(boost));
				boost /= 10;
			}
			searchQuery.setMasterLevelQuery(boolQuery.should(searchQuery.getMasterLevelQuery()));
		}
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
	 * @return fetchOffset: the index of the first hit, that is not a hero
	 *         product
	 */
	public static int extractSlices(SearchResponse searchResponse, InternalSearchParams internalParams, SearchResult searchResult) {
		if (internalParams.getSortings().size() == 0) {
			StaticProductSet[] productSets = internalParams.heroProductSets;
			int hitIndex = 0;
			SearchHit[] hits = searchResponse.getHits().getHits();
			// expected min boost is factor 10 smaller, because the scoring is
			// also multiplied by values below 1
			float expectedMinBoost = 10f * (float) Math.pow(10, productSets.length);
			for (StaticProductSet productSet : productSets) {
				List<ResultHit> resultHits = new ArrayList<>();
				for (int i = 0; i < productSet.ids.length && hitIndex < hits.length && expectedMinBoost < hits[hitIndex].getScore(); i++) {
					ResultHit resultHit = ResultMapper.mapSearchHit(hits[hitIndex], Collections.emptyMap());
					resultHits.add(resultHit);
					hitIndex++;
				}
				expectedMinBoost /= 10;

				if (resultHits.size() > 0) {
					SearchResultSlice slice = new SearchResultSlice();
					slice.setLabel(productSet.getName());
					slice.setHits(resultHits);
					slice.setMatchCount(resultHits.size());
					searchResult.slices.add(slice);
				}
			}
			return hitIndex;
		}
		return 0;
	}

}
