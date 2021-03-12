package de.cxp.ocs.spi.search;

import java.util.Map;

import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.elasticsearch.query.ESQueryBuilder;

public interface ESQueryBuilderProvider {

	/**
	 * Name of that strategy. Should be unique, otherwise the full class name is
	 * prefixed
	 */
	String getStrategyName();

	/**
	 * Initialize a ESQueryBuilder with the configured settings and
	 * field-weights. This only happens once during initialization, so the
	 * resulting ESQueryBuilder will be reused for every search, that's why it
	 * should be state-less or thread safe.
	 * 
	 * @param settings
	 * @param fieldWeights
	 * @param fieldConfig
	 * @return
	 */
	ESQueryBuilder initialize(Map<String, String> settings, Map<String, Float> fieldWeights, FieldConfigIndex fieldConfig);

}
