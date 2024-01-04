package de.cxp.ocs.elasticsearch;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Component
@NoArgsConstructor
@Slf4j
public class AbandonedIndexCleanupTask implements Runnable {

	@Autowired
	ElasticsearchIndexClient indexClient;

	@Setter
	@Value("${ocs.index.cleanup.abandonedIndexDeletionAgeSeconds:21600}")
	int abandonedIndexDeletionAgeSeconds;

	@Setter
	private String indexNamePattern = "ocs-*";

	public AbandonedIndexCleanupTask(ElasticsearchIndexClient indexClient, String indexName, int abandonedIndexDeletionAgeSeconds) {
		this.indexClient = indexClient;
		this.abandonedIndexDeletionAgeSeconds = abandonedIndexDeletionAgeSeconds;
		this.indexNamePattern = "ocs-*-" + indexName + "-*";
	}

	@Scheduled(fixedRate = 60, timeUnit = TimeUnit.MINUTES)
	public void run() {
		Map<String, Set<AliasMetadata>> aliases = indexClient.getAliases(indexNamePattern);

		Instant taskRunTime = Instant.now();
		for (Entry<String, Set<AliasMetadata>> alias : aliases.entrySet()) {
			if (!alias.getValue().isEmpty()) continue;

			Instant indexCreationDate = indexClient.getSettings(alias.getKey())
					.map(s -> Instant.ofEpochMilli(s.getAsLong("index.creation_date", 0L)))
					.orElse(Instant.MAX);

			Duration activeImportAge = Duration.between(indexCreationDate, taskRunTime);
			if (activeImportAge.toSeconds() >= abandonedIndexDeletionAgeSeconds) {
				log.info("Deleting abandoned index {} that was created {} ago",
						alias.getKey(),
						DurationFormatUtils.formatDurationWords(activeImportAge.toMillis(), true, true));
				indexClient.deleteIndex(alias.getKey(), true);
			}
		}
	}
}
