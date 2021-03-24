package de.cxp.ocs.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

import de.cxp.ocs.spi.search.SearchConfigurationProvider;
import lombok.NonNull;

public class DefaultSearchConfigrationProvider implements SearchConfigurationProvider {

	@Autowired
	@NonNull
	private ApplicationProperties properties;


	@Override
	public SearchConfiguration getTenantSearchConfiguration(String tenant) {
		SearchConfiguration mergedConfig = new SearchConfiguration();

		mergedConfig.setIndexName(getTargetIndex(tenant).orElse(tenant));

		getFacetConfiguration(tenant).ifPresent(mergedConfig::setFacetConfiguration);
		getScoringConfiguration(tenant).ifPresent(mergedConfig::setScoring);

		mergedConfig.getQueryConfigs().addAll(getQueryConfiguration(tenant));
		mergedConfig.getSortConfigs().addAll(getSortConfigs(tenant));

		return mergedConfig;
	}

	public Optional<String> getTargetIndex(String tenant) {
		return Optional.ofNullable(properties.getTenantConfig()
				.getOrDefault(tenant, properties.getDefaultTenantConfig()).getIndexName());
	}

	public Optional<ScoringConfiguration> getScoringConfiguration(String tenant) {
		TenantSearchConfiguration tenantConfig = properties.getTenantConfig().getOrDefault(tenant, properties.getDefaultTenantConfig());
		if (tenantConfig.disableScorings) {
			return Optional.empty();
		}
		return Optional.ofNullable(tenantConfig.getScoringConfiguration());
	}

	public Optional<FacetConfiguration> getFacetConfiguration(String tenant) {
		TenantSearchConfiguration tenantConfig = properties.getTenantConfig().getOrDefault(tenant, properties.getDefaultTenantConfig());
		if (tenantConfig.disableFacets) {
			return Optional.empty();
		}
		return Optional.ofNullable(tenantConfig.getFacetConfiguration());
	}

	public List<QueryConfiguration> getQueryConfiguration(String tenant) {
		TenantSearchConfiguration tenantConfig = properties.getTenantConfig().getOrDefault(tenant, properties.getDefaultTenantConfig());
		if (tenantConfig.disableQueryConfig) {
			return null;
		}
		return new ArrayList<>(tenantConfig.getQueryConfiguration().values());
	}

	public List<SortOptionConfiguration> getSortConfigs(String tenant) {
		TenantSearchConfiguration tenantConfig = properties.getTenantConfig().getOrDefault(tenant, properties.getDefaultTenantConfig());
		if (tenantConfig.disableSortingConfig) {
			return Collections.emptyList();
		}
		return tenantConfig.getSortConfigs();
	}

	@Override
	public Set<String> getConfiguredTenants() {
		return properties.getTenantConfig().keySet();
	}

}
