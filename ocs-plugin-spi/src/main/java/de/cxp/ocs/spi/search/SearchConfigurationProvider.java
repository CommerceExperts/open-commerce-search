package de.cxp.ocs.spi.search;

import java.util.Set;

import de.cxp.ocs.config.SearchConfiguration;

/**
 * SPI Interface to provide search configurations.
 */
public interface SearchConfigurationProvider {

	/**
	 * Returns the list of all configured tenants.
	 * 
	 * @return
	 */
	Set<String> getConfiguredTenants();

	/**
	 * Returns the search configuration for the specified tenant.
	 * 
	 * @param tenant
	 * @return
	 */
	SearchConfiguration getTenantSearchConfiguration(String tenant);

}
