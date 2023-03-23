package de.cxp.ocs.elasticsearch.prodset;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Set;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.elasticsearch.Searcher;
import de.cxp.ocs.model.params.DynamicProductSet;
import de.cxp.ocs.model.params.ProductSet;
import de.cxp.ocs.model.params.SearchQuery;
import de.cxp.ocs.model.params.StaticProductSet;
import de.cxp.ocs.model.result.ResultHit;
import de.cxp.ocs.model.result.SearchResult;
import de.cxp.ocs.util.InternalSearchParams;
import de.cxp.ocs.util.SearchParamsParser;

public class DynamicProductSetResolver implements ProductSetResolver {

	@Override
	public StaticProductSet resolve(ProductSet dynamicProductSet, Set<String> excludedIds, Searcher searcher, SearchContext searchContext) {
		DynamicProductSet dynamicSet = (DynamicProductSet) dynamicProductSet;

		SearchQuery searchQuery = new SearchQuery();
		searchQuery.q = dynamicSet.query;
		searchQuery.sort = dynamicSet.sort;
		searchQuery.limit = dynamicSet.limit;
		searchQuery.withFacets = false;

		InternalSearchParams productSetParams = SearchParamsParser.extractInternalParams(
				searchQuery,
				dynamicSet.filters == null ? Collections.emptyMap() : dynamicSet.filters,
				searchContext);
		productSetParams.excludedIds = excludedIds;
		productSetParams.setWithResultData(false);

		// TODO: add caching
		StaticProductSet resolvedSet = search(dynamicProductSet, searcher, productSetParams);
		resolvedSet.setAsSeparateSlice(dynamicSet.asSeparateSlice);
		resolvedSet.setVariantBoostTerms(dynamicSet.getVariantBoostTerms());
		return resolvedSet;
	}

	private StaticProductSet search(ProductSet dynamicProductSet, Searcher searcher, InternalSearchParams productSetParams) {
		try {
			SearchResult prodSetResult = searcher.find(productSetParams);
			if (prodSetResult.getSlices().size() > 0) {
				String[] ids = new String[prodSetResult.getSlices().get(0).hits.size()];
				int i = 0;
				for (ResultHit hit : prodSetResult.getSlices().get(0).hits) {
					ids[i++] = hit.getDocument().id;
				}
				return new StaticProductSet(ids, dynamicProductSet.getName());
			}
			else {
				return new StaticProductSet(new String[0], dynamicProductSet.getName());
			}
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public boolean runAsync() {
		return true;
	}

}
