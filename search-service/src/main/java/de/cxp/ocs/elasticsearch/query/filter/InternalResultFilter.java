package de.cxp.ocs.elasticsearch.query.filter;

public interface InternalResultFilter {

	String getField();

	String getFieldPrefix();

	boolean isNestedFilter();

	String[] getValues();

}