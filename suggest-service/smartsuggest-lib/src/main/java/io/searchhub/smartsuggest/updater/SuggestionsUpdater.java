package io.searchhub.smartsuggest.updater;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.store.AlreadyClosedException;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.searchhub.smartsuggest.monitoring.MeterRegistryAdapter;
import io.searchhub.smartsuggest.querysuggester.QuerySuggester;
import io.searchhub.smartsuggest.querysuggester.QuerySuggesterProxy;
import io.searchhub.smartsuggest.querysuggester.SuggesterFactory;
import io.searchhub.smartsuggest.spi.SuggestData;
import io.searchhub.smartsuggest.spi.SuggestDataProvider;
import io.searchhub.smartsuggest.spi.SuggestRecord;
import io.searchhub.smartsuggest.util.Util;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class SuggestionsUpdater implements Runnable {

	@NonNull
	private final SuggestDataProvider dataProvider;

	@NonNull
	private final String indexName;

	@NonNull
	private final QuerySuggesterProxy querySuggesterProxy;

	@NonNull
	private final SuggesterFactory factory;

	private Instant lastUpdate = null;

	private int	updateFailCount		= 0;
	private int	updateSuccessCount	= 0;
	private int	suggestionsCount	= -1;

	@Override
	public void run() {
		try {
			update();
			updateFailCount = 0;
		}
		catch (AlreadyClosedException ace) {
			log.info("Stopping updates for closed suggester {}", indexName);
			throw ace;
		}
		catch (IllegalStateException unrecoverableEx) {
			log.error("Stopping background suggestions updates for index {} due to {}:{}",
					indexName, unrecoverableEx.getClass().getSimpleName(), unrecoverableEx.getMessage());
			throw unrecoverableEx;
		}
		catch (Exception e) {
			updateFailCount++;
			log.warn("update failed for index {}: {}",
					indexName, e.getClass().getSimpleName() + " : " + e.getMessage());
			log.debug("", e);
			if (updateFailCount > 5) {
				log.error("More than 5 update failures! Stopping background suggestions updates for index {}", indexName);
				throw new RuntimeException(e);
			}
		}
	}

	private void update() throws IOException {
		Instant modTime = Instant.ofEpochMilli(dataProvider.getLastDataModTime(indexName));
		if (modTime == null) {
			throw new IllegalStateException("no data available for index " + indexName);
		}
		if (lastUpdate == null || modTime.isAfter(lastUpdate)) {
			SuggestData suggestData = dataProvider.loadData(indexName);

			if (suggestData == null) {
				log.error("Received NULL suggest data from query api service. Unable to update query suggester for index "
						+ indexName);
			}
			else {
				List<SuggestRecord> suggestRecords = suggestData.getSuggestRecords();
				final int count = suggestRecords.size();
				QuerySuggester querySuggester = factory.getSuggester(suggestData);
				try {
					querySuggesterProxy.updateQueryMapper(querySuggester);
				}
				catch (AlreadyClosedException ace) {
					log.info("Suggester Update for index {} canceled, because suggester closed", indexName);
					querySuggester.destroy();
					throw ace;
				}

				log.info("Received suggest data for index {} with {} suggestions", indexName, count);
				lastUpdate = modTime;
				updateSuccessCount++;
				suggestionsCount = count;
			}
		}
		else {
			log.trace("No changes for index {}. last update = {}, remote data mod.time = {}",
					indexName, lastUpdate, modTime);
		}
	}

	private void addSensors(MeterRegistry reg) {
		Iterable<Tag> indexTag = Tags.of("indexName", indexName);
		reg.gauge(Util.APP_NAME + ".update.fail.count", indexTag, this, updater -> updater.updateFailCount);
		reg.more().counter(Util.APP_NAME + ".update.success.count", indexTag, this, updater -> updater.updateSuccessCount);
		reg.more().timeGauge(Util.APP_NAME + ".suggestions.age", indexTag, this, TimeUnit.SECONDS,
				updater -> (System.currentTimeMillis() - updater.lastUpdate.toEpochMilli()) / 1000);
		reg.gauge(Util.APP_NAME + ".suggestions.size", indexTag, this, updater -> updater.suggestionsCount);
	}

	public void setMetricsRegistryAdapter(Optional<MeterRegistryAdapter> metricsRegistryAdapter) {
		metricsRegistryAdapter.ifPresent(adapter -> this.addSensors(adapter.getMetricsRegistry()));
	}

}
