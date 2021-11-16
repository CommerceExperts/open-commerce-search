package de.cxp.ocs.smartsuggest.spi;

import lombok.NonNull;

public interface SuggestConfigProvider {

	SuggestConfig get(@NonNull String indexName);

}
