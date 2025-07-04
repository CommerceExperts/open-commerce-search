package de.cxp.ocs.smartsuggest;

import java.time.Instant;
import java.util.*;

import de.cxp.ocs.smartsuggest.spi.SuggestData;
import de.cxp.ocs.smartsuggest.spi.SuggestDataProvider;
import de.cxp.ocs.smartsuggest.spi.SuggestRecord;
import lombok.Getter;
import lombok.Setter;

public class RemoteSuggestDataProviderSimulation implements SuggestDataProvider {

	private Map<String, SuggestData>	loadedSuggestions		= new HashMap<>();
	private Map<String, Long>			loadedSuggestionTimes	= new HashMap<>();

	@Setter
	private boolean availability = true;

	@Setter
	private long requestLatencyInMs = 20;

	@Setter
	private long timeoutMs = 8 * 1000;

	private Random latencyDeviation = new Random();

	@Setter
	@Getter // overwrite SuggestDataProvider::getName
	private String name = UUID.randomUUID().toString();

	void updateSuggestions(String indexName, List<SuggestRecord> suggestions) {
		if (!availability) {
			throw new IllegalStateException("Service is not available at the moment");
		}

		long timestamp = Instant.now().toEpochMilli();
		loadedSuggestions.put(indexName, SuggestData.builder().suggestRecords(suggestions).modificationTime(timestamp).build());
		loadedSuggestionTimes.put(indexName, timestamp);
	}

	@Override
	public boolean hasData(String indexName) {
		return loadedSuggestionTimes.containsKey(indexName);
	}

	@Override
	public long getLastDataModTime(String indexName) {
		simulateConnection();
		return hasData(indexName) ? loadedSuggestionTimes.get(indexName) : -1;
	}

	@Override
	public SuggestData loadData(String indexName) {
		simulateConnection();
		return loadedSuggestions.get(indexName);
	}

	private void simulateConnection() {
		if (requestLatencyInMs > 0) {
			try {
				long latency = requestLatencyInMs / 2 + latencyDeviation.nextInt((int) requestLatencyInMs);
				Thread.sleep(Math.min(latency, timeoutMs));
			}
			catch (InterruptedException e) {}
		}

		if (!availability || requestLatencyInMs > timeoutMs) {
			throw new RuntimeException("Service is not available at the moment");
		}
	}

}
