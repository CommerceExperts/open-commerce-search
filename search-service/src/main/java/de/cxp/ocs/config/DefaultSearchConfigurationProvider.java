package de.cxp.ocs.config;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import de.cxp.ocs.spi.search.SearchConfigurationProvider;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DefaultSearchConfigurationProvider implements SearchConfigurationProvider {

	@NonNull
	private final ApplicationProperties properties;

	@Override
	public SearchConfiguration getTenantSearchConfiguration(String tenant) {
		SearchConfiguration mergedConfig = new SearchConfiguration();

		mergedConfig.setIndexName(getTargetIndex(tenant).orElse(tenant));

		getQueryProcessing(tenant).ifPresent(mergedConfig::setQueryProcessing);
		getFacetConfiguration(tenant).ifPresent(mergedConfig::setFacetConfiguration);
		getScoringConfiguration(tenant).ifPresent(mergedConfig::setScoring);
		getVariantPickingStrategy(tenant).ifPresent(mergedConfig::setVariantPickingStrategy);

		mergedConfig.getQueryConfigs().addAll(getQueryConfiguration(tenant));
		mergedConfig.getSortConfigs().addAll(getSortConfigs(tenant));
		mergedConfig.getRescorers().addAll(getRescorers(tenant));

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

	private Optional<String> getVariantPickingStrategy(String tenant) {
		return Optional.ofNullable(properties.getTenantConfig()
				.getOrDefault(tenant, properties.getDefaultTenantConfig()).getVariantPickingStrategy());
	}

	public Optional<QueryProcessingConfiguration> getQueryProcessing(String tenant) {
		return getSubConfiguration(tenant, ApplicationSearchProperties::getQueryProcessing,
				tenantConfig -> tenantConfig == null);
	}

	public Optional<ScoringConfiguration> getScoringConfiguration(String tenant) {
		return getSubConfiguration(tenant, ApplicationSearchProperties::getScoringConfiguration,
				tenantConfig -> tenantConfig == null || tenantConfig.useDefaultScoringConfig);
	}

	public Optional<FacetConfiguration> getFacetConfiguration(String tenant) {
		return getSubConfiguration(tenant, ApplicationSearchProperties::getFacetConfiguration,
				tenantConfig -> tenantConfig == null || tenantConfig.useDefaultFacetConfig);
	}

	public Collection<QueryConfiguration> getQueryConfiguration(String tenant) {
		return getSubConfiguration(tenant, ApplicationSearchProperties::getQueryConfiguration,
				tenantConfig -> tenantConfig == null || tenantConfig.useDefaultQueryConfig)
						.map(map -> {
							// if no name is specified for the query configs,
							// set it to the map key
							map.forEach((key, conf) -> {
								if (conf.getName() == null) {
									conf.setName(key);
								}
							});
							return map.values();
						})
						.orElseGet(Collections::emptyList);
	}

	public Collection<SortOptionConfiguration> getSortConfigs(String tenant) {
		return getSubConfiguration(tenant, ApplicationSearchProperties::getSortConfiguration,
				tenantConfig -> tenantConfig == null || tenantConfig.useDefaultSortConfig)
						.orElseGet(Collections::emptyList);
	}

	private Collection<String> getRescorers(String tenant) {
		return getSubConfiguration(tenant, ApplicationSearchProperties::getRescorers,
				tenantConfig -> tenantConfig == null)
						.orElseGet(Collections::emptyList);
	}

	private <T> Optional<T> getSubConfiguration(String tenant, Function<ApplicationSearchProperties, T> getter, Predicate<ApplicationSearchProperties> useDefault) {
		ApplicationSearchProperties tenantConfig = properties.getTenantConfig().get(tenant);
		if (useDefault.test(tenantConfig)) {
			return Optional.ofNullable(getter.apply(properties.getDefaultTenantConfig()));
		}
		return Optional.ofNullable(getter.apply(tenantConfig));
	}

	@Override
	public Set<String> getConfiguredTenants() {
		return properties.getTenantConfig().keySet();
	}

	@Override
	public void setDefaultProvider(SearchConfigurationProvider defaultSearchConfigrationProvider) {
		// nothing to do. we are the default
	}

}
