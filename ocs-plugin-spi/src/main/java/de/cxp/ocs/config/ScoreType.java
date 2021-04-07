package de.cxp.ocs.config;

public enum ScoreType {
	WEIGHT, RANDOM_SCORE,

	/**
	 * field value factor scoring can only be applied on numeric score data.
	 */
	FIELD_VALUE_FACTOR,

	/**
	 * score using custom script. Make sure to provide the required option
	 * 'script_id_or_code'.
	 */
	SCRIPT_SCORE,

	/**
	 * decay methods can be applied on numeric, date or geo-point fields
	 */
	DECAY_GAUSS, DECAY_LINEAR, DECAY_EXP

}
