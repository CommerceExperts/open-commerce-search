package de.cxp.ocs.spi.search;

import java.util.Set;

import de.cxp.ocs.config.SearchConfiguration;

public interface SearchConfigurationProvider {

	Set<String> getConfiguredTenants();

	SearchConfiguration getTenantSearchConfiguration(String tenant);

}
