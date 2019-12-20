package de.cxp.ocs.elasticsearch;

import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;

import fr.pilato.elasticsearch.tools.template.TemplateElasticsearchUpdater;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ElasticSearchBuilder implements AutoCloseable {

	private static String[] TEMPLATES = { "german_structured_search", "structured_search" };

	private RestHighLevelClient	highLevelClient;
	private RestClientBuilder	restClientBuilder;

	private boolean initialized = false;

	public ElasticSearchBuilder(RestClientBuilder clientBuilder) {
		restClientBuilder = clientBuilder;
	}

	public synchronized void initializeTemplates() throws Exception {
		if (!initialized) {
			// TODO: support custom replica + shard settings
			// TODO: avoid duplicate analyzer / field setup definition by
			// merging/generating the templates from single parts
			for (String template : TEMPLATES) {
				TemplateElasticsearchUpdater.createTemplate(getRestHLClient().getLowLevelClient(), template, true);
			}
			initialized = true;
		}
	}


	private RestHighLevelClient getRestHLClient() {
		if (highLevelClient == null) {
			synchronized (this) {
				if (highLevelClient == null) {
					highLevelClient = new RestHighLevelClient(restClientBuilder);
				}
			}
		}
		return highLevelClient;
	}

	@Override
	public void close() throws Exception {
		synchronized (this) {
			if (highLevelClient != null) {
				highLevelClient.close();
				highLevelClient = null;
			}
		}
	}

}
