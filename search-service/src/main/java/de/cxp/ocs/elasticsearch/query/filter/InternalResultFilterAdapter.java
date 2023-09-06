package de.cxp.ocs.elasticsearch.query.filter;

import org.elasticsearch.index.query.QueryBuilder;

import de.cxp.ocs.elasticsearch.model.filter.InternalResultFilter;

public interface InternalResultFilterAdapter<F extends InternalResultFilter> {

	/**
	 * Build query for given filter using the specified field-prefix.
	 * Negation of the filter MUST NOT be handled inside the adapter!
	 * 
	 * @param fieldPrefix
	 *        the prefix to the field name including the separator "."
	 * @param filter
	 *        the filter with name and values and all required information.
	 * @return
	 */
	QueryBuilder getAsQuery(String fieldPrefix, F filter);

}
