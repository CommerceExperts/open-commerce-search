package de.cxp.ocs.smartsuggest.spi;

import java.util.*;

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
				.mapToLong(sdp -> sdp.getLastDataModTime(indexName))
				.max()
				.orElse(-1L);
	}

	@Override
	public SuggestData loadData(final String indexName) {
		// don't do short cuts here for a single suggestDataProvider,
		// because if this merger is used, the user should
		// be able to rely on the tagged data for filtering!

		SuggestData merged = new SuggestData();

		// set mutable types so they can be extended in the loop
		merged.setWordsToIgnore(new HashSet<>());
		merged.setSuggestRecords(new ArrayList<>());

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
						+ " Locale '{}' from will be kept and '{}' from {}-data will be dropped",
						indexName, locale, loadedData.getType());
			}

			if (loadedData.getModificationTime() > lastModificationTime) {
				lastModificationTime = loadedData.getModificationTime();
			}

			merged.getWordsToIgnore().addAll(loadedData.getWordsToIgnore());

			loadedData.getSuggestRecords()
					.forEach(suggestRecord -> {
						attachTypeAsTag(suggestRecord, loadedData.getType());
						merged.getSuggestRecords().add(suggestRecord);
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
