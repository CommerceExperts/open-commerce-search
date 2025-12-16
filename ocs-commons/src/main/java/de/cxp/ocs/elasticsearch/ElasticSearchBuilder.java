package de.cxp.ocs.elasticsearch;

import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.RestHighLevelClientBuilder;

public class ElasticSearchBuilder implements AutoCloseable {

	private       RestHighLevelClient highLevelClient;
	private final RestClient          restClient;
	private final boolean             useCompatibilityMode;

	public ElasticSearchBuilder(RestClient restClient, boolean useCompatibilityMode) {
		this.restClient = restClient;
		this.useCompatibilityMode = useCompatibilityMode;
	}

	public RestHighLevelClient getRestHLClient() {
		if (highLevelClient == null) {
			synchronized (this) {
				if (highLevelClient == null) {
					highLevelClient = new RestHighLevelClientBuilder(restClient)
							.setApiCompatibilityMode(useCompatibilityMode)
							.build();
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
