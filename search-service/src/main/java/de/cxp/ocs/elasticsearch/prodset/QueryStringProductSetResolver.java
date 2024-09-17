package de.cxp.ocs.elasticsearch.prodset;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.elasticsearch.Searcher;
import de.cxp.ocs.model.params.ProductSet;
import de.cxp.ocs.model.params.QueryStringProductSet;
import de.cxp.ocs.model.params.SearchQuery;
import de.cxp.ocs.model.params.StaticProductSet;
import de.cxp.ocs.model.result.ResultHit;
import de.cxp.ocs.model.result.SearchResult;
import de.cxp.ocs.util.InternalSearchParams;
import de.cxp.ocs.util.SearchParamsParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class QueryStringProductSetResolver implements ProductSetResolver {

    @Override
    public StaticProductSet resolve(ProductSet set, Set<String> excludedIds, Searcher searcher, SearchContext searchContext) {
        QueryStringProductSet queryStringProductSet = (QueryStringProductSet) set;

        SearchQuery searchQuery = new SearchQuery();
        searchQuery.q = queryStringProductSet.query;
        searchQuery.sort = queryStringProductSet.sort;
        searchQuery.limit = queryStringProductSet.limit;
        searchQuery.withFacets = false;

        InternalSearchParams parameters = SearchParamsParser.extractInternalParams(
                searchQuery,
                queryStringProductSet.filters == null ? Collections.emptyMap() : queryStringProductSet.filters,
                searchContext);
        parameters.excludedIds = excludedIds;
        parameters.setWithResultData(false);

        StaticProductSet resolvedSet = search(queryStringProductSet, searcher, parameters);
        resolvedSet.setAsSeparateSlice(queryStringProductSet.asSeparateSlice);
        resolvedSet.setVariantBoostTerms(Optional.ofNullable(queryStringProductSet.getVariantBoostTerms()).orElse(queryStringProductSet.query));

        return resolvedSet;
    }

    private StaticProductSet search(QueryStringProductSet set, Searcher searcher, InternalSearchParams productSetParams) {
        try {
            SearchResult searchResult = searcher.queryStringFind(productSetParams, set.getFieldWeights());
            List<ResultHit> hits = searchResult.getSlices().get(0).hits;

            String[] ids = hits.stream()
                    .map(hit -> hit.getDocument().id)
                    .toArray(String[]::new);

            return new StaticProductSet(ids, set.getName());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
