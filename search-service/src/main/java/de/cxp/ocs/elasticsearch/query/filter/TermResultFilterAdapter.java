package de.cxp.ocs.elasticsearch.query.filter;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import lombok.Data;

@Data
public class TermResultFilterAdapter implements InternalResultFilterAdapter<TermResultFilter> {

	@Override
	public QueryBuilder getAsQuery(String fieldPrefix, TermResultFilter filter) {
		return filter.isNestedFilter() ? nestedFilterQuery(fieldPrefix, filter) : standardFilterQuery(fieldPrefix, filter);
	}

	private BoolQueryBuilder nestedFilterQuery(String fieldPrefix, TermResultFilter filter) {
		BoolQueryBuilder termFilterQuery = QueryBuilders.boolQuery()
				.must(QueryBuilders.termQuery(fieldPrefix + "name", filter.getField().getName()))
				.must(toTermsFilter(fieldPrefix + (filter.isFilterOnId() ? "id" : "value.normalized"), filter.getValues()));
		return termFilterQuery;
	}

	private QueryBuilder standardFilterQuery(String fieldPrefix, TermResultFilter filter) {
		String[] values = filter.getValues();
		String fullFieldName = fieldPrefix + filter.getField().getName();
		return toTermsFilter(fullFieldName, values);
	}

	private QueryBuilder toTermsFilter(String fullFieldName, String[] values) {
		if (values.length > 1) {
			return QueryBuilders.termsQuery(fullFieldName, values);
		}
		else {
			return QueryBuilders.termQuery(fullFieldName, values[0]);
		}
	}

}
