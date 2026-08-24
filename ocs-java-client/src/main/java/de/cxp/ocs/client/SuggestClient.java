package de.cxp.ocs.client;

import java.util.List;
import java.util.function.Consumer;

import de.cxp.ocs.api.SuggestService;
import de.cxp.ocs.model.suggest.Suggestion;
import feign.Feign;
import feign.Feign.Builder;
import feign.jackson.JacksonDecoder;

public class SuggestClient implements SuggestService {

	private final SuggestApi target;

	/**
	 * With this constructor the Feign::Builder can be configured.
	 * 
	 * @param endpointUrl
	 * @param feignConfigurer
	 */
	public SuggestClient(String endpointUrl, Consumer<Feign.Builder> feignConfigurer) {
		Builder fb = Feign.builder();
		feignConfigurer.accept(fb);
		target = fb.target(SuggestApi.class, endpointUrl);
	}

	/**
	 * Initializes the SearchClient with the given endpointUrl and
	 * recommended settings.
	 * 
	 * @param endpointUrl
	 */
	public SuggestClient(String endpointUrl) {
		this(endpointUrl, f -> {
			f.decoder(new JacksonDecoder());
		});
	}

	@Override
	public List<Suggestion> suggest(String index, String userQuery, Integer limit, String filter) throws Exception {
		return target.suggest(index, userQuery, limit, filter);
	}

}
