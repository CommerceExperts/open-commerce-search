package de.cxp.ocs.elasticsearch;

import java.time.Duration;

import org.elasticsearch.Version;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import de.cxp.ocs.config.ConnectionConfiguration;
import lombok.extern.slf4j.Slf4j;

@SuppressWarnings("deprecation")
@Slf4j
public class ElasticsearchContainerUtil {

	public static final int ES_PORT = 9200;

	public static ElasticsearchContainer spinUpEs() {
		log.info("starting Elasticsearch container..");
		ElasticsearchContainer container = new ElasticsearchContainer(
				DockerImageName
						.parse("docker.elastic.co/elasticsearch/elasticsearch")
						.withTag(Version.CURRENT.toString()));
		container.addEnv("discovery.type", "single-node");
		container.addEnv("ES_JAVA_OPTS", "-Xms1024m -Xmx1024m");
		container.setWaitStrategy(new HttpWaitStrategy().forPort(ES_PORT));
		container.withStartupTimeout(Duration.ofSeconds(60));
		container.start();
		log.info("started Elasticsearch container '{}' on port {}", container.getContainerName(), container.getMappedPort(ES_PORT));
		return container;
	}

	public static RestHighLevelClient initClient(ElasticsearchContainer container) {
		ConnectionConfiguration connectionConf = new ConnectionConfiguration();
		connectionConf.setHosts("localhost:" + container.getMappedPort(ES_PORT));
		RestClientBuilder restClientBuilder = RestClientBuilderFactory.createRestClientBuilder(connectionConf);
		return new RestHighLevelClient(restClientBuilder);
	}

}
