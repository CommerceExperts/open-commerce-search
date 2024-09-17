package de.cxp.ocs;

import java.util.Optional;

import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import de.cxp.ocs.conf.ApplicationProperties;
import de.cxp.ocs.conf.DefaultIndexerConfigurationProvider;
import de.cxp.ocs.elasticsearch.ElasticSearchBuilder;
import de.cxp.ocs.elasticsearch.RestClientBuilderFactory;
import de.cxp.ocs.model.index.Attribute;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.FacetEntry;
import de.cxp.ocs.model.result.HierarchialFacetEntry;
import de.cxp.ocs.plugin.PluginManager;
import de.cxp.ocs.spi.indexer.IndexerConfigurationProvider;
import de.cxp.ocs.util.DocumentDeserializer;
import de.cxp.ocs.util.ProductDeserializer;
import lombok.extern.slf4j.Slf4j;

@SpringBootApplication
@EnableScheduling
@RefreshScope
@Slf4j
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	public RestClientBuilder getRestClientBuilder(ApplicationProperties properties) {
		return RestClientBuilderFactory.createRestClientBuilder(properties.getConnectionConfiguration());
	}

	@Bean
	@SuppressWarnings("deprecation")
	public RestHighLevelClient getRestHighLevelClient(RestClientBuilder clientBuilder) {
		return new RestHighLevelClient(clientBuilder);
	}

	@Bean
	public ElasticSearchBuilder getESBuilder(RestClientBuilder restClientBuilder) {
		return new ElasticSearchBuilder(restClientBuilder);
	}

	@Bean
	public PluginManager getPluginManager(ApplicationProperties properties) {
		return new PluginManager(properties.getDisabledPlugins(), properties.getPreferedPlugins());
	}

	@Bean
	public IndexerConfigurationProvider configurationProvider(PluginManager pluginManager, ApplicationProperties properties) {
		log.info("going to connect to Elasticsearch hosts {}", properties.getConnectionConfiguration().getHosts());
		DefaultIndexerConfigurationProvider defaultConfigProvider = new DefaultIndexerConfigurationProvider(properties);
		Optional<IndexerConfigurationProvider> indexerConfigurationProvider = pluginManager.loadPrefered(IndexerConfigurationProvider.class);
		indexerConfigurationProvider.ifPresent(icp -> icp.setDefaultProvider(defaultConfigProvider));
		return indexerConfigurationProvider.orElse(defaultConfigProvider);
	}

	/**
	 * Customization for ObjectMapper that's used for rest requests.
	 * 
	 * @return
	 */
	@Bean
	public Module paramNamesModule() {
		return new ParameterNamesModule(Mode.PROPERTIES);
	}

	/**
	 * Customizations for ObjectMapper that's used for rest requests.
	 * 
	 * @return
	 */
	@Bean
	public Module mixinModule() {
		SimpleModule module = new SimpleModule();

		module.setMixInAnnotation(Attribute.class, SingleStringArgsCreator.class);
		module.setMixInAnnotation(Facet.class, FacetMixin.class);
		module.setMixInAnnotation(FacetEntry.class, WithTypeInfo.class);

		module.registerSubtypes(HierarchialFacetEntry.class, Product.class);

		module.addDeserializer(Document.class, new DocumentDeserializer());
		module.addDeserializer(Product.class, new ProductDeserializer());

		return module;
	}

	@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "_type")
	public static abstract class WithTypeInfo {}

	public static abstract class SingleStringArgsCreator {

		@JsonCreator
		SingleStringArgsCreator(String label) {}
	}

	public static abstract class FacetMixin {

		@JsonCreator
		FacetMixin(String name) {}

		@JsonIgnore
		abstract String getLabel();

		@JsonIgnore
		abstract String getType();
	}
}
