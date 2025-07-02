package de.cxp.ocs.config;

import java.util.*;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Final search-configuration that contains all the fetched configuration
 * objects.
 */
@Getter // write setters with java-doc!
@NoArgsConstructor
@EqualsAndHashCode
public class SearchConfiguration {

	private String indexName;

	private Locale locale = Locale.ROOT;

	private QueryProcessingConfiguration queryProcessing = new QueryProcessingConfiguration();

	private FacetConfiguration facetConfiguration = new FacetConfiguration();

	private ScoringConfiguration scoring = new ScoringConfiguration();

	private List<String> rescorers = new ArrayList<>();

	private List<QueryConfiguration> queryConfigs = new ArrayList<>();

	private final Map<String, Map<String, String>> pluginConfiguration = new LinkedHashMap<>();

	private List<SortOptionConfiguration> sortConfigs = new ArrayList<>();

	private String variantPickingStrategy = "pickIfBestScored";

	public enum ProductSetType {
		Static, Dynamic, Generic, Querystring;

		public static ProductSetType fromString(String s) {
			if (s == null || s.isBlank() || s.length() < 2) {
				throw new IllegalArgumentException("Invalid enum name " + s);
			}
			return ProductSetType.valueOf(Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase());
		}
	}

	private EnumMap<ProductSetType, String> heroProductResolver = new EnumMap<>(ProductSetType.class);

	public SearchConfiguration setHeroProductResolver(Map<ProductSetType, String> customResolverClasses) {
		heroProductResolver.putAll(customResolverClasses);
		return this;
	}

	/**
	 * Optional index-name that should be addressed by the tenant. If null, the
	 * index name will be set to the tenant name.
	 * 
	 * @param indexName
	 *        index name
	 * @return self
	 */
	public SearchConfiguration setIndexName(String indexName) {
		this.indexName = indexName;
		return this;
	}

	/**
	 * Set locale to handle certain language specific normalization inside the searcher,
	 * like case-insensitive filters.
	 * Defaults to ROOT locale.
	 * 
	 * @param locale
	 *        locale to set
	 * @return self
	 */
	public SearchConfiguration setLocale(Locale locale) {
		this.locale = locale;
		return this;
	}

	/**
	 * Optional query processing configuration. If nothing special is defined,
	 * the standard behavior will be used.
	 * 
	 * @param queryProcessing
	 *        query processing configuration
	 * @return self
	 */
	public SearchConfiguration setQueryProcessing(@NonNull QueryProcessingConfiguration queryProcessing) {
		this.queryProcessing = queryProcessing;
		return this;
	}

	/**
	 * <p>
	 * Optional facet configuration to customize the way the facets should be
	 * displayed.
	 * </p>
	 * <p>
	 * If default/empty, facets will be generated according to default settings.
	 * </p>
	 * 
	 * @param facetConfiguration
	 *        facet configuration
	 * @return self
	 */
	public SearchConfiguration setFacetConfiguration(@NonNull FacetConfiguration facetConfiguration) {
		this.facetConfiguration = facetConfiguration;
		return this;
	}

	/**
	 * <p>
	 * Optional scoring configuration.
	 * </p>
	 * <p>
	 * If set to default (empty), no scoring rules will be applied at all.
	 * </p>
	 * 
	 * @param scoring
	 *        scoring configuration
	 * @return self
	 */
	public SearchConfiguration setScoring(@NonNull ScoringConfiguration scoring) {
		this.scoring = scoring;
		return this;
	}

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
	 * 
	 * @param rescorers
	 *        list of canonical rescorer-provider class names
	 * @return self
	 */
	public SearchConfiguration setRescorers(@NonNull List<String> rescorers) {
		this.rescorers = rescorers;
		return this;
	}

	/**
	 * Get query relaxation chain. If empty, only the DefaultQueryBuilder will
	 * be used.
	 * 
	 * @param queryConfigs
	 *        list of query configuration
	 * @return self
	 */
	public SearchConfiguration setQueryConfigs(@NonNull List<QueryConfiguration> queryConfigs) {
		this.queryConfigs = queryConfigs;
		return this;
	}

	/**
	 * <p>
	 * Specific which sorting option should be part of result.
	 * </p>
	 * <p>
	 * If list is empty, all possible sorting options will be delivered in
	 * default style according to indexed sorting fields.
	 * </p>
	 * 
	 * @param sortConfigs
	 *        list of sort configuration
	 * @return self
	 */
	public SearchConfiguration setSortConfigs(@NonNull List<SortOptionConfiguration> sortConfigs) {
		this.sortConfigs = sortConfigs;
		return this;
	}

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
	 * 
	 * @param pluginClassName
	 *        full class names
	 * @param pluginConfig
	 *        config data for that plugin class
	 * @return self
	 */
	public SearchConfiguration addPluginConfiguration(@NonNull String pluginClassName, @NonNull Map<String, String> pluginConfig) {
		pluginConfiguration.put(pluginClassName, pluginConfig);
		return this;
	}

	/**
	 * 
	 * Set when variants should be the result hit instead of their main product.
	 * Could be one of the following predefined strategies:
	 * <ul>
	 * <li>"pickAlways": pick first variant if available.</li>
	 * <li>"pickIfDrilledDown": Pick best variant if at a single hit some
	 * variants were filtered.</li>
	 * <li>"pickIfBestScored": Pick first variant, if it has a better score than
	 * the second one or if it's the only one left.</li>
	 * <li>"pickIfSingleHit": Picks a variant only if there are no other
	 * variants matching.</li>
	 * </ul>
	 * 
	 * @param variantPickingStrategy
	 *        name of variant picking strategy
	 */
	public void setVariantPickingStrategy(String variantPickingStrategy) {
		this.variantPickingStrategy = variantPickingStrategy;
	}
}
