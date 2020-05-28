package io.searchhub.smartsuggest.querysuggester;

import static java.util.Collections.emptySet;

import java.util.List;
import java.util.Set;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

public interface QuerySuggester extends AutoCloseable {

    @RequiredArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Getter
    class Result {
        /**
         * The user friendly name of the result group
         */
        private final String name;

        private final List<String> suggestions;
    }

    int DEFAULT_MAXIMUM_RESULTS = 10;

    /**
     * @param term
     * 		the term for which to get suggestions
     * @return A list of suggestions for the given term.
     * 		   At most {@value #DEFAULT_MAXIMUM_RESULTS} results will be returned
     */
    default List<Result> suggest(String term) throws SuggestException {
        return suggest(term, DEFAULT_MAXIMUM_RESULTS, emptySet());
    }

    /**
     * @param term
     * 		the term for which to get suggestions
     * @param maxResults
     * 		the maximum number of suggestions to return
     * @param groups
     * 		the group names used for filtering
     * @return A list of suggestions for the given term
     */
    List<Result> suggest(String term, int maxResults, Set<String> groups) throws SuggestException;

    /**
     * Destroys any resources created by this suggester
     */
	default void destroy() {
		try {
			close();
		}
		catch (Exception e) {
			org.slf4j.LoggerFactory.getLogger(QuerySuggester.class)
					.warn("error while destroying suggester: {}:{}", e.getClass(), e.getMessage());
		}
	}
}
