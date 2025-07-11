package de.cxp.ocs.smartsuggest.querysuggester;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;

import de.cxp.ocs.smartsuggest.spi.SuggestConfig;
import de.cxp.ocs.smartsuggest.spi.SuggestConfigProvider;
import de.cxp.ocs.smartsuggest.spi.SuggestDataProvider;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompoundQuerySuggester implements QuerySuggester, Accountable {

	final List<QuerySuggester> suggesterList;

	@Setter
	private boolean isMultiThreaded = false;
	@Setter
	private boolean	doLimitFinalResult	= true;

	private SuggestConfig defaultSuggestConfig;


	public CompoundQuerySuggester(List<QuerySuggester> suggester, SuggestConfig defaultSuggestConfig) {
		suggesterList = new ArrayList<>(suggester);
		this.defaultSuggestConfig = defaultSuggestConfig;
	}

	// for testing purposes
	CompoundQuerySuggester(String indexName, List<SuggestDataProvider> dataProviders, SuggestConfigProvider configProvider, SuggesterFactory<?> factory)
			throws IOException {
		suggesterList = new ArrayList<>();
		for (SuggestDataProvider dataProvider : dataProviders) {
			if (dataProvider.hasData(indexName)) {
				QuerySuggester suggester = factory.getSuggester(dataProvider.loadData(indexName), configProvider.getConfig(indexName, defaultSuggestConfig));
				suggesterList.add(suggester);
			}
		}
	}

	@Override
	public List<Suggestion> suggest(String term, int maxResults, Set<String> tags) throws SuggestException {
		if (suggesterList.isEmpty()) return Collections.emptyList();
		if (suggesterList.size() == 1) return suggesterList.getFirst().suggest(term, maxResults, tags);

		Stream<QuerySuggester> suggesterStream = isMultiThreaded ? suggesterList.stream().parallel() : suggesterList.stream();
		List<Suggestion> finalResult = new ArrayList<>();
		suggesterStream
				.map(s -> s.suggest(term, maxResults, tags))
				.forEach(finalResult::addAll);
		return doLimitFinalResult && finalResult.size() > maxResults ? finalResult.subList(0, maxResults) : finalResult;
	}

	@Override
	public boolean isReady() {
		return suggesterList.stream().allMatch(QuerySuggester::isReady);
	}

	@Override
	public void close() throws Exception {
		suggesterList.forEach(s -> {
			try {
				s.close();
			}
			catch (Exception e) {
				log.error("failed to close suggester {} because of ", s.getClass().getSimpleName(), e);
			}
		});

	}

	@Override
	public long ramBytesUsed() {
		long mySize = RamUsageEstimator.shallowSizeOf(this);
		for (QuerySuggester s : suggesterList) {
			if (s instanceof Accountable) {
				// the heavy implementations all should implement Accountable
				mySize += RamUsageEstimator.sizeOf((Accountable) s);
			}
			else {
				mySize += RamUsageEstimator.shallowSizeOf(s);
			}
		}
		return mySize;
	}

	@Override
	public long recordCount() {
		long recordCount = 0;
		for (QuerySuggester s : suggesterList) {
			recordCount += s.recordCount();
		}
		return recordCount;
	}
}
