package de.cxp.ocs.smartsuggest.spi.standard;

import de.cxp.ocs.smartsuggest.spi.SuggestConfig;
import de.cxp.ocs.smartsuggest.spi.SuggestConfigProvider;
import lombok.NonNull;

public class DefaultSuggestConfigProvider implements SuggestConfigProvider {

	private SuggestConfig defaultSuggestConfig = new SuggestConfig();

	public DefaultSuggestConfigProvider() {}

	public DefaultSuggestConfigProvider(@NonNull SuggestConfig defaultSuggestConfig) {
		this.defaultSuggestConfig = defaultSuggestConfig;
	}

	@Override
	public SuggestConfig getConfig(@NonNull String indexName, SuggestConfig indexConfig) {
		return indexConfig == null ? defaultSuggestConfig.clone() : indexConfig;
	}

	@Override
	public int getPriority() {
		return Integer.MAX_VALUE;
	}

}
