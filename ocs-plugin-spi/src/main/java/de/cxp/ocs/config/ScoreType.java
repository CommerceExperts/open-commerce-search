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
	 * <p>
	 * Similar to a field-value-factor but in the context of Elasticsearch only usable
	 * for rank_feature/rank_features fields.
	 * </p>
	 * It supports the following options:
	 * <ul>
	 * <li>DYNAMIC_PARAM: name of the parameter that should be used to add a field suffix. Usable only for rank_features
	 * field that contains dynamic features.</li>
	 * <li>MISSING: if given, it is used to invert the rank-feature-query to provide a default score to all other
	 * products in the result set</li>
	 * <li>MODIFIER with the values 'linear', 'log', 'saturation' and 'sigmoid'.</li>
	 * <li>FACTOR: used for 'log' modifier only to add to value 'log(scaling_factor + value)'</li>
	 * <li>BOOST: value multiplied with the modified score</li>
	 * <li>PIVOT: used for 'saturation' or 'sigmoid' function</li>
	 * <li>EXPONENT: used for 'sigmoid' function</li>
	 * </ul>
	 * 
	 * @see https://www.elastic.co/guide/en/elasticsearch/reference/7.17/query-dsl-rank-feature-query.html
	 */
	RANK_FEATURE,

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
