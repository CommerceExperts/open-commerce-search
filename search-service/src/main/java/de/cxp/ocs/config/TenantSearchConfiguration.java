package de.cxp.ocs.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.NestedConfigurationProperty;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TenantSearchConfiguration {

	@NestedConfigurationProperty
	private String indexName;

	/**
	 * can be used by the tenant specific configuration to disable facet
	 * creation.
	 */
	boolean disableFacets = false;

	/**
	 * can be used by the tenant specific configuration to disable scoring.
	 */
	boolean disableScorings = false;

	/**
	 * can be used by the tenant specific configuration to disable conditional
	 * queries and only use the default query.
	 */
	boolean disableQueryConfig = false;

	@NestedConfigurationProperty
	private FacetConfiguration facetConfiguration = new FacetConfiguration();

	@NestedConfigurationProperty
	private ScoringConfiguration scoringConfiguration = new ScoringConfiguration();

	@NestedConfigurationProperty
	private final Map<String, QueryConfiguration> queryConfiguration = new LinkedHashMap<>();

	@NestedConfigurationProperty
	private final List<SortOptionConfiguration> sortConfigs = new ArrayList<>();

}
