package de.cxp.ocs;

import java.util.Map.Entry;

import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;

import de.cxp.ocs.conf.ApplicationProperties;
import de.cxp.ocs.conf.IndexConfiguration;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.elasticsearch.RestClientBuilderFactory;
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

		RestClientBuilder restClientBuilder = RestClientBuilderFactory
				.createRestClientBuilder(properties.getConnectionConfiguration().getHosts());
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
}
