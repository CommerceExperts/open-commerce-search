package de.cxp.ocs.smartsuggest.querysuggester;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;

import de.cxp.ocs.smartsuggest.limiter.Limiter;
import de.cxp.ocs.smartsuggest.spi.SuggestConfigProvider;
import de.cxp.ocs.smartsuggest.spi.SuggestDataProvider;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompoundQuerySuggester implements QuerySuggester, Accountable {

	final List<QuerySuggester> suggesterList;

	private final Limiter limiter;

	@Setter
	private boolean isMultiThreaded = false;

	public CompoundQuerySuggester(List<QuerySuggester> suggester, Limiter limiter) {
		suggesterList = new ArrayList<>(suggester);
		this.limiter = limiter;
	}

	// for testing purposes
	CompoundQuerySuggester(String indexName, List<SuggestDataProvider> dataProviders, SuggestConfigProvider configProvider, SuggesterFactory factory, Limiter limiter)
			throws IOException {
		suggesterList = new ArrayList<>();
		for (SuggestDataProvider dataProvider : dataProviders) {
			if (dataProvider.hasData(indexName)) {
				QuerySuggester suggester = factory.getSuggester(dataProvider.loadData(indexName), configProvider.get(indexName));
				suggesterList.add(suggester);
			}
		}
		this.limiter = limiter;
	}

	@Override
	public List<Suggestion> suggest(String term, int maxResults, Set<String> tags) throws SuggestException {
		if (suggesterList.isEmpty()) return Collections.emptyList();
		if (suggesterList.size() == 1) return suggesterList.get(0).suggest(term, maxResults, tags);

		Stream<QuerySuggester> suggesterStream = suggesterList.stream();
		if (isMultiThreaded) suggesterStream = suggesterStream.parallel();

		List<Suggestion> finalResult = new ArrayList<>();
		suggesterStream
				.map(s -> s.suggest(term, maxResults, tags))
				.forEach(finalResult::addAll);
		return limiter.limit(finalResult, maxResults);
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
