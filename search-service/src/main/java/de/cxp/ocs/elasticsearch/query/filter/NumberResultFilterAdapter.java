package de.cxp.ocs.elasticsearch.query.filter;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;

import lombok.Data;

@Data
public class NumberResultFilterAdapter implements InternalResultFilterAdapter<NumberResultFilter> {

	@Override
	public QueryBuilder getAsQuery(String fieldPrefix, NumberResultFilter filter) {
		RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery(fieldPrefix + "value");
		if (filter.getLowerBound() != null) rangeQuery.from(filter.getLowerBound(), true);
		if (filter.getUpperBound() != null) rangeQuery.to(filter.getUpperBound(), true);

		return QueryBuilders.boolQuery()
				.must(QueryBuilders.termQuery(fieldPrefix + "name", filter.getField()))
				.must(rangeQuery);
	}


}
