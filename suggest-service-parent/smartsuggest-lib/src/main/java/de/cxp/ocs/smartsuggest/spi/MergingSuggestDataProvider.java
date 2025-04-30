package de.cxp.ocs.smartsuggest.spi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Merges all the data it gets from all specified data providers. To distinguish
 * the types afterwards, the suggest data type is added as tag to each according
 * suggest record.
 */
@Slf4j
@RequiredArgsConstructor
public class MergingSuggestDataProvider implements SuggestDataProvider {

	final List<SuggestDataProvider> suggestDataProviders;

	@Override
	public boolean hasData(final String indexName) {
		return suggestDataProviders.stream().anyMatch(sdp -> sdp.hasData(indexName));
	}

	@Override
	public long getLastDataModTime(final String indexName) {
		return suggestDataProviders.stream()
				.filter(sdp -> sdp.hasData(indexName))
				.mapToLong(sdp -> {
					try {
						return sdp.getLastDataModTime(indexName);
					}
					catch (IOException e) {
						log.error("Failed to retrieve dataModTime from data provider {} for index {}",
								sdp.getClass().getSimpleName(), indexName);
						return -1;
					}
				})
				.max()
				.orElse(-1L);
	}

	@Override
	public SuggestData loadData(final String indexName) throws IOException {
		// don't do short cuts here for a single suggestDataProvider,
		// because if this merger is used, the user should
		// be able to rely on the tagged data for filtering!

		SuggestData merged = new SuggestData();

		// set mutable types so they can be extended in the loop
		merged.setWordsToIgnore(new HashSet<>());
		ArrayList<SuggestRecord> suggestRecords = new ArrayList<>();
		merged.setSuggestRecords(suggestRecords);

		Locale locale = null;
		long lastModificationTime = -1;

		for (SuggestDataProvider sdp : suggestDataProviders) {
			if (!sdp.hasData(indexName)) continue;

			// TODO cache data from unchanged data sources would be good here
			// => better use persistence to store that stuff per index name

			SuggestData loadedData = sdp.loadData(indexName);
			if (locale == null) {
				locale = loadedData.getLocale();
			}
			else if (loadedData.getLocale() != null && !locale.equals(loadedData.getLocale())) {
				log.warn("the different suggestDataProviders provide different locale setting for index {}."
						+ " Locale '{}' from first data provider will be kept and '{}' from {}-data will be dropped",
						indexName, locale, loadedData.getLocale(), loadedData.getType());
			}

			if (loadedData.getModificationTime() > lastModificationTime) {
				lastModificationTime = loadedData.getModificationTime();
			}

			merged.getWordsToIgnore().addAll(loadedData.getWordsToIgnore());

			loadedData.getSuggestRecords()
					.forEach(suggestRecord -> {
						attachTypeAsTag(suggestRecord, loadedData.getType());
						suggestRecords.add(suggestRecord);
					});
		}

		merged.setLocale(locale);
		merged.setModificationTime(lastModificationTime);
		merged.setType("merged");
		return merged;
	}

	private void attachTypeAsTag(SuggestRecord suggestRecord, String dataType) {
		Set<String> tags = suggestRecord.getTags();
		if (tags == null || tags.isEmpty()) {
			suggestRecord.setTags(Collections.singleton(dataType));
		}
		else {
			tags = tags instanceof HashSet ? tags : new HashSet<>(tags);
			tags.add(dataType);
			suggestRecord.setTags(tags);
		}
	}

}
