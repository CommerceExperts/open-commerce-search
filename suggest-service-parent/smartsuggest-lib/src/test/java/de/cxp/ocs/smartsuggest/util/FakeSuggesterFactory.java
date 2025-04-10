package de.cxp.ocs.smartsuggest.util;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import de.cxp.ocs.smartsuggest.monitoring.MeterRegistryAdapter;
import de.cxp.ocs.smartsuggest.querysuggester.SuggesterFactory;
import de.cxp.ocs.smartsuggest.spi.SuggestConfig;
import de.cxp.ocs.smartsuggest.spi.SuggestData;
import de.cxp.ocs.smartsuggest.spi.SuggestRecord;
import io.micrometer.core.instrument.Tag;

public class FakeSuggesterFactory implements SuggesterFactory<FakeSuggester> {

	@Override
	public FakeSuggester getSuggester(SuggestData suggestData, SuggestConfig config) {
		SuggestRecord[] suggestRecords;
		if (suggestData instanceof List) {
			suggestRecords = ((List<SuggestRecord>) suggestData.getSuggestRecords()).toArray(new SuggestRecord[0]);
		}
		else {
			suggestRecords = StreamSupport.stream(suggestData.getSuggestRecords().spliterator(), false).toArray(SuggestRecord[]::new);
		}
		return new FakeSuggester(suggestRecords);
	}

	@Override
	public Path persist(FakeSuggester querySuggester) {
		return null;
	}

	@Override
	public FakeSuggester recover(Path baseDir, SuggestConfig config) {
		return null;
	}

	@Override
	public void instrument(Optional<MeterRegistryAdapter> metricsRegistryAdapter, Iterable<Tag> tags) {
		// irrelevant for testing
	}

}
