package de.cxp.ocs.smartsuggest.querysuggester;

public enum SuggesterEngine {

	/**
	 * Implementation that uses https://lucene.apache.org/
	 */
	LUCENE,

	/**
	 * Implementation that uses https://github.com/nikcomestotalk/autosuggest
	 */
	DHIMAN;

}
