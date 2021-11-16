package de.cxp.ocs.smartsuggest.spi;

import lombok.NonNull;

public interface SuggestConfigProvider {

	SuggestConfig getConfig(@NonNull String indexName);

	default int getPriority() {
		return 100;
	}
}
