package de.cxp.ocs.config;

public enum ScoreType {

	/**
	 * Simple static weight score that is added to all documents. Useful to
	 * achieve a basic score for the other functions.
	 */
	WEIGHT,

	/**
	 * Random score for each document.
	 * The document id is used as a random see, so the same document gets the
	 * same reproducible score.
	 */
	RANDOM_SCORE,

	/**
	 * field value factor scoring can only be applied on numeric score data.
	 */
	FIELD_VALUE_FACTOR,

	/**
	 * score using custom script. Make sure to provide the required option
	 * 'SCRIPT_CODE'.
	 */
	SCRIPT_SCORE,

	/**
	 * <strong>Gaussian</strong> decay that is calculated on numeric, date or
	 * geo-point data values.
	 * Required parameters are 'ORIGIN', 'SCALE', 'OFFSET', and 'DECAY'.
	 * 
	 * see <a href=
	 * "https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html#function-decay">ES
	 * Function Decay documentation</a>
	 */
	DECAY_GAUSS,

	/**
	 * <strong>Linear</strong> decay that is calculated on numeric, date or
	 * geo-point data values.
	 * Required parameters are 'ORIGIN', 'SCALE', 'OFFSET', and 'DECAY'.
	 * 
	 * see <a href=
	 * "https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html#function-decay">ES
	 * Function Decay documentation</a>
	 */
	DECAY_LINEAR,

	/**
	 * <strong>Exponential</strong> decay that is calculated on numeric, date or
	 * geo-point data values.
	 * Required parameters are 'ORIGIN', 'SCALE', 'OFFSET', and 'DECAY'.
	 * 
	 * see <a href=
	 * "https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html#function-decay">ES
	 * Function Decay documentation</a>
	 */
	DECAY_EXP

}
