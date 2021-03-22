package de.cxp.ocs;

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
import de.cxp.ocs.plugin.PluginManager;
import de.cxp.ocs.spi.search.SearchConfigurationProvider;
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
	public PluginManager pluginManager(ApplicationProperties properties) {
		return new PluginManager(properties.getDisabledPlugins(), properties.getPreferedPlugins());
	}

	@Bean
	public SearchConfigurationProvider configProvider(PluginManager pluginManager, ApplicationProperties properties) {
		return pluginManager.loadPrefered(SearchConfigurationProvider.class)
				.orElseGet(DefaultSearchConfigrationProvider::new);
	}
}
