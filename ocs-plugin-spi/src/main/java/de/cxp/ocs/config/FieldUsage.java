package de.cxp.ocs.config;

/**
 * Enum describing the usage of an field that will be indexed.
 */
public enum FieldUsage {

	/**
	 * Fields with this usage are analyzed in all set up ways made ready for full-text search.
	 */
	SEARCH,

	/**
	 * Fields with this usage are made ready to returned in the response at the matched hits.
	 */
	RESULT,

	/**
	 * Fields with this usage are prepared for sorting.
	 */
	SORT,

	/**
	 * Fields with this usage are prepared for automatic facet creation and filtering.
	 */
	FACET,

	/**
	 * Fields with this usage are prepared to be used for filtering without automatic facet generation.
	 */
	FILTER,

	/**
	 * Fields with this usage are prepared to be used for scoring
	 */
	SCORE

}
