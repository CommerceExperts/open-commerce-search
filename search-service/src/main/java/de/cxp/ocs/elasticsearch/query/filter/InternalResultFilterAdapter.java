package de.cxp.ocs.elasticsearch.query.filter;

import org.elasticsearch.index.query.QueryBuilder;

public interface InternalResultFilterAdapter<F extends InternalResultFilter> {

	QueryBuilder getAsQuery(String fieldPrefix, F filter);

}
