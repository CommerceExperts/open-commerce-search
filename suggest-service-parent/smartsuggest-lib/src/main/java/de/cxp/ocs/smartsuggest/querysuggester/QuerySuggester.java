package de.cxp.ocs.smartsuggest.querysuggester;

import static java.util.Collections.emptySet;

import java.util.List;
import java.util.Set;

public interface QuerySuggester extends AutoCloseable {

    int DEFAULT_MAXIMUM_RESULTS = 10;

    /**
     * @param term
     * 		the term for which to get suggestions
     * @return A list of suggestions for the given term.
     * 		   At most {@value #DEFAULT_MAXIMUM_RESULTS} results will be returned
     */
	default List<Suggestion> suggest(String term) throws SuggestException {
        return suggest(term, DEFAULT_MAXIMUM_RESULTS, emptySet());
    }

    /**
	 * @param term
	 *        the term for which to get suggestions
	 * @param maxResults
	 *        the maximum number of suggestions to return
	 * @param tags
	 *        the group names used for filtering
	 * @return A list of suggestions for the given term
	 */
	List<Suggestion> suggest(String term, int maxResults, Set<String> tags) throws SuggestException;

	/**
	 * @return true if ready to serve suggestions
	 */
	boolean isReady();

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
