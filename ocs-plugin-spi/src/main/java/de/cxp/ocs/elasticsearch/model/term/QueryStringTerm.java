package de.cxp.ocs.elasticsearch.model.term;

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
	 * @return the single original unescaped term.
	 */
	String getRawTerm();

	Occur getOccur();
	
	/**
	 * @return if that query is already enclosed in quotes or brackets.
	 */
	boolean isEnclosed();

}
