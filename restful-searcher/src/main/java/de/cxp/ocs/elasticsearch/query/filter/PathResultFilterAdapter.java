package de.cxp.ocs.elasticsearch.query.filter;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

public class PathResultFilterAdapter implements InternalResultFilterAdapter<PathResultFilter> {

	@Override
	public QueryBuilder getAsQuery(String fieldPrefix, PathResultFilter filter) {
		return QueryBuilders.prefixQuery(fieldPrefix + filter.getField(), StringUtils.join(filter.getPath(), '/'));
	}

}
