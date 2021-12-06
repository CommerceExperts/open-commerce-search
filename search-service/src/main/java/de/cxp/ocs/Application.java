package de.cxp.ocs;

import java.util.Optional;

import org.elasticsearch.client.RestClientBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.module.SimpleModule;

import de.cxp.ocs.config.ApplicationProperties;
import de.cxp.ocs.config.DefaultSearchConfigrationProvider;
import de.cxp.ocs.elasticsearch.ElasticSearchBuilder;
import de.cxp.ocs.elasticsearch.RestClientBuilderFactory;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.params.DynamicProductSet;
import de.cxp.ocs.model.params.ProductSet;
import de.cxp.ocs.model.params.StaticProductSet;
import de.cxp.ocs.plugin.PluginManager;
import de.cxp.ocs.spi.search.SearchConfigurationProvider;

@SpringBootApplication
@RefreshScope
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	public ElasticSearchBuilder getESBuilder(RestClientBuilder restClientBuilder) {
		return new ElasticSearchBuilder(restClientBuilder);
	}

	@Bean
	public RestClientBuilder getRestClientBuilder(ApplicationProperties properties) {
		return RestClientBuilderFactory.createRestClientBuilder(properties.getConnectionConfiguration());
	}

	@Bean
	public SearchPlugins pluginManager(ApplicationProperties properties) {
		PluginManager pluginManager = new PluginManager(properties.getDisabledPlugins(), properties.getPreferedPlugins());

		Optional<SearchConfigurationProvider> configurationProvider = pluginManager.loadPrefered(SearchConfigurationProvider.class);
		SearchConfigurationProvider defaultConfigProvider = new DefaultSearchConfigrationProvider(properties);
		configurationProvider.ifPresent(scp -> scp.setDefaultProvider(new DefaultSearchConfigrationProvider(properties)));

		return new SearchPlugins(pluginManager, configurationProvider.orElse(defaultConfigProvider));
	}

	@Bean
	public Module mixinModule() {
		SimpleModule module = new SimpleModule();

		module.setMixInAnnotation(ProductSet.class, WithTypeInfo.class);
		module.setMixInAnnotation(Document.class, NoNullValues.class);
		module.registerSubtypes(new NamedType(DynamicProductSet.class, "dynamic"), new NamedType(StaticProductSet.class, "static"));

		return module;
	}

	@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
	public static abstract class WithTypeInfo {}

	@JsonInclude(Include.NON_NULL)
	public static abstract class NoNullValues {}

}
