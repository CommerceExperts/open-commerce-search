package de.cxp.ocs.smartsuggest.querysuggester;

import java.util.Optional;

import de.cxp.ocs.smartsuggest.monitoring.MeterRegistryAdapter;
import de.cxp.ocs.smartsuggest.spi.SuggestData;

public interface SuggesterFactory {

	/**
	 * Method to receive an optional MeterRegistryAdapter.
	 * 
	 * @param metricsRegistry
	 */
	void setMetricsRegistry(Optional<MeterRegistryAdapter> metricsRegistry);

	QuerySuggester getSuggester(SuggestData suggestData);

}
