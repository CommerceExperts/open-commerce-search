package de.cxp.ocs.smartsuggest.spi;

import lombok.NonNull;

public interface SuggestConfigProvider {

	/**
	 * Retrieve config for a given index. In case only some index specific values should be set, the default suggest
	 * config can be used since it may contain global settings (if not null).
	 * 
	 * @param indexName
	 * @param defaultSuggestConfig
	 *        nullable suggest config that was set as default for the whole service. Can be used to merge global and
	 *        index-specific config values.
	 * @return
	 */
	SuggestConfig getConfig(@NonNull String indexName, SuggestConfig defaultSuggestConfig);

	default int getPriority() {
		return 100;
	}
}
