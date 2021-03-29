package de.cxp.ocs.config;

public enum ScoreType {
	weight, random_score,

	/**
	 * field value factor scoring can only be applied on numeric score data.
	 */
	field_value_factor,

	/**
	 * decay methods can be applied on numeric, date or geo-point fields
	 */
	decay_gauss, decay_linear, decay_exp

}
