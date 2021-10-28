package de.cxp.ocs.spi.search;

import java.util.Set;

import de.cxp.ocs.config.SearchConfiguration;

/**
 * SPI Interface to provide search configurations.
 */
public interface SearchConfigurationProvider {

	/**
	 * @return
	 *         the list of all configured tenants
	 */
	Set<String> getConfiguredTenants();

	/**
	 * @param tenant
	 *        tenant name
	 * @return
	 *         the search configuration for the specified tenant
	 */
	SearchConfiguration getTenantSearchConfiguration(String tenant);

}
