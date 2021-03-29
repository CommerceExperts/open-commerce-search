package de.cxp.ocs.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Final search-configuration that contains all the fetched configuration
 * objects.
 */
@Data
@NoArgsConstructor
public class SearchConfiguration {

	/**
	 * Optional index-name that should be addressed by the tenant. If null, the
	 * index name will be set to the tenant name.
	 * 
	 * @param tenant
	 * @return
	 */
	private String indexName;

	/**
	 * Optional query processing configuration. If nothing special is defined,
	 * the standard behavior will be used.
	 */
	@NonNull
	private QueryProcessingConfiguration queryProcessing = new QueryProcessingConfiguration();

	/**
	 * <p>
	 * Optional facet configuration to customize the way the facets should be
	 * displayed.
	 * </p>
	 * <p>
	 * If default/empty, facets will be generated according to default settings.
	 * </p>
	 * 
	 * @param tenant
	 * @return
	 */
	@NonNull
	private FacetConfiguration facetConfiguration = new FacetConfiguration();

	/**
	 * <p>
	 * Optional scoring configuration.
	 * </p>
	 * <p>
	 * If set to default (empty), no scoring rules will be applied at all.
	 * </p>
	 * 
	 * @param tenant
	 * @return
	 */
	@NonNull
	private ScoringConfiguration scoring = new ScoringConfiguration();

	/**
	 * <p>
	 * List of full canonical class names of the
	 * {@link de.cxp.ocs.spi.search.RescorerProvider} that should activated
	 * for the according tenant.
	 * </p>
	 * <p>
	 * Use the 'pluginConfiguration' setting to add custom configuration for the
	 * activated rescorers.
	 * <p>
	 * Per defaults it's empty, so no rescorers are used.
	 * </p>
	 */
	private List<String> rescorers = new ArrayList<>();

	/**
	 * Get query relaxation chain. If empty, only the DefaultQueryBuilder will
	 * be used.
	 * 
	 * @param tenant
	 * @return
	 */
	@NonNull
	private final List<QueryConfiguration> queryConfigs = new ArrayList<>();

	/**
	 * <p>
	 * Specific sorting configuration, e.g. to specify which sorting options
	 * should be part of result.
	 * </p>
	 * <p>
	 * If empty list is retruned, the sortings will be delivered in default
	 * style according to indexed sorting fields
	 * </p>
	 * 
	 * @param tenant
	 * @return
	 */
	@NonNull
	private final List<SortOptionConfiguration> sortConfigs = new ArrayList<>();

	/**
	 * <p>
	 * Settings for the single possible customization classes, like rescorers,
	 * query analyzers etc.
	 * </p>
	 * <p>
	 * As a key the full canonical class name of the customization class is
	 * expected.
	 * </p>
	 * <p>
	 * The value is an optional string-to-string map with arbitrary settings for
	 * the according customization class that will be passed to it with the
	 * "initialize" method.
	 * </p>
	 * <p>
	 * Check the java-doc of the according classes for more details.
	 * </p>
	 */
	private final Map<String, Map<String, String>> pluginConfiguration = new LinkedHashMap<>();
}
