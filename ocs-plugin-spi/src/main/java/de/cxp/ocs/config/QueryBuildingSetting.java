package de.cxp.ocs.config;

public enum QueryBuildingSetting {

	/**
	 * Analyzer to be used to analyze the input query.
	 */
	analyzer,

	/**
	 * Analyzer to be used to analyze the input query that is put into quotes as alternative match.
	 */
	quoteAnalyzer,

	/**
	 * Slop value that should be used for the quoted part of the query.
	 * see https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-match-query-phrase.html
	 */
	phraseSlop,

	/**
	 * see
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-minimum-should-match.html
	 */
	minShouldMatch,

	/**
	 * one of OR, AND
	 */
	operator,

	/**
	 * float value between 0 (inclusive) and 1 (inclusive)
	 */
	tieBreaker,

	/**
	 * one of CROSS_FIELDS (default), BEST_FIELDS, MOST_FIELDS, PHRASE,
	 * PHRASE_PREFIX
	 * 
	 * see
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-multi-match-query.html#multi-match-types
	 */
	multimatch_type,

	/**
	 * see
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/common-options.html#fuzziness
	 */
	fuzziness,

	/**
	 * Used for preFetchQuery to specify, which es-query-builder should be
	 * used to search for "unknown" terms. Set it to an existing name of
	 * another query-builder.
	 * Leave it undefined to ignore such terms (= 0-matches in case all
	 * queries
	 * are unknown).
	 */
	fallbackQuery,

	/**
	 * Setting that can be set to "true" or "false". If true, a spellcheck can
	 * be executed with the built query.
	 * If spell corrections could be fetched for the given user query and no
	 * results where found by the query itself, the query will be executed again
	 * with the spell corrections.
	 */
	allowParallelSpellcheck,

	/**
	 * boolean setting to enable the creation and searching for combined terms
	 * (shingles)
	 */
	isQueryWithShingles,

	/**
	 * If set to true, no other query will be used after this one, even if it
	 * returned 0 results.
	 */
	acceptNoResult
}
