package de.cxp.ocs.client;

import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

import de.cxp.ocs.api.searcher.SearchService;
import de.cxp.ocs.client.deserializer.ObjectMapperFactory;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.params.ArrangedSearchQuery;
import de.cxp.ocs.model.params.SearchQuery;
import de.cxp.ocs.model.result.SearchResult;
import feign.Feign;
import feign.Feign.Builder;
import feign.codec.Decoder;
import feign.httpclient.ApacheHttpClient;

public class SearchClient implements SearchService {

	private final SearchApi target;

	/**
	 * With this constructor the Feign::Builder can be configured.
	 * 
	 * @param endpointUrl
	 * @param feignConfigurer
	 */
	public SearchClient(String endpointUrl, Consumer<Feign.Builder> feignConfigurer) {
		Builder fb = Feign.builder();
		feignConfigurer.accept(fb);
		fb.client(new ApacheHttpClient());
		target = fb.target(SearchApi.class, endpointUrl);
	}

	/**
	 * Initializes the SearchClient with the given endpointUrl and the default
	 * Jackson encoder.
	 * If this constructor is not used, Jackson is not necessary on the
	 * classpath. Instead take care of a working {@link Decoder}.
	 * 
	 * @param endpointUrl
	 */
	public SearchClient(String endpointUrl) {
		this(endpointUrl, f -> {
			f.decoder(ObjectMapperFactory.createJacksonDecoder());
			f.encoder(ObjectMapperFactory.createJacksonEncoder());
			return;
		});
	}

	@Override
	public String[] getTenants() {
		return target.getTenants();
	}

	@Override
	public SearchResult search(String tenant, SearchQuery searchParams, Map<String, String> filters) throws Exception {
		return target.search(tenant,
				searchParams.q, searchParams.sort, searchParams.offset, searchParams.limit, searchParams.withFacets,
				filters == null ? Collections.emptyMap() : filters);
	}

	@Override
	public SearchResult arrangedSearch(String tenant, ArrangedSearchQuery searchQuery) throws Exception {
		return target.arrangedSearch(tenant, searchQuery);
	}

	@Override
	public Document getDocument(String tenant, String docId) throws Exception {
		return target.getDocument(tenant, docId);
	}
}
