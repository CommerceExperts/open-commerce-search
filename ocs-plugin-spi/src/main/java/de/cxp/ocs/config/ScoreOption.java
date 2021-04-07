package de.cxp.ocs.config;

public enum ScoreOption {

	/**
	 * if not set, the random function won't be deterministic and change
	 * for each request
	 */
	random_seed,

	// field_value_factor options:
	missing, modifier,

	/**
	 * required option for script_score
	 */
	script_code,

	/**
	 * required option for the decay_* score types.
	 * 
	 * The point of origin used for calculating distance. Must be given as a
	 * number for numeric field, date for date fields and geo point for geo
	 * fields. Required for geo and numeric field. For date fields the
	 * default is now. Date math (for example now-1h) is supported for
	 * origin.
	 * 
	 * see
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html#function-decay
	 */
	origin,

	/**
	 * required option for the decay_* score types.
	 * 
	 * Required for all types. Defines the distance from origin + offset at
	 * which the computed score will equal decay parameter. For geo fields:
	 * Can be defined as number+unit (1km, 12m,…). Default unit is meters.
	 * For date fields: Can to be defined as a number+unit ("1h", "10d",…).
	 * Default unit is milliseconds. For numeric field: Any number.
	 * 
	 * see
	 * https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html#function-decay
	 */
	scale,

	/**
	 * The decay parameter defines how documents are scored at the distance
	 * given at scale. If no decay is defined, documents at the distance
	 * scale will be scored 0.5.
	 * 
	 * Only used for decay_* score types
	 */
	decay,

	/**
	 * If an offset is defined, the decay function will only compute the
	 * decay function for documents with a distance greater that the defined
	 * offset. The default is 0.
	 * 
	 * Only used for decay_* score types.
	 */
	offset
}
