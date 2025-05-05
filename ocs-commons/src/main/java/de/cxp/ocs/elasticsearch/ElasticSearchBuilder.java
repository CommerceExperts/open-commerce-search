package de.cxp.ocs.elasticsearch;

import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;

public class ElasticSearchBuilder implements AutoCloseable {

	private       RestHighLevelClient highLevelClient;
	private final RestClientBuilder   restClientBuilder;

	public ElasticSearchBuilder(RestClientBuilder clientBuilder) {
		restClientBuilder = clientBuilder;
	}

	public RestHighLevelClient getRestHLClient() {
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
