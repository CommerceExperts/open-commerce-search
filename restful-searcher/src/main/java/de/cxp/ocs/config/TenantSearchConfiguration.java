package de.cxp.ocs.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.NestedConfigurationProperty;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Data
@NoArgsConstructor
public class TenantSearchConfiguration {

	@NestedConfigurationProperty
	@NonNull
	private String indexName;

	@NestedConfigurationProperty
	private FacetConfiguration facetConfiguration = new FacetConfiguration();

	@NestedConfigurationProperty
	private ScoringConfiguration scoringConfiguration = new ScoringConfiguration();

	@NestedConfigurationProperty
	private final Map<String, QueryConfiguration> queryConfiguration = new LinkedHashMap<>();

}
