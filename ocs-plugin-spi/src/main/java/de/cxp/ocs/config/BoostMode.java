package de.cxp.ocs.config;

/**
 * The boost_mode specified, how the score is combined with the score of the
 * query.
 */
public enum BoostMode {
	MULTIPLY, REPLACE, SUM, AVG, MAX, MIN
}
