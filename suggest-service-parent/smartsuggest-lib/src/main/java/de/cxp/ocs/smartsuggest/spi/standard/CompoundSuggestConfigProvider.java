package de.cxp.ocs.smartsuggest.spi.standard;

import java.util.ArrayList;
import java.util.List;

import de.cxp.ocs.smartsuggest.spi.SuggestConfig;
import de.cxp.ocs.smartsuggest.spi.SuggestConfigProvider;
import lombok.NonNull;

public class CompoundSuggestConfigProvider implements SuggestConfigProvider {

	private final List<SuggestConfigProvider> configProviders;

	public CompoundSuggestConfigProvider(List<SuggestConfigProvider> configProviders) {
		this.configProviders = new ArrayList<>(configProviders);
	}

	@Override
	public SuggestConfig get(@NonNull String indexName) {
		SuggestConfig foundConfig = null;
		for (SuggestConfigProvider configProvider : configProviders) {
			foundConfig = configProvider.get(indexName);
			if (foundConfig != null) break;
		}
		return foundConfig;
	}

}
