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
	public SuggestConfig getConfig(@NonNull String indexName, SuggestConfig defaultSuggestConfig) {
		// the very default is the no-args constructed SuggestConfig
		SuggestConfig foundConfig = defaultSuggestConfig != null ? defaultSuggestConfig : new SuggestConfig();
		for (SuggestConfigProvider configProvider : configProviders) {
			// provide the config of the previous config providers as default, so they can be merged
			SuggestConfig providedConfig = configProvider.getConfig(indexName, foundConfig);
			if (providedConfig != null) {
				foundConfig = providedConfig;
			}
		}
		return foundConfig;
	}

}
