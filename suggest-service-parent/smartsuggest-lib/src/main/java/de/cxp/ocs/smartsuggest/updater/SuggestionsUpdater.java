package de.cxp.ocs.smartsuggest.updater;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.store.AlreadyClosedException;

import de.cxp.ocs.smartsuggest.monitoring.Instrumentable;
import de.cxp.ocs.smartsuggest.monitoring.MeterRegistryAdapter;
import de.cxp.ocs.smartsuggest.querysuggester.QuerySuggester;
import de.cxp.ocs.smartsuggest.querysuggester.QuerySuggesterProxy;
import de.cxp.ocs.smartsuggest.querysuggester.SuggesterFactory;
import de.cxp.ocs.smartsuggest.spi.SuggestData;
import de.cxp.ocs.smartsuggest.spi.SuggestDataProvider;
import de.cxp.ocs.smartsuggest.spi.SuggestRecord;
import de.cxp.ocs.smartsuggest.util.Util;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class SuggestionsUpdater implements Runnable, Instrumentable {

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
		catch (Throwable e) {
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
		if (lastUpdate == null && !dataProvider.hasData(indexName)) {
			throw new IllegalStateException("dataprovider " + dataProvider.getClass().getSimpleName()
					+ " has no data for index " + indexName);
		}

		long remoteModTimeMs = dataProvider.getLastDataModTime(indexName);
		if (remoteModTimeMs < 0) {
			throw new IllegalStateException("dataprovider " + dataProvider.getClass().getSimpleName()
					+ " states to have data for index " + indexName
					+ " but lastModTime was " + remoteModTimeMs);
		}

		Instant remoteModTime = Instant.ofEpochMilli(remoteModTimeMs);
		if (lastUpdate == null || remoteModTime.isAfter(lastUpdate)) {
			SuggestData suggestData = dataProvider.loadData(indexName);

			if (suggestData == null) {
				log.error("Received NULL suggest data from query api service. Unable to update query suggester for index " + indexName);
				return;
			}

			long dataModTimestamp = suggestData.getModificationTime();
			if (dataModTimestamp > 0L) {
				Instant dataModTime = Instant.ofEpochMilli(dataModTimestamp);
				if (!remoteModTime.equals(dataModTime)) {
					log.warn("Received data for index {} with the wrong modTime '{}' - expected modTime {}! Will try again with the next update.",
							indexName, dataModTime, remoteModTime);
					return;
				}
			}

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
			lastUpdate = remoteModTime;
			updateSuccessCount++;
			suggestionsCount = count;
		}
		else {
			log.trace("No changes for index {}. last update = {}, remote data mod.time = {}",
					indexName, lastUpdate, remoteModTime);
		}
	}

	@Override
	public void setMetricsRegistryAdapter(Optional<MeterRegistryAdapter> metricsRegistryAdapter) {
		metricsRegistryAdapter.ifPresent(adapter -> this.addSensors(adapter.getMetricsRegistry()));
	}

	private void addSensors(MeterRegistry reg) {
		Iterable<Tag> indexTag = Tags
				.of("indexName", indexName)
				.and("dataProvider", dataProvider.getClass().getCanonicalName());
		reg.gauge(Util.APP_NAME + ".update.fail.count", indexTag, this, updater -> updater.updateFailCount);
		reg.more().counter(Util.APP_NAME + ".update.success.count", indexTag, this, updater -> updater.updateSuccessCount);
		reg.more().timeGauge(Util.APP_NAME + ".suggestions.age", indexTag, this, TimeUnit.SECONDS,
				updater -> (updater.lastUpdate == null ? -1 : System.currentTimeMillis() - updater.lastUpdate.toEpochMilli()) / 1000);
		reg.gauge(Util.APP_NAME + ".suggestions.size", indexTag, this, updater -> updater.suggestionsCount);
	}

}
