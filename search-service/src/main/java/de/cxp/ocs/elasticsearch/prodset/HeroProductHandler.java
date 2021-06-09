package de.cxp.ocs.elasticsearch.prodset;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;

import de.cxp.ocs.elasticsearch.ResultMapper;
import de.cxp.ocs.elasticsearch.query.MasterVariantQuery;
import de.cxp.ocs.model.params.ProductSet;
import de.cxp.ocs.model.params.StaticProductSet;
import de.cxp.ocs.model.result.ResultHit;
import de.cxp.ocs.model.result.SearchResult;
import de.cxp.ocs.model.result.SearchResultSlice;
import de.cxp.ocs.util.InternalSearchParams;

public class HeroProductHandler {

	// TODO: if class has no state, change all methods to static functions

	/**
	 * Extend MasterVariantQuery to inject the hero products and boost them to
	 * the top.
	 * 
	 * @param searchQuery
	 * @param heroProductSets
	 */
	public void extendQuery(MasterVariantQuery searchQuery, InternalSearchParams internalParams) {
		StaticProductSet[] productSets = internalParams.heroProductSets;
		if (productSets.length > 0) {
			BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
			float boost = 10f * (float) Math.pow(10, productSets.length);
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
	 * @param searchResponse
	 * @param heroProductSets
	 * @return
	 */
	public int getCorrectedMinHitCount(SearchResponse searchResponse, InternalSearchParams internalParams) {
		// FIXME: if the result was sorted or filtered, we can't tell for sure,
		// how many of the hero products actually matched. Imagine a static IDs
		// list passed trough the API, but only some of these IDs actually
		// exist. If we now expect a higher hitcount, the query-relaxation loop
		// could go to the next - undesired - stage
		// possible fix: verify IDs list prior searching for it (=> worse
		// performance)
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
	 * @param heroProductSets
	 * @param searchResult
	 * @return fetchOffset: the index of the first hit, that is not a hero
	 *         product
	 */
	public int extractSlices(SearchResponse searchResponse, InternalSearchParams internalParams, SearchResult searchResult) {
		if (internalParams.getSortings().size() == 0) {
			StaticProductSet[] productSets = internalParams.heroProductSets;
			int hitIndex = 0;
			SearchHit[] hits = searchResponse.getHits().getHits();
			for (StaticProductSet productSet : productSets) {
				List<ResultHit> resultHits = new ArrayList<>();
				for (int i = 0; i < productSet.ids.length && hitIndex < hits.length; i++) {
					String expectedId = productSet.ids[i];
					if (expectedId.equals(hits[hitIndex].getId())) {
						ResultHit resultHit = ResultMapper.mapSearchHit(hits[hitIndex], Collections.emptyMap());
						resultHits.add(resultHit);
						hitIndex++;
					}
				}

				SearchResultSlice slice = new SearchResultSlice();
				slice.setLabel(productSet.getName());
				slice.setHits(resultHits);
				slice.setMatchCount(resultHits.size());
				searchResult.slices.add(slice);
			}
			return hitIndex;
		}
		return 0;
	}

}
