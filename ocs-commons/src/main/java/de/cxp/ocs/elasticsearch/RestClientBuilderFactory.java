package de.cxp.ocs.elasticsearch;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

import de.cxp.ocs.config.ConnectionConfiguration;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class RestClientBuilderFactory {

	private RestClientBuilderFactory() {}

	public static RestClientBuilder createRestClientBuilder(@NonNull final ConnectionConfiguration connectionConf) {
		List<HttpHost> hostsList = new ArrayList<>();
		for (String hostString : connectionConf.getHosts().split(",")) {
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
		RestClientBuilder restClientBuilder = RestClient.builder(hostsList.toArray(new HttpHost[0]));
		if (connectionConf.getAuth() != null && !connectionConf.getAuth().isEmpty()) {
			log.info("enabled authentication for elasticsearch client");
			byte[] authEncoded = Base64.getEncoder().encode(connectionConf.getAuth().getBytes());
			restClientBuilder.setDefaultHeaders(new Header[] { new BasicHeader("Authorization", "Basic " + new String(authEncoded)) });
		}
		else {
			log.info("no authentication for elasticsearch client enabled");
		}
		return restClientBuilder;
	}
}
