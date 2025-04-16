package de.cxp.ocs.smartsuggest.spi;

public interface SuggestDataProvider extends AbstractDataProvider<SuggestData> {

	default String getName() {
		return this.getClass().getSimpleName();
	}

}
