package de.cxp.ocs.elasticsearch.prodset;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;

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
	public StaticProductSet resolve(ProductSet dynamicProductSet, int extraBuffer, Searcher searcher, SearchContext searchContext) {
		DynamicProductSet productSet = (DynamicProductSet) dynamicProductSet;

		SearchQuery searchQuery = new SearchQuery();
		searchQuery.q = productSet.query;
		searchQuery.sort = productSet.sort;
		searchQuery.limit = productSet.limit + extraBuffer;
		searchQuery.withFacets = false;

		// TODO: add caching
		StaticProductSet resolved = search(dynamicProductSet, searcher, searchContext, productSet, searchQuery);
		return resolved;
	}

	private StaticProductSet search(ProductSet dynamicProductSet, Searcher searcher, SearchContext searchContext, DynamicProductSet productSet, SearchQuery searchQuery) {
		InternalSearchParams productSetParams = SearchParamsParser.extractInternalParams(
				searchQuery,
				productSet.filters == null ? Collections.emptyMap() : productSet.filters,
				searchContext);

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
