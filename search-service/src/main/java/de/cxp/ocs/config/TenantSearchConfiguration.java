package de.cxp.ocs.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.NestedConfigurationProperty;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TenantSearchConfiguration {

	@NestedConfigurationProperty
	private String indexName;

	@NestedConfigurationProperty
	private FacetConfiguration facetConfiguration = new FacetConfiguration();

	@NestedConfigurationProperty
	private ScoringConfiguration scoringConfiguration = new ScoringConfiguration();

	@NestedConfigurationProperty
	private final Map<String, QueryConfiguration> queryConfiguration = new LinkedHashMap<>();

}
