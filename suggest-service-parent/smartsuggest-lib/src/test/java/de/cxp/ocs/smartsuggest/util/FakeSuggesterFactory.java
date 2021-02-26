package de.cxp.ocs.smartsuggest.util;

import java.util.Optional;

import de.cxp.ocs.smartsuggest.monitoring.MeterRegistryAdapter;
import de.cxp.ocs.smartsuggest.querysuggester.QuerySuggester;
import de.cxp.ocs.smartsuggest.querysuggester.SuggesterFactory;
import de.cxp.ocs.smartsuggest.spi.SuggestData;
import de.cxp.ocs.smartsuggest.spi.SuggestRecord;
import lombok.Setter;

public class FakeSuggesterFactory implements SuggesterFactory {

	@Override
	public QuerySuggester getSuggester(SuggestData suggestData) {
		return new FakeSuggester(suggestData.getSuggestRecords().toArray(new SuggestRecord[0]));
	}

	@Setter
	private Optional<MeterRegistryAdapter> metricsRegistryAdapter = Optional.empty();

}
