package de.cxp.ocs.config;

import java.util.*;

import org.springframework.boot.context.properties.NestedConfigurationProperty;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ApplicationSearchProperties {

	@NestedConfigurationProperty
	private String indexName;

	Locale locale = Locale.ROOT;

	/**
	 * can be used by the tenant specific configuration to fallback to default
	 * facet configuration.
	 */
	boolean useDefaultFacetConfig = false;

	/**
	 * can be used by the tenant specific configuration to fallback to the
	 * default scoring configuration.
	 */
	boolean useDefaultScoringConfig = false;

	/**
	 * can be used by the tenant specific configuration to fallback to the
	 * default query configuration.
	 */
	boolean useDefaultQueryConfig = false;
	/**
	 * can be used by the tenant specific configuration to fallback to the
	 * default sorting configuration.
	 */
	boolean useDefaultSortConfig = false;

	private String variantPickingStrategy = "pickIfBestScored";

	@NestedConfigurationProperty
	private QueryProcessingConfiguration queryProcessing = new QueryProcessingConfiguration();

	@NestedConfigurationProperty
	private FacetConfiguration facetConfiguration = new FacetConfiguration();

	@NestedConfigurationProperty
	private ScoringConfiguration scoringConfiguration = new ScoringConfiguration();

	private List<String> rescorers = new ArrayList<>();

	@NestedConfigurationProperty
	private final Map<String, QueryConfiguration> queryConfiguration = new LinkedHashMap<>();

	@NestedConfigurationProperty
	private final List<SortOptionConfiguration> sortConfiguration = new ArrayList<>();

	private final Map<String, Map<String, String>> pluginConfiguration = new LinkedHashMap<>();

}
