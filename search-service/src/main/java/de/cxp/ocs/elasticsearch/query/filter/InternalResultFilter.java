package de.cxp.ocs.elasticsearch.query.filter;

import de.cxp.ocs.config.Field;

public interface InternalResultFilter {

	Field getField();

	boolean isFilterOnId();

	String getFieldPrefix();

	boolean isNestedFilter();

	String[] getValues();

}