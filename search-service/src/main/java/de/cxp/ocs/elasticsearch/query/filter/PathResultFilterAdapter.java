package de.cxp.ocs.elasticsearch.query.filter;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import lombok.Data;

@Data
public class PathResultFilterAdapter implements InternalResultFilterAdapter<PathResultFilter> {

	@Override
	public QueryBuilder getAsQuery(String fieldPrefix, PathResultFilter filter) {
		String filterValueField = fieldPrefix + (filter.isFilterOnId() ? "id" : "value");
		String[] filterValues = filter.isFilterOnId() ? filter.getLeastPathValues() : filter.getValues();

		return QueryBuilders.boolQuery()
				.must(QueryBuilders.termQuery(fieldPrefix + "name", filter.getField().getName()))
				.must(QueryBuilders.termsQuery(filterValueField, filterValues));
	}


}
