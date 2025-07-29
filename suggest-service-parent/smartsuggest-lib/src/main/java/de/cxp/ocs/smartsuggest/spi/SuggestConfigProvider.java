package de.cxp.ocs.smartsuggest.spi;

import lombok.NonNull;

public interface SuggestConfigProvider {

	/**
	 * Retrieve config for a given index. In case only some index specific values should be set, the default suggest
	 * config can be used since it may contain global settings (if not null).
	 * 
	 * @param indexName name of the index that should be configured
	 * @param defaultSuggestConfig
	 *        copy of the suggest config that was set as default for the whole service.
	 *        It can be modified and returned or a different config object can be returned.
	 *        Returning null is considered equivalent to returning that default config.
	 * @return suggest configuration
	 */
	SuggestConfig getConfig(@NonNull String indexName, SuggestConfig defaultSuggestConfig);

	/**
	 * Return priority when this config provider should be asked for the configuration. Providers with a low priority
	 * value (e.g. 1) will be asked first and providers with a higher value will be called later and can overwrite
	 * previously set values.
	 * 
	 * @return priority value, defaults to 100
	 */
	default int getPriority() {
		return 100;
	}
}
