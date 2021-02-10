package de.cxp.ocs.client;

import java.util.List;
import java.util.function.Consumer;

import de.cxp.ocs.api.SuggestService;
import de.cxp.ocs.model.suggest.Suggestion;
import feign.Feign;
import feign.Feign.Builder;

public class SuggestClient implements SuggestService {

	private SuggestApi target;

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
	 * feign defaults
	 * 
	 * @param endpointUrl
	 */
	public SuggestClient(String endpointUrl) {
		this(endpointUrl, f -> {});
	}

	@Override
	public List<Suggestion> suggest(String index, String userQuery, Integer limit, String filter) throws Exception {
		return target.suggest(index, userQuery, limit, filter);
	}

}
