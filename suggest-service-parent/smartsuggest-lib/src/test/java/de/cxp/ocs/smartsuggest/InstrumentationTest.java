package de.cxp.ocs.smartsuggest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import de.cxp.ocs.smartsuggest.monitoring.MeterRegistryAdapter;
import de.cxp.ocs.smartsuggest.spi.SuggestRecord;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public class InstrumentationTest {

	private String testIndex = "instrumentation_test";

	private RemoteSuggestDataProviderSimulation	serviceMock	= new RemoteSuggestDataProviderSimulation();
	private QuerySuggestManager					querySuggestManager;

	@Test
	public void testInstrumentationOfReinitializedSuggester() throws Exception {
		SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

		querySuggestManager = QuerySuggestManager.builder()
				.withSuggestDataProvider(serviceMock)
				.addMetricsRegistryAdapter(MeterRegistryAdapter.of(meterRegistry))
				.setMinimalUpdateRate()
				.build();

		serviceMock.setRequestLatencyInMs(0);
		serviceMock.updateSuggestions(testIndex, Arrays.asList(
				SuggestRecord.builder().primaryText("apples").build(),
				SuggestRecord.builder().primaryText("oranges").build()));

		assertEquals(2, querySuggestManager.getQuerySuggester(testIndex, true).recordCount());
		// assert that gauge has the same value
		Gauge recordCountGauge = meterRegistry.get("smartsuggest.lucene_suggester.record_count").gauge();
		assertEquals(2d, recordCountGauge.measure().iterator().next().getValue());

		// now destroy and make sure the meters are destroyed as well
		querySuggestManager.destroyQuerySuggester(testIndex);
		assertEquals(Double.NaN, recordCountGauge.measure().iterator().next().getValue());
		assertThrows(MeterNotFoundException.class, () -> meterRegistry.get("smartsuggest.lucene_suggester.record_count").gauge());

		// sanity check: even if the service has new data, we still expect no meter
		serviceMock.updateSuggestions(testIndex, Arrays.asList(
				SuggestRecord.builder().primaryText("apples").build(),
				SuggestRecord.builder().primaryText("banana").build(),
				SuggestRecord.builder().primaryText("oranges").build()));
		assertEquals(Double.NaN, recordCountGauge.measure().iterator().next().getValue());
		assertThrows(MeterNotFoundException.class, () -> meterRegistry.get("smartsuggest.lucene_suggester.record_count").gauge());

		// reinitialize suggester
		assertEquals(3, querySuggestManager.getQuerySuggester(testIndex, true).recordCount());
		// old gauge instance should still not work
		assertEquals(Double.NaN, recordCountGauge.measure().iterator().next().getValue());
		// ..instead a new gauge is created
		recordCountGauge = meterRegistry.get("smartsuggest.lucene_suggester.record_count").gauge();
		assertNotNull(recordCountGauge);
		assertEquals(3d, recordCountGauge.measure().iterator().next().getValue());
	}
}
