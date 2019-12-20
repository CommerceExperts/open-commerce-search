package de.cxp.ocs.elasticsearch;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

import lombok.NonNull;

public final class RestClientBuilderFactory {

	private RestClientBuilderFactory() {}

	public static RestClientBuilder createRestClientBuilder(@NonNull
	final String hostsString) {
		List<HttpHost> hostsList = new ArrayList<>();
		for (String hostString : hostsString.split(",")) {
			if (hostString != null && !hostString.isEmpty()) {
				if (hostString.contains("://")) {
					URI hostUri = URI.create(hostString);
					hostsList.add(new HttpHost(hostUri.getHost(), hostUri.getPort(), hostUri.getScheme()));
				}
				else if (hostString.matches(".*:\\d+")) {
					String[] hostParts = hostString.split(":", 2);
					hostsList.add(new HttpHost(hostParts[0], Integer.valueOf(hostParts[1])));
				}
				else {
					hostsList.add(new HttpHost(hostString));
				}

			}
		}
		return RestClient.builder(hostsList.toArray(new HttpHost[hostsList.size()]));
	}
}
