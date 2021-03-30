package de.cxp.ocs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import de.cxp.ocs.spi.search.ConfigurableExtension;
import de.cxp.ocs.spi.search.ESQueryFactory;
import de.cxp.ocs.spi.search.RescorerProvider;
import de.cxp.ocs.spi.search.SearchConfigurationProvider;
import de.cxp.ocs.spi.search.UserQueryAnalyzer;
import de.cxp.ocs.spi.search.UserQueryPreprocessor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class SearchPlugins {

	private SearchConfigurationProvider configurationProvider;

	private Map<String, Supplier<? extends ESQueryFactory>> esQueryFactories;

	private Map<String, Supplier<? extends UserQueryAnalyzer>> userQueryAnalyzers;

	private Map<String, Supplier<? extends UserQueryPreprocessor>> userQueryPreprocessors;

	private Map<String, Supplier<? extends RescorerProvider>> rescorers;

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
