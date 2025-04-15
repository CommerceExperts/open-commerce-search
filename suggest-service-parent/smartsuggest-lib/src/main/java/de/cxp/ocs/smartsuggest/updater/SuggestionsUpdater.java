package de.cxp.ocs.smartsuggest.updater;

import de.cxp.ocs.smartsuggest.monitoring.Instrumentable;
import de.cxp.ocs.smartsuggest.monitoring.MeterRegistryAdapter;
import de.cxp.ocs.smartsuggest.querysuggester.QuerySuggester;
import de.cxp.ocs.smartsuggest.querysuggester.QuerySuggesterProxy;
import de.cxp.ocs.smartsuggest.querysuggester.SuggesterFactory;
import de.cxp.ocs.smartsuggest.spi.*;
import de.cxp.ocs.smartsuggest.util.FileUtils;
import de.cxp.ocs.smartsuggest.util.Util;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.store.AlreadyClosedException;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class SuggestionsUpdater implements Runnable, Instrumentable {

	private final SuggestDataProvider  dataSourceProvider;
	private final IndexArchiveProvider archiveProvider;

	@NonNull
	private final SuggestConfigProvider configProvider;

	private final SuggestConfig defaultSuggestConfig;

	@NonNull
	private final String indexName;

	@NonNull
	private final QuerySuggesterProxy querySuggesterProxy;

	@NonNull
	private final SuggesterFactory<?> factory;

	private Instant lastUpdate = null;

	private int  updateFailCount    = 0;
	private int  updateSuccessCount = 0;
	private long suggestionsCount   = -1;

	public static SuggestionsUpdaterBuilder builder() {
		return new SuggestionsUpdaterBuilder();
	}

	@Setter
	@Accessors(fluent = true)
	public static class SuggestionsUpdaterBuilder {

		private SuggestDataProvider   dataSourceProvider;
		private IndexArchiveProvider  archiveProvider;
		private SuggestConfigProvider configProvider;
		private SuggestConfig         defaultSuggestConfig;
		private String                indexName;
		private QuerySuggesterProxy   querySuggesterProxy;
		private SuggesterFactory<?>   factory;

		SuggestionsUpdaterBuilder() {
		}

		public SuggestionsUpdater build() {
			// actually this is an internal used class, but we don't define module restrictions/exports yet
			Objects.requireNonNull(indexName, "indexName required");
			Objects.requireNonNull(configProvider, "configProvider required for index " + indexName);
			Objects.requireNonNull(querySuggesterProxy, "QuerySuggesterProxy required to setup Updater for index " + indexName);
			Objects.requireNonNull(factory, "SuggesterFactory required for index " + indexName);
			if (dataSourceProvider == null && archiveProvider == null) {
				throw new IllegalArgumentException("Either dataSourceProvider or archiverProvider must be given for index " + indexName);
			}
			return new SuggestionsUpdater(dataSourceProvider, archiveProvider, configProvider, defaultSuggestConfig, indexName, querySuggesterProxy, factory);
		}

	}

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
		Instant remoteSourceDataModTime = dataSourceProvider == null ? null : getRemoteDataModTime(dataSourceProvider);
		Instant remoteArchiveModTime = archiveProvider == null ? null : getRemoteDataModTime(archiveProvider);

		if (remoteSourceDataModTime == null && remoteArchiveModTime == null) {
			log.warn("no data available for index {} from dataprovider {}", indexName, getProviderNames());
		}
		else if (remoteSourceDataModTime == null) {
			updateFromArchiveProvider(remoteArchiveModTime);
		}
		else if (remoteArchiveModTime == null) {
			updateFromSourceDataProvider(remoteSourceDataModTime);
			if (archiveProvider != null) archiveSuggestIndex();
		}
		// both providers have data, decide which one to use
		else if (lastUpdate == null || remoteSourceDataModTime.isAfter(lastUpdate) || remoteArchiveModTime.isAfter(lastUpdate)) {
			// only do an update from remote data, if it's really newer than the archive. otherwise prefer archive as it's quicker to process
			if (remoteSourceDataModTime.isAfter(remoteArchiveModTime)) {
				if (updateFromSourceDataProvider(remoteSourceDataModTime)) archiveSuggestIndex();
			}
			else {
				updateFromArchiveProvider(remoteArchiveModTime);
			}
		}
		else {
			log.trace("No changes for index {}. last update = {}, remote source mod.time = {}, remote archive mod.time = {}",
					indexName, lastUpdate, remoteSourceDataModTime, remoteArchiveModTime);
		}
	}

	private void archiveSuggestIndex() throws IOException {
		IndexArchive archive = factory.createArchive(querySuggesterProxy.getInnerSuggester());
		archiveProvider.store(indexName, archive);
	}

	private String getProviderNames() {
		String providerName = null;
		if (dataSourceProvider != null) providerName = dataSourceProvider.getClass().getSimpleName();
		if (archiveProvider != null) {
			providerName = providerName == null ? "" : " and ";
			providerName += archiveProvider.getClass().getSimpleName();
		}
		return providerName;
	}

	private Instant getRemoteDataModTime(AbstractDataProvider<?> dataProvider) throws Exception {
		if (lastUpdate == null && !dataProvider.hasData(indexName)) {
			return null;
		}

		long remoteModTimeMs = dataProvider.getLastDataModTime(indexName);
		if (remoteModTimeMs < 0) {
			if (lastUpdate != null) {
				throw new Exception("dataprovider " + dataProvider.getClass().getSimpleName() + " seems unavailable at the moment");
			}
			else {
				throw new IllegalStateException("dataprovider " + dataProvider.getClass().getSimpleName()
						+ " states to have data for index " + indexName
						+ " but lastModTime was " + remoteModTimeMs);
			}
		}

		return Instant.ofEpochMilli(remoteModTimeMs);
	}

	private void updateFromArchiveProvider(Instant remoteArchiveModTime) throws Exception {
		if (lastUpdate == null || remoteArchiveModTime.isAfter(lastUpdate)) {
			IndexArchive loadedArchive = fetchSuggestData(archiveProvider, remoteArchiveModTime);
			if (!FileUtils.isTarGz(loadedArchive.zippedTarFile())) {
				throw new IllegalStateException("Not a tar.gz file: " + loadedArchive.zippedTarFile());
			}
			QuerySuggester querySuggester = factory.recover(loadedArchive, configProvider.getConfig(indexName, defaultSuggestConfig));
			finishUpdate(querySuggester, remoteArchiveModTime);
		}
	}

	private boolean updateFromSourceDataProvider(Instant remoteSuggestDataModTime) throws Exception {
		if (lastUpdate == null || remoteSuggestDataModTime.isAfter(lastUpdate)) {
			SuggestData suggestData = fetchSuggestData(dataSourceProvider, remoteSuggestDataModTime);
			log.info("Received source data for index {} with {} records", indexName,
					suggestData.getSuggestRecords() instanceof Collection ? ((Collection<?>) suggestData.getSuggestRecords()).size() : "?");

			SuggestConfig suggestConfig = configProvider.getConfig(indexName, defaultSuggestConfig);
			long startIndexation = System.currentTimeMillis();
			QuerySuggester querySuggester = factory.getSuggester(suggestData, suggestConfig);
			final long count = querySuggester.recordCount();
			log.info("Indexed {} suggest records for index {} in {}ms", count, indexName, System.currentTimeMillis() - startIndexation);

			finishUpdate(querySuggester, remoteSuggestDataModTime);
			return true;
		}
		// no new changes from remote - don't log this, as it would be logged every N seconds
		return false;
	}

	@NonNull
	private <T extends DatedData> T fetchSuggestData(AbstractDataProvider<T> dataProvider, Instant remoteModTime) throws IOException {
		log.info("Fetching data for index {}", indexName);
		T data = dataProvider.loadData(indexName);
		Objects.requireNonNull(data,
				"data for index " + indexName + " provided by " + dataProvider.getClass().getCanonicalName() + " is null. Unable to update query suggester.");

		long dataModTimestamp = data.getModificationTime();
		if (dataModTimestamp > 0L && remoteModTime.toEpochMilli() != dataModTimestamp) {
			throw new IllegalStateException(
					"Received data for index " + indexName + " by " + dataProvider.getClass().getCanonicalName() + " with the wrong modTime (" + data.getModificationTime() + ")"
							+ " - expected modTime " + remoteModTime + "!");
		}

		return data;
	}

	private void finishUpdate(QuerySuggester querySuggester, Instant remoteModTime) throws Exception {
		try {
			querySuggesterProxy.updateSuggester(querySuggester);
			lastUpdate = remoteModTime;
			updateSuccessCount++;
			suggestionsCount = querySuggester.recordCount();
			log.info("Updated suggester for index {} with {} records", indexName, suggestionsCount);
		}
		catch (AlreadyClosedException ace) {
			log.info("Suggester Update for index {} canceled, because suggester closed", indexName);
			querySuggester.destroy();
			throw ace;
		}
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
