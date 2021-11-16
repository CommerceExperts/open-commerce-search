package de.cxp.ocs.smartsuggest.spi.standard;

import de.cxp.ocs.smartsuggest.spi.SuggestConfig;
import de.cxp.ocs.smartsuggest.spi.SuggestConfigProvider;
import lombok.NonNull;

public class DefaultSuggestConfigProvider implements SuggestConfigProvider {

	private SuggestConfig defaultSuggestConfig = new SuggestConfig();

	public DefaultSuggestConfigProvider() {}

	public DefaultSuggestConfigProvider(SuggestConfig defaultSuggestConfig) {
		this.defaultSuggestConfig = defaultSuggestConfig;
	}

	@Override
	public SuggestConfig get(@NonNull String indexName) {
		return defaultSuggestConfig;
	}

}
