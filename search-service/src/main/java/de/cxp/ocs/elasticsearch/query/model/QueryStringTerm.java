package de.cxp.ocs.elasticsearch.query.model;

import org.apache.commons.lang3.StringUtils;

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

	/**
	 * see
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-query-string-query.html#_reserved_characters
	 * 
	 * @param text
	 * @return
	 */
	public static String escape(String text) {
		return StringUtils.replaceEach(text,
				new String[] { " +", " -", "=", "&&", "||", "!", "(", ")", "{", "}", "[", "]", "^", "\"", "~", "*", "?",
						":", "\\", "/", "<", ">" },
				new String[] { " \\+", " \\-", "\\=", "\\&&", "\\||", "\\!", "\\(", "\\)", "\\{", "\\}", "\\[", "\\]",
						"\\^", "\\\"", "\\~", "\\*", "\\?", "\\:", "\\\\", "\\/", "", "" });
		// the chars < and > are removed entirely
	}
}
