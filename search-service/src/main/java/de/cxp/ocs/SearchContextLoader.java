package de.cxp.ocs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import de.cxp.ocs.config.FieldConfigIncompatibilityException;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.config.FieldConfiguration;
import de.cxp.ocs.config.SearchConfiguration;
import de.cxp.ocs.config.SearchConfiguration.ProductSetType;
import de.cxp.ocs.elasticsearch.ElasticSearchBuilder;
import de.cxp.ocs.elasticsearch.FieldConfigFetcher;
import de.cxp.ocs.elasticsearch.prodset.HeroProductHandler;
import de.cxp.ocs.elasticsearch.prodset.ProductSetResolver;
import de.cxp.ocs.spi.search.UserQueryPreprocessor;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@AllArgsConstructor
public class SearchContextLoader {

	@Autowired
	@NonNull
	private ElasticSearchBuilder esBuilder;

	@Autowired
	@NonNull
	private SearchPlugins plugins;

	public SearchContext loadContext(String tenant) {
		SearchConfiguration searchConfig = plugins.getConfigurationProvider().getTenantSearchConfiguration(tenant);

		Map<ProductSetType, ProductSetResolver> resolvers = loadProductSetResolver(searchConfig);

		FieldConfigIndex fieldConfigIndex = loadFieldConfigIndex(searchConfig);

		List<UserQueryPreprocessor> userQueryPreprocessors = SearchPlugins.initialize(
				searchConfig.getQueryProcessing().getUserQueryPreprocessors(),
				plugins.getUserQueryPreprocessors(),
				searchConfig.getPluginConfiguration());
		log.info("Using index(es) {} for tenant {}", searchConfig.getIndexName(), tenant);
		return new SearchContext(fieldConfigIndex, searchConfig, userQueryPreprocessors, new HeroProductHandler(resolvers));
	}

	private Map<ProductSetType, ProductSetResolver> loadProductSetResolver(SearchConfiguration searchConfig) {
		Map<String, Supplier<? extends ProductSetResolver>> heroProductResolverSuppliers = plugins.getHeroProductResolvers();
		Map<ProductSetType, ProductSetResolver> resolvers = new EnumMap<>(ProductSetType.class);
		for (ProductSetType productSetType : ProductSetType.values()) {
			Optional.ofNullable(searchConfig.getHeroProductResolver().get(productSetType))
					.flatMap(productResolverClassName -> SearchPlugins.initialize(productResolverClassName, heroProductResolverSuppliers, searchConfig.getPluginConfiguration().get(productResolverClassName)))
					.ifPresent(resolver -> resolvers.put(productSetType, resolver));
		}
		return resolvers;
	}

	public FieldConfigIndex loadFieldConfigIndex(SearchConfiguration searchConfig) {
		String[] tenantIndexes = StringUtils.split(searchConfig.getIndexName(), ',');
		FieldConfigIndex fieldConfigIndex = null;
		Set<String> validIndexNames = new HashSet<>();
		for (String indexName : tenantIndexes) {
			FieldConfiguration fieldConfig = loadFieldConfiguration(indexName);
			if (fieldConfigIndex == null) {
				fieldConfigIndex = new FieldConfigIndex(fieldConfig);
				validIndexNames.add(indexName);
			}
			else {
				try {
					fieldConfigIndex.addFieldConfig(fieldConfig);
					validIndexNames.add(indexName);
				}
				catch (FieldConfigIncompatibilityException e) {
					log.error("field-configuration of indexes {} are not compatible! Will omit usage of {}.",
							searchConfig.getIndexName(), indexName, e);
				}
			}
		}
		if (tenantIndexes.length > validIndexNames.size()) {
			searchConfig.setIndexName(StringUtils.join(validIndexNames, ','));
		}
		return fieldConfigIndex;
	}

	private FieldConfiguration loadFieldConfiguration(String indexName) {
		FieldConfiguration fieldConfig;
		try {
			fieldConfig = new FieldConfigFetcher(esBuilder.getRestHLClient()).fetchConfig(indexName);
		}
		catch (IOException e) {
			log.error("couldn't fetch field configuration from index {}", indexName);
			throw new UncheckedIOException(e);
		}
		return fieldConfig;
	}
}
