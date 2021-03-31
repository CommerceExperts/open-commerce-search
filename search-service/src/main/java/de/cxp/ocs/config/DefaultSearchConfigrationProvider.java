package de.cxp.ocs.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import de.cxp.ocs.spi.search.SearchConfigurationProvider;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DefaultSearchConfigrationProvider implements SearchConfigurationProvider {

	@NonNull
	private final ApplicationProperties properties;

	@Override
	public SearchConfiguration getTenantSearchConfiguration(String tenant) {
		SearchConfiguration mergedConfig = new SearchConfiguration();

		mergedConfig.setIndexName(getTargetIndex(tenant).orElse(tenant));

		getQueryProcessing(tenant).ifPresent(mergedConfig::setQueryProcessing);
		getFacetConfiguration(tenant).ifPresent(mergedConfig::setFacetConfiguration);
		getScoringConfiguration(tenant).ifPresent(mergedConfig::setScoring);

		mergedConfig.getQueryConfigs().addAll(getQueryConfiguration(tenant));
		mergedConfig.getSortConfigs().addAll(getSortConfigs(tenant));

		mergedConfig.getRescorers().addAll(properties.getTenantConfig()
				.getOrDefault(tenant, properties.getDefaultTenantConfig())
				.getRescorers());

		// merge plugin configuration
		// (tenant specific may overwrite default config)
		Optional.ofNullable(properties.getDefaultTenantConfig().getPluginConfiguration())
				.ifPresent(mergedConfig.getPluginConfiguration()::putAll);
		Optional.ofNullable(properties.getTenantConfig().get(tenant))
				.map(ApplicationSearchProperties::getPluginConfiguration)
				.ifPresent(mergedConfig.getPluginConfiguration()::putAll);

		return mergedConfig;
	}

	public Optional<String> getTargetIndex(String tenant) {
		return Optional.ofNullable(properties.getTenantConfig()
				.getOrDefault(tenant, properties.getDefaultTenantConfig()).getIndexName());
	}

	public Optional<QueryProcessingConfiguration> getQueryProcessing(String tenant) {
		return Optional.ofNullable(properties.getTenantConfig()
				.getOrDefault(tenant, properties.getDefaultTenantConfig()).getQueryProcessing());
	}

	public Optional<ScoringConfiguration> getScoringConfiguration(String tenant) {
		ApplicationSearchProperties tenantConfig = properties.getTenantConfig().getOrDefault(tenant, properties.getDefaultTenantConfig());
		if (tenantConfig.disableScorings) {
			return Optional.empty();
		}
		return Optional.ofNullable(tenantConfig.getScoringConfiguration());
	}

	public Optional<FacetConfiguration> getFacetConfiguration(String tenant) {
		ApplicationSearchProperties tenantConfig = properties.getTenantConfig().getOrDefault(tenant, properties.getDefaultTenantConfig());
		if (tenantConfig.disableFacets) {
			return Optional.empty();
		}
		return Optional.ofNullable(tenantConfig.getFacetConfiguration());
	}

	public List<QueryConfiguration> getQueryConfiguration(String tenant) {
		ApplicationSearchProperties tenantConfig = properties.getTenantConfig().getOrDefault(tenant, properties.getDefaultTenantConfig());
		if (tenantConfig.disableQueryConfig) {
			return null;
		}
		return new ArrayList<>(tenantConfig.getQueryConfiguration().values());
	}

	public List<SortOptionConfiguration> getSortConfigs(String tenant) {
		ApplicationSearchProperties tenantConfig = properties.getTenantConfig().getOrDefault(tenant, properties.getDefaultTenantConfig());
		if (tenantConfig.disableSortingConfig) {
			return Collections.emptyList();
		}
		return tenantConfig.getSortConfiguration();
	}


	@Override
	public Set<String> getConfiguredTenants() {
		return properties.getTenantConfig().keySet();
	}

}
