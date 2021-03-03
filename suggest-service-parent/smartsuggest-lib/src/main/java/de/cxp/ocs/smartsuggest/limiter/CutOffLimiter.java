package de.cxp.ocs.smartsuggest.limiter;

import java.util.List;

import de.cxp.ocs.smartsuggest.querysuggester.Suggestion;

/**
 * Simplest implementation, that just cut's off the given list with the
 * specified limit.
 */
public class CutOffLimiter implements Limiter {

	@Override
	public List<Suggestion> limit(List<Suggestion> suggestions, int limit) {
		return suggestions.size() > limit ? suggestions.subList(0, limit) : suggestions;
	}

}
