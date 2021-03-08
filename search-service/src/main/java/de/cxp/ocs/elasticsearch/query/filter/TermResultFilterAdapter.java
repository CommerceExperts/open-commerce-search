package de.cxp.ocs.elasticsearch.query.filter;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import lombok.Data;

@Data
public class TermResultFilterAdapter implements InternalResultFilterAdapter<TermResultFilter> {

	@Override
	public QueryBuilder getAsQuery(String fieldPrefix, TermResultFilter filter) {
		return QueryBuilders.boolQuery()
				.must(QueryBuilders.termQuery(fieldPrefix + "name", filter.getField().getName()))
				.must(QueryBuilders.termsQuery(fieldPrefix + (filter.isFilterOnId() ? "code" : "value"), filter.getValues()));
	}


}
