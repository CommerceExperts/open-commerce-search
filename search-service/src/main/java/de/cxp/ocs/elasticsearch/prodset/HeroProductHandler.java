package de.cxp.ocs.elasticsearch.prodset;

import java.util.*;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.elasticsearch.Searcher;
import de.cxp.ocs.elasticsearch.mapper.ResultMapper;
import de.cxp.ocs.elasticsearch.mapper.VariantPickingStrategy;
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
 * the single hits in the order they have been requested.</li>
 * <li></li>
 * </ul>
 * 
 * @author Rudolf Batt
 */
@Slf4j
public class HeroProductHandler {

	private static int MAX_IDS_ORDERED_BOOSTING = 1000;
	// used to identify products boosted by the hero-products query
	public static String QUERY_NAME_PREFIX = "hero-product-set-";

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
	 *        array of product sets to be resolved to static product sets
	 * @param searcher
	 *        matching Searcher instance for these products
	 * @param searchContext
	 *        context
	 * @return array of resolved product sets
	 */
	public static StaticProductSet[] resolve(ProductSet[] productSets, Searcher searcher, SearchContext searchContext) {
		StaticProductSet[] resolvedSets = new StaticProductSet[productSets.length];
		int nextPos = 0;
		Set<String> foundIds = new HashSet<String>(Arrays.stream(productSets).mapToInt(ProductSet::getSize).sum());
		for (ProductSet set : productSets) {
			int position = nextPos++;

			final ProductSetResolver resolver = resolvers.get(set.getType());

			if (resolver == null) {
				log.error("No resolver found for product set type '{}'", set.getType());
				resolvedSets[position] = new StaticProductSet().setIds(new String[0]).setName(set.getName());
			}
			// only run async, if there are more than 1 sets
			else if (resolver.runAsync() && productSets.length > 1) {
				resolvedSets[position] = resolver.resolve(set, foundIds, searcher, searchContext);
				foundIds.addAll(Arrays.asList(resolvedSets[position].getIds()));
			}
			else {
				resolvedSets[position] = resolver.resolve(set, foundIds, searcher, searchContext);
				foundIds.addAll(Arrays.asList(resolvedSets[position].getIds()));
			}
		}

		return resolvedSets;
	}

	public static Optional<QueryBuilder> getHeroQuery(InternalSearchParams internalParams) {
		StaticProductSet[] productSets = internalParams.heroProductSets;
		QueryBuilder heroQuery = null;
		if (productSets != null && productSets.length > 0) {
			// we will join several product sets with boolean should
			if (productSets.length > 1) {
				heroQuery = QueryBuilders.boolQuery();
			}
			/**
			 * assume 3 sets, each with 1000/max ids
			 * boost-ranges should be:
			 * A: 1000_000 * [1000 : 1] = 1000_000_000 : 1000_000
			 * B: 1000 * [1000 : 1] = 1000_000 : 1000
			 * C: 1 * [1000 : 1] = 1000 : 1
			 * 
			 * last product of set A has boost (1_000_000 * 1),
			 * which must be greater than
			 * the first product of set B that has boost (1000 * 1000)
			 * 
			 * Additional add factor 10 to ensure those IDs are returned prior to the "natural" result.
			 */
			float boost = 10f * (float) Math.pow(MAX_IDS_ORDERED_BOOSTING, productSets.length - 1);
			for (int i = 0; i < productSets.length; i++) {
				if (productSets[i].ids.length > 0) {
					// normaly we would generate a query-string query to
					// guarantee the order of the IDs, but that's limited to
					// 1024 clauses. So in case we have more IDs, we have to
					// switch to the normal ids query
					QueryBuilder productSetQuery;
					if (productSets[i].ids.length > MAX_IDS_ORDERED_BOOSTING) {
						log.warn("Cannot guarantee the order of the provided IDs for a product set with more than 1024 IDs (for request with user query = {})",
								internalParams.getUserQuery());
						productSetQuery = QueryBuilders.idsQuery()
								.addIds(productSets[i].ids)
								.boost(boost)
								.queryName(QUERY_NAME_PREFIX + i);
					}
					else {
						productSetQuery = QueryBuilders.queryStringQuery(idsAsOrderedBoostQuery(productSets[i].ids))
								.boost(boost)
								.defaultField("_id")
								.defaultOperator(Operator.OR)
								.queryName(QUERY_NAME_PREFIX + i);
					}
					heroQuery = heroQuery == null ? productSetQuery : ((BoolQueryBuilder) heroQuery).should(productSetQuery);
				}
				boost /= MAX_IDS_ORDERED_BOOSTING;
			}
		}
		return Optional.ofNullable(heroQuery);
	}

	private static String idsAsOrderedBoostQuery(@NonNull String[] ids) {
		long boost = MAX_IDS_ORDERED_BOOSTING;
		// rough approx. of string-length
		StringBuilder idsOrderedBoostQuery = new StringBuilder(ids.length * (ids[0].length() + 2 + String.valueOf(boost).length()));
		for (String id : ids) {
			idsOrderedBoostQuery
					.append(id)
					.append('^')
					.append(String.valueOf(boost))
					.append(' ');
			boost -= 1;
		}
		return idsOrderedBoostQuery.toString();
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
	 *        ES search result
	 * @param internalParams
	 *        with sortings and hero product-sets
	 * @param searchResult
	 *        where hero product slices should be added
	 * @param variantPickingStrategy
	 *        function to pick variant in case of inner variant hits
	 * @return set of ids, that are already part of primary slices
	 */
	public static Set<String> extractSlices(SearchResponse searchResponse, InternalSearchParams internalParams, SearchResult searchResult,
			VariantPickingStrategy variantPickingStrategy) {
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
					ResultHit resultHit = ResultMapper.mapSearchHit(hits[h], Collections.emptyMap(), variantPickingStrategy);
					searchResult.slices.get(sliceIndex).getHits().add(resultHit);
				}
			}

			searchResult.slices.removeIf(s -> "_empty".equals(s.getLabel()));

			return productSetAssignment.keySet();
		}
		return Collections.emptySet();
	}

}
