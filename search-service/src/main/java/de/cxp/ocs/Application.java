package de.cxp.ocs;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;

import de.cxp.ocs.config.ApplicationProperties;
import de.cxp.ocs.config.DefaultSearchConfigrationProvider;
import de.cxp.ocs.elasticsearch.ElasticSearchBuilder;
import de.cxp.ocs.elasticsearch.RestClientBuilderFactory;
import de.cxp.ocs.plugin.ExtensionSupplierRegistry;
import de.cxp.ocs.plugin.PluginManager;
import de.cxp.ocs.spi.search.ESQueryFactory;
import de.cxp.ocs.spi.search.RescorerProvider;
import de.cxp.ocs.spi.search.SearchConfigurationProvider;
import de.cxp.ocs.spi.search.UserQueryAnalyzer;
import de.cxp.ocs.spi.search.UserQueryPreprocessor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.spring.autoconfigure.MeterRegistryCustomizer;

@SpringBootApplication
@RefreshScope
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	public ElasticSearchBuilder getESBuilder(ApplicationProperties properties, MeterRegistry registry) {
		RestClientBuilder restClientBuilder = RestClientBuilderFactory.createRestClientBuilder(properties.getConnectionConfiguration());
		return new ElasticSearchBuilder(restClientBuilder);
	}

	@Bean
	public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags(
			@Value("${spring.application.name}") String applicationName) {
		return registry -> {
			registry.config().commonTags("application", applicationName);
		};
	}

	@Bean
	public SearchPlugins pluginManager(ApplicationProperties properties) {
		SearchPlugins plugins = new SearchPlugins();
		PluginManager pluginManager = new PluginManager(properties.getDisabledPlugins(), properties.getPreferedPlugins());
		plugins.setConfigurationProvider(pluginManager.loadPrefered(SearchConfigurationProvider.class)
				.orElseGet(() -> new DefaultSearchConfigrationProvider(properties)));
		plugins.setEsQueryFactories(extensionsAsSuppliers(pluginManager.loadAll(ESQueryFactory.class)));
		plugins.setUserQueryAnalyzers(extensionsAsSuppliers(pluginManager.loadAll(UserQueryAnalyzer.class)));
		plugins.setUserQueryPreprocessors(extensionsAsSuppliers(pluginManager.loadAll(UserQueryPreprocessor.class)));
		plugins.setRescorers(extensionsAsSuppliers(pluginManager.loadAll(RescorerProvider.class)));
		return plugins;
	}

	private <T> Map<String, Supplier<? extends T>> extensionsAsSuppliers(List<T> instances) {
		ExtensionSupplierRegistry<T> registry = new ExtensionSupplierRegistry<T>();
		instances.forEach(registry::register);
		return registry.getExtensionSuppliers();
	}

}
