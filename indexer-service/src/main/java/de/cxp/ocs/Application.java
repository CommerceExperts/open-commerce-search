package de.cxp.ocs;

import java.util.Map.Entry;

import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import de.cxp.ocs.conf.ApplicationProperties;
import de.cxp.ocs.conf.IndexConfiguration;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.elasticsearch.RestClientBuilderFactory;
import de.cxp.ocs.model.index.Attribute;
import de.cxp.ocs.model.index.Product;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.FacetEntry;
import de.cxp.ocs.model.result.HierarchialFacetEntry;
import fr.pilato.elasticsearch.tools.ElasticsearchBeyonder;
import fr.pilato.elasticsearch.tools.SettingsFinder.Defaults;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.spring.autoconfigure.MeterRegistryCustomizer;

@SpringBootApplication
@RefreshScope
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	public RestHighLevelClient getElasticsearchClient(ApplicationProperties properties) throws Exception {
		fixFieldConfiguration(properties.getDefaultIndexConfig());
		for (IndexConfiguration indexerProps : properties.getIndexConfig().values()) {
			fixFieldConfiguration(indexerProps);
		}

		RestClientBuilder restClientBuilder = RestClientBuilderFactory.createRestClientBuilder(properties.getConnectionConfiguration());
		RestHighLevelClient highLevelClient = new RestHighLevelClient(restClientBuilder);
		ElasticsearchBeyonder.start(highLevelClient.getLowLevelClient(), Defaults.ConfigDir, Defaults.MergeMappings, true);
		return highLevelClient;
	}

	private void fixFieldConfiguration(IndexConfiguration indexerProperties) {
		for (Entry<String, Field> field : indexerProperties.getFieldConfiguration().getFields().entrySet()) {
			if (field.getValue().getName() == null) {
				field.getValue().setName(field.getKey());
			}
		}
	}

	@Bean
	public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags(
			@Value("${spring.application.name}") String applicationName) {
		return registry -> {
			registry.config().commonTags("application", applicationName);
		};
	}

	@Bean
	public Module parameterNamesModule() {
		return new ParameterNamesModule(Mode.PROPERTIES);
	}

	@Bean
	public Module mixinModule() {
		SimpleModule module = new SimpleModule();

		module.setMixInAnnotation(Attribute.class, SingleStringArgsCreator.class);
		module.setMixInAnnotation(Facet.class, FacetMixin.class);
		module.setMixInAnnotation(FacetEntry.class, WithTypeInfo.class);

		module.registerSubtypes(HierarchialFacetEntry.class, Product.class);

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
