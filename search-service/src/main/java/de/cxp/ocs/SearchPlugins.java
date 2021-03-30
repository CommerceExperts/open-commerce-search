package de.cxp.ocs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import de.cxp.ocs.plugin.ExtensionSupplierRegistry;
import de.cxp.ocs.plugin.PluginManager;
import de.cxp.ocs.spi.search.ConfigurableExtension;
import de.cxp.ocs.spi.search.ESQueryFactory;
import de.cxp.ocs.spi.search.RescorerProvider;
import de.cxp.ocs.spi.search.SearchConfigurationProvider;
import de.cxp.ocs.spi.search.UserQueryAnalyzer;
import de.cxp.ocs.spi.search.UserQueryPreprocessor;
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

	private <T> Map<String, Supplier<? extends T>> loadSuppliers(Class<T> clazz) {
		ExtensionSupplierRegistry<T> registry = new ExtensionSupplierRegistry<T>();
		pluginManager.loadAll(clazz).forEach(registry::register);
		return Collections.unmodifiableMap(registry.getExtensionSuppliers());
	}

	public static <T> Optional<T> initialize(String clazz, Map<String, Supplier<? extends T>> suppliers, Map<String, String> settings) {
		if (clazz == null || suppliers == null) return Optional.empty();
		Supplier<? extends T> supplier = suppliers.get(clazz);
		if (supplier == null) return Optional.empty();

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
			SearchPlugins.initialize(clazz, suppliers, pluginSettings.get(clazz))
					.ifPresent(instances::add);
		}
		return instances;
	}
}
