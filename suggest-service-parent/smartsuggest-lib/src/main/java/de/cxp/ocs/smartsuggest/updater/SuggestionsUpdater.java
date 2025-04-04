package de.cxp.ocs.smartsuggest.updater;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.store.AlreadyClosedException;

import de.cxp.ocs.smartsuggest.monitoring.Instrumentable;
import de.cxp.ocs.smartsuggest.monitoring.MeterRegistryAdapter;
import de.cxp.ocs.smartsuggest.querysuggester.QuerySuggester;
import de.cxp.ocs.smartsuggest.querysuggester.QuerySuggesterProxy;
import de.cxp.ocs.smartsuggest.querysuggester.SuggesterFactory;
import de.cxp.ocs.smartsuggest.spi.SuggestConfig;
import de.cxp.ocs.smartsuggest.spi.SuggestConfigProvider;
import de.cxp.ocs.smartsuggest.spi.SuggestData;
import de.cxp.ocs.smartsuggest.spi.SuggestDataProvider;
import de.cxp.ocs.smartsuggest.util.Util;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class SuggestionsUpdater implements Runnable, Instrumentable {

	@NonNull
	private final SuggestDataProvider dataProvider;

	@NonNull
	private final SuggestConfigProvider configProvider;

	private final SuggestConfig defaultSuggestConfig;

	@NonNull
	private final String indexName;

	@NonNull
	private final QuerySuggesterProxy querySuggesterProxy;

	@NonNull
	private final SuggesterFactory factory;

	private Instant lastUpdate = null;

	private int		updateFailCount		= 0;
	private int		updateSuccessCount	= 0;
	private long	suggestionsCount	= -1;

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
		catch (InterruptedException interrupt) {
			log.info("Updates for index {} interrupted. Stopping Updater now.", indexName);
			throw new RuntimeException(interrupt);
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

	private void update() throws Exception {
		Instant remoteModTime = getRemoteDataModTime();
		if (lastUpdate == null || remoteModTime.isAfter(lastUpdate)) {
			SuggestData suggestData = fetchSuggestData(remoteModTime);
			if (suggestData == null) return;

			SuggestConfig suggestConfig = configProvider.getConfig(indexName, defaultSuggestConfig);
			long startIndexation = System.currentTimeMillis();
			QuerySuggester querySuggester = factory.getSuggester(suggestData, suggestConfig);
			final long count = querySuggester.recordCount();
			log.info("Indexed {} suggest records for index {} in {}ms", count, indexName, System.currentTimeMillis() - startIndexation);

			try {
				querySuggesterProxy.updateQueryMapper(querySuggester);
			}
			catch (AlreadyClosedException ace) {
				log.info("Suggester Update for index {} canceled, because suggester closed", indexName);
				querySuggester.destroy();
				throw ace;
			}

			lastUpdate = remoteModTime;
			updateSuccessCount++;
			suggestionsCount = count;
		}
		else {
			log.trace("No changes for index {}. last update = {}, remote data mod.time = {}",
					indexName, lastUpdate, remoteModTime);
		}
	}

	private Instant getRemoteDataModTime() throws Exception {
		if (lastUpdate == null && !dataProvider.hasData(indexName)) {
			throw new IllegalStateException("dataprovider " + dataProvider.getClass().getSimpleName()
					+ " has no data for index " + indexName);
		}

		long remoteModTimeMs = dataProvider.getLastDataModTime(indexName);
		if (remoteModTimeMs < 0) {
			if (lastUpdate != null) {
				throw new Exception("dataprovider " + dataProvider.getClass().getSimpleName() + " seems unavailable at the moment");
			} else {
				throw new IllegalStateException("dataprovider " + dataProvider.getClass().getSimpleName()
						+ " states to have data for index " + indexName
						+ " but lastModTime was " + remoteModTimeMs);
			}
		}

		return Instant.ofEpochMilli(remoteModTimeMs);
	}

	private SuggestData fetchSuggestData(Instant remoteModTime) throws IOException {
		log.info("Fetching data for index {}", indexName);
		SuggestData suggestData = dataProvider.loadData(indexName);

		if (suggestData == null) {
			log.error("Received NULL suggest data from query api service. Unable to update query suggester for index {}", indexName);
			return null;
		}

		long dataModTimestamp = suggestData.getModificationTime();
		if (dataModTimestamp > 0L) {
			Instant dataModTime = Instant.ofEpochMilli(dataModTimestamp);
			if (!remoteModTime.equals(dataModTime)) {
				log.warn("Received data for index {} with the wrong modTime '{}' - expected modTime {}! Will try again with the next update.",
						indexName, dataModTime, remoteModTime);
				return null;
			}
		}

		log.info("Received data for index {} with {} records", indexName,
				suggestData.getSuggestRecords() instanceof Collection ? ((Collection<?>) suggestData.getSuggestRecords()).size() : "?");
		return suggestData;
	}

	@Override
	public void instrument(Optional<MeterRegistryAdapter> metricsRegistryAdapter, Iterable<Tag> tags) {
		metricsRegistryAdapter.ifPresent(adapter -> this.addSensors(adapter.getMetricsRegistry(), tags));
	}

	private void addSensors(MeterRegistry reg, Iterable<Tag> tags) {
		reg.gauge(Util.APP_NAME + ".update.fail.count", tags, this, updater -> updater.updateFailCount);
		reg.more().counter(Util.APP_NAME + ".update.success.count", tags, this, updater -> updater.updateSuccessCount);
		reg.more().timeGauge(Util.APP_NAME + ".suggestions.age", tags, this, TimeUnit.SECONDS,
				updater -> (double) (updater.lastUpdate == null ? -1 : System.currentTimeMillis() - updater.lastUpdate.toEpochMilli()) / 1000);
		reg.gauge(Util.APP_NAME + ".suggestions.size", tags, this, updater -> updater.suggestionsCount);
	}

}
