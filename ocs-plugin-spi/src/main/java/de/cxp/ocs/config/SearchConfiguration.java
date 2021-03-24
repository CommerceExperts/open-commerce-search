package de.cxp.ocs.config;

import java.util.ArrayList;
import java.util.List;

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
}
