package de.cxp.ocs.elasticsearch.facets;

import java.util.Map;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;

public interface FacetCreatorFactory {

	boolean supportsType(String facetType);

	FacetCreator create(Map<String, FacetConfig> facetConfigs);

}
