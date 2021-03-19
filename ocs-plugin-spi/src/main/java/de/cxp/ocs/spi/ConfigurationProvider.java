package de.cxp.ocs.spi;

import java.util.Map;
import java.util.Optional;

import de.cxp.ocs.config.FacetConfiguration;
import de.cxp.ocs.config.FieldConfiguration;
import de.cxp.ocs.config.QueryConfiguration;
import de.cxp.ocs.config.ScoringConfiguration;

public interface ConfigurationProvider {

	// used by indexer for new full indexation
	Optional<FieldConfiguration> getFieldConfiguration(String indexName);

	// used by searcher to assemble SearchConfiguration
	Optional<ScoringConfiguration> getScoringConfiguration(String tenant);

	Optional<FacetConfiguration> getFacetConfiguration(String tenant);

	Optional<Map<String, QueryConfiguration>> getQueryConfiguration(String tenant);

}
