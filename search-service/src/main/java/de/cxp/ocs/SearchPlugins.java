package de.cxp.ocs;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.MDC;

import de.cxp.ocs.elasticsearch.prodset.ProductSetResolver;
import de.cxp.ocs.plugin.ExtensionSupplierRegistry;
import de.cxp.ocs.plugin.PluginManager;
import de.cxp.ocs.spi.search.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class SearchPlugins {

	private final PluginManager pluginManager;

	@Getter
	private final SearchConfigurationProvider configurationProvider;

	private Map<String, Supplier<? extends ESQueryFactory>> esQueryFactories;

	private Map<String, Supplier<? extends UserQueryAnalyzer>> userQueryAnalyzers;

	private Map<String, Supplier<? extends UserQueryPreprocessor>> userQueryPreprocessors;

	private Map<String, Supplier<? extends RescorerProvider>> rescorers;

	private Map<String, Supplier<? extends ProductSetResolver>> heroProductResolvers;

	private Set<Supplier<? extends CustomFacetCreator>> customFacetCreators;

	// use lazy loading to guarantee, that
	// a) all suppliers are only loaded once
	// b) only the requested suppliers are loaded
	// c) a new extension class is not forgotten at some registry routine

	public Map<String, Supplier<? extends ESQueryFactory>> getEsQueryFactories() {
		if (esQueryFactories == null) {
			esQueryFactories = loadSuppliers(ESQueryFactory.class);
		}
		return esQueryFactories;
	}

	public Map<String, Supplier<? extends UserQueryAnalyzer>> getUserQueryAnalyzers() {
		if (userQueryAnalyzers == null) {
			userQueryAnalyzers = loadSuppliers(UserQueryAnalyzer.class);
		}
		return userQueryAnalyzers;
	}

	public Map<String, Supplier<? extends UserQueryPreprocessor>> getUserQueryPreprocessors() {
		if (userQueryPreprocessors == null) {
			userQueryPreprocessors = loadSuppliers(UserQueryPreprocessor.class);
		}
		return userQueryPreprocessors;
	}

	public Map<String, Supplier<? extends RescorerProvider>> getRescorerProviders() {
		if (rescorers == null) {
			rescorers = loadSuppliers(RescorerProvider.class);
		}
		return rescorers;
	}

	public Map<String, Supplier<? extends ProductSetResolver>> getHeroProductResolvers() {
		if (heroProductResolvers == null) {
			heroProductResolvers = loadSuppliers(ProductSetResolver.class);
		}
		return heroProductResolvers;
	}

	public Set<Supplier<? extends CustomFacetCreator>> getFacetCreators() {
		if (customFacetCreators == null) {
			Map<String, Supplier<? extends CustomFacetCreator>> customFacetCreatorsMap = loadSuppliers(CustomFacetCreator.class);
			// fetch each supplier once
			customFacetCreators = customFacetCreatorsMap.entrySet().stream()
					// filter by canonical name since it's unique and we should not have any duplicates
					.filter(entry -> entry.getKey().contains("."))
					.map(Entry::getValue).collect(Collectors.toSet());
		}
		return customFacetCreators;
	}

	private <T> Map<String, Supplier<? extends T>> loadSuppliers(Class<T> clazz) {
		ExtensionSupplierRegistry<T> registry = new ExtensionSupplierRegistry<T>();
		pluginManager.loadAll(clazz).forEach(registry::register);
		return Collections.unmodifiableMap(registry.getExtensionSuppliers());
	}

	public static <T> Optional<T> initialize(String clazz, Map<String, Supplier<? extends T>> suppliers, Map<String, String> settings) {
		if (clazz == null || suppliers == null) return Optional.empty();
		Supplier<? extends T> supplier = suppliers.get(clazz);
		if (supplier == null) {
			log.error("no supplier found for configured plugin class {}", clazz);
			return Optional.empty();
		}

		try {
			T instance = supplier.get();
			if (instance instanceof ConfigurableExtension) {
				((ConfigurableExtension) instance).initialize(settings != null ? settings : Collections.emptyMap());
			}
			return Optional.of(instance);
		}
		catch (Exception e) {
			log.error("failed to initialize plugin {}", clazz, e);
		}
		return Optional.empty();
	}

	public static <T> List<T> initialize(List<String> classNames, Map<String, Supplier<? extends T>> suppliers, Map<String, Map<String, String>> pluginSettings) {
		List<T> instances = new ArrayList<>(classNames.size());
		for (String clazz : classNames) {
			Optional<T> initialized = SearchPlugins.initialize(clazz, suppliers, pluginSettings.get(clazz));
			initialized.ifPresent(i -> {
				instances.add(i);
				log.info("initialized plugin class {} for tenant {}", clazz, MDC.get("tenant"));
			});
		}
		return instances;
	}

}
