package io.searchhub.smartsuggest.querysuggester;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import io.searchhub.smartsuggest.spi.SuggestRecord;

public interface QueryIndexer {

	/**
	 * @return The time of the last indexing
	 */
	Instant getLastIndexTime();

	/**
	 * @param suggestions
	 * 		the suggestions to index
	 */
	CompletableFuture<Void> index(Iterable<SuggestRecord> suggestions) throws IOException;
}
