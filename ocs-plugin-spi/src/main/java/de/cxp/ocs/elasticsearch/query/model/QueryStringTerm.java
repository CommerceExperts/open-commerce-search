package de.cxp.ocs.elasticsearch.query.model;

/**
 * A single term for a query-string-query
 */
public interface QueryStringTerm {

	/**
	 * Prepare the term for a query-string-query.
	 * 
	 * @return term in query-string-query format.
	 */
	String toQueryString();

	/**
	 * @return the single original word without the additional noise.
	 */
	String getWord();

}
