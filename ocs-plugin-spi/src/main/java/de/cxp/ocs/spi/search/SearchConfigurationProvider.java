package de.cxp.ocs.spi.search;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import de.cxp.ocs.config.FacetConfiguration;
import de.cxp.ocs.config.QueryConfiguration;
import de.cxp.ocs.config.ScoringConfiguration;
import de.cxp.ocs.config.SortOptionConfiguration;

public interface SearchConfigurationProvider {

	Set<String> getConfiguredTenants();

	/**
	 * Optional index-name that should be adressed by the tenant. If empty, the
	 * index name will be set to the tenant name.
	 * 
	 * @param tenant
	 * @return
	 */
	Optional<String> getTargetIndex(String tenant);

	/**
	 * <p>
	 * Optional scoring configuration.
	 * </p>
	 * <p>
	 * If empty, no scoring rules will be applied at all.
	 * </p>
	 * 
	 * @param tenant
	 * @return
	 */
	Optional<ScoringConfiguration> getScoringConfiguration(String tenant);

	/**
	 * <p>
	 * Optional facet configuration to customize the way the facets should be
	 * displayed.
	 * </p>
	 * <p>
	 * If empty, facets will be generated according to default settings.
	 * </p>
	 * 
	 * @param tenant
	 * @return
	 */
	Optional<FacetConfiguration> getFacetConfiguration(String tenant);

	/**
	 * Get query relaxation chain.
	 * 
	 * @param tenant
	 * @return can be a empty list, if only the DefaultQueryBuilder should be
	 *         used.
	 */
	List<QueryConfiguration> getQueryConfiguration(String tenant);

	/**
	 * Specific sorting configuration, e.g. to specify which sorting options
	 * should be part of result.
	 * 
	 * @param tenant
	 * @return empty list if sortings should be delivered in
	 *         default style according to indexed sorting fields
	 */
	List<SortOptionConfiguration> getSortConfigs(String tenant);

}
