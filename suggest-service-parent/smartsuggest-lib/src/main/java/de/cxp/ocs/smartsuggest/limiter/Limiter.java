package de.cxp.ocs.smartsuggest.limiter;

import java.util.List;

import de.cxp.ocs.smartsuggest.querysuggester.Suggestion;

/**
 * <p>
 * A Limiter is used for results that have different kind of suggestions, for
 * example if several different data sources are used at different suggesters,
 * as it's done at the
 * {@link de.cxp.ocs.smartsuggest.querysuggester.CompoundQuerySuggester}.
 * </p>
 * <p>
 * It could also be used to retrieve more suggestions, group or sort them by a
 * certain criterion and limit them afterwards.
 * </p>
 */
public interface Limiter {

	List<Suggestion> limit(List<Suggestion> suggestions, int limit);

}
