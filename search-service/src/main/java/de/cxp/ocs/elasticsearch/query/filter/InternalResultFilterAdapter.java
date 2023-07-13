package de.cxp.ocs.elasticsearch.query.filter;

import org.elasticsearch.index.query.QueryBuilder;

import de.cxp.ocs.elasticsearch.model.filter.InternalResultFilter;

public interface InternalResultFilterAdapter<F extends InternalResultFilter> {

	QueryBuilder getAsQuery(String fieldPrefix, F filter);

}
