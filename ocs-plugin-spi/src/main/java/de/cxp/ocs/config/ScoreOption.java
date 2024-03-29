package de.cxp.ocs.config;

public enum ScoreOption {

	/**
	 * <p>
	 * Option that should be set with a boolean value (true|false).
	 * </p>
	 * <p>
	 * If set to "true", that scoring option will be used to score the
	 * variant records of a master among each other.
	 * </p>
	 * <p>
	 * The default behavior depends on the scoring function:
	 * Scoring function that are based on a variant field, then the default is "true".
	 * All other scoring functions are not used for variant scoring unless defined explicitly.
	 * </p>
	 */
	USE_FOR_VARIANTS,

	/**
	 * if not set, the random function won't be deterministic and change
	 * for each request
	 */
	RANDOM_SEED,

	/**
	 * Specifies the value for a document that misses the value for the
	 * according scoring field.
	 */
	MISSING,

	/**
	 * <p>
	 * Mathematical modifier for the data values.
	 * With Elasticsearch 7 this is one of the strings
	 * "none", "log", "log1p", "log2p", "ln", "ln1p", "ln2p", "square", "sqrt",
	 * or "reciprocal".
	 * </p>
	 * <p>
	 * For score-type RANK_FEATURE the values 'linear', 'log', 'saturation' and 'sigmoid' are supported.
	 * </p>
	 * <p>
	 * see
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html#function-field-value-factor
	 * </p>
	 */
	MODIFIER,

	/**
	 * Factor (double value) that is multiplied to each field value, before the
	 * modifier is applied to it.
	 */
	FACTOR,

	/**
	 * Only usable for ScoreType.RANK_FEATURE to boost an according value.
	 */
	BOOST,

	/**
	 * Only usable for ScoreType.RANK_FEATURE to add a field-name suffix.
	 */
	DYNAMIC_PARAM,

	/**
	 * Only usable for ScoreType.RANK_FEATURE and MODIFIER set to 'sigmoid' or 'saturation'.
	 * See ScoreType.RANK_FEATURE.
	 */
	PIVOT,

	/**
	 * Only usable for ScoreType.RANK_FEATURE and MODIFIER set to 'sigmoid'.
	 * See ScoreType.RANK_FEATURE.
	 */
	EXPONENT,

	/**
	 * required option for script_score
	 */
	SCRIPT_CODE,

	/**
	 * required option for the decay_* score types.
	 * <p>
	 * The point of origin used for calculating distance. Must be given as a
	 * number for numeric field, date for date fields and geo point for geo
	 * fields. Required for geo and numeric field. For date fields the
	 * default is now. Date math (for example now-1h) is supported for
	 * origin.
	 * </p>
	 * see <a href=
	 * "https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html#function-decay">ES
	 * Function Decay documentation</a>
	 */
	ORIGIN,

	/**
	 * required option for the decay_* score types.
	 * <p>
	 * Required for all types. Defines the distance from origin + offset at
	 * which the computed score will equal decay parameter. For geo fields:
	 * Can be defined as number+unit (1km, 12m,…). Default unit is meters.
	 * For date fields: Can to be defined as a number+unit ("1h", "10d",…).
	 * Default unit is milliseconds. For numeric field: Any number.
	 * </p>
	 * see <a href=
	 * "https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html#function-decay">ES
	 * Function Decay documentation</a>
	 */
	SCALE,

	/**
	 * The decay parameter defines how documents are scored at the distance
	 * given at scale.
	 * <p>
	 * If no decay is defined, documents at the distance
	 * scale will be scored 0.5.
	 * </p>
	 * Only used for decay_* score types
	 */
	DECAY,

	/**
	 * If an offset is defined, the decay function will only compute the
	 * decay function for documents with a distance greater that the defined
	 * offset. The default is 0.
	 * 
	 * Only used for decay_* score types.
	 */
	OFFSET,

}
