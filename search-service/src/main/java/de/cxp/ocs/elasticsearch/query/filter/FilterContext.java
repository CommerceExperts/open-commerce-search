package de.cxp.ocs.elasticsearch.query.filter;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import de.cxp.ocs.elasticsearch.query.MasterVariantQuery;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class FilterContext {

	private final static Map<String, QueryBuilder> NO_FILTER = Collections.emptyMap();

	private final static MasterVariantQuery NO_QUERY = new MasterVariantQuery(null, null, false, true);

	@Getter
	private final Map<String, InternalResultFilter> internalFilters;

	@Getter
	private final Map<String, QueryBuilder> basicFilterQueries;

	@Getter
	private final Map<String, QueryBuilder> postFilterQueries;

	@Getter
	private final MasterVariantQuery joinedBasicFilters;

	@Getter
	private final QueryBuilder joinedPostFilters;

	public FilterContext(Map<String, InternalResultFilter> internalFilters) {
		this(internalFilters, NO_FILTER, NO_FILTER, NO_QUERY, null);
	}

	public QueryBuilder allWithPostFilterNamesExcluded(String filterNamePath) {
		QueryBuilder allFilter;
		if (postFilterQueries.isEmpty()) {
			allFilter = QueryBuilders.matchAllQuery();
		}
		else {
			allFilter = getJoinedPostFilters();
			if (!(allFilter instanceof BoolQueryBuilder)) {
				allFilter = QueryBuilders.boolQuery().must(allFilter);
			}
			((BoolQueryBuilder) allFilter).mustNot(QueryBuilders.termsQuery(filterNamePath, postFilterQueries.keySet()));
		}
		return allFilter;
	}

	public static QueryBuilder allButOne(String exclude, Map<String, QueryBuilder> filterQueries) {
		// don't use "remove" or similar on filterQueries,
		// because filterQueries is an UnmodifiableMap
		if (filterQueries.size() == 1 && filterQueries.containsKey(exclude)) {
			return QueryBuilders.matchAllQuery();
		}
		// if there are exactly 2 entries and one is the excluded, set
		// finalQuery to the remaining query later. Setting it to "null" here is
		// the "marker" for this behavior
		QueryBuilder finalQuery;
		if (filterQueries.size() == 2 && filterQueries.containsKey(exclude)) {
			finalQuery = null;
		}
		else {
			finalQuery = QueryBuilders.boolQuery();
		}

		for (Entry<String, QueryBuilder> fq : filterQueries.entrySet()) {
			if (fq.getKey().equals(exclude)) continue;
			// only null if there is only one matching QueryBuilder
			if (finalQuery == null) {
				finalQuery = fq.getValue();
				break;
			}
			else {
				((BoolQueryBuilder) finalQuery).must(fq.getValue());
			}
		}
		return finalQuery;
	}
}
