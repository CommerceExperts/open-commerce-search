package de.cxp.ocs.elasticsearch.query.filter;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;

import lombok.Data;

@Data
public class NumberResultFilterAdapter implements InternalResultFilterAdapter<NumberResultFilter> {

	@Override
	public QueryBuilder getAsQuery(String fieldPrefix, NumberResultFilter filter) {
		return filter.isNestedFilter() ? getNestedFilter(fieldPrefix, filter) : createRangeQuery(fieldPrefix + filter.getField().getName(), filter);
	}

	private QueryBuilder getNestedFilter(String fieldPrefix, NumberResultFilter filter) {
		RangeQueryBuilder rangeQuery = createRangeQuery(fieldPrefix + "value", filter);
		return QueryBuilders.boolQuery()
				.must(QueryBuilders.termQuery(fieldPrefix + "name", filter.getField().getName()))
				.must(rangeQuery);
	}

	private RangeQueryBuilder createRangeQuery(String filterFieldName, NumberResultFilter filter) {
		RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery(filterFieldName);
		// lower and upper bound must be inclusive, since we support numeric filters like `sale=1` that
		// are converted into the internal filter equivalent to `sale=1-1`
		if (filter.getLowerBound() != null) rangeQuery.from(filter.getLowerBound(), true);
		if (filter.getUpperBound() != null) rangeQuery.to(filter.getUpperBound(), true);
		return rangeQuery;
	}

}
