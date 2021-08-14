package de.cxp.ocs;

import static de.cxp.ocs.OCSStack.getElasticsearchClient;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.elasticsearch.client.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.rnorth.ducttape.unreliables.Unreliables;

import de.cxp.ocs.client.SuggestClient;
import de.cxp.ocs.model.params.SearchQuery;
import de.cxp.ocs.model.result.SearchResult;
import de.cxp.ocs.model.suggest.Suggestion;

@ExtendWith({ OCSStack.class })
public class ITBulkIndexationWorks {

	private final String indexName = "indexation_test";

	@Test
	public void testDefaultIndexation() throws Exception {
		assertThat(new DataIndexer(OCSStack.getImportClient()).indexTestData(indexName)).isTrue();

		flushIndex();

		SearchResult sportResult = OCSStack.getSearchClient()
				.search(indexName, new SearchQuery().setQ("sport"), Collections.emptyMap());
		assertThat(sportResult.getSlices().get(0).matchCount).isGreaterThan(0);

		SuggestClient suggestClient = OCSStack.getSuggestClient();
		Callable<List<Suggestion>> getSuggestions = () -> suggestClient.suggest(indexName, "ap", 10, "");

		// first request is empty - will start to fetch data from ES index
		assertThat(getSuggestions.call()).isEmpty();
		Unreliables.retryUntilTrue(20, TimeUnit.SECONDS, () -> getSuggestions.call().size() > 0);
		assertThat(getSuggestions.call()).isNotEmpty();
	}

	private void flushIndex() throws IOException, InterruptedException {
		getElasticsearchClient().performRequest(new Request("POST", indexName + "/_flush/synced"));
	}
}
