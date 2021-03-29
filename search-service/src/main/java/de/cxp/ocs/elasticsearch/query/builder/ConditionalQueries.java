package de.cxp.ocs.elasticsearch.query.builder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;
import de.cxp.ocs.spi.search.ESQueryFactory;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ConditionalQueries {

	private final List<ConditionalQuery> conditionalQueryBuilders;

	public ConditionalQueries(ESQueryFactory defaultQueryBuilder) {
		conditionalQueryBuilders = new ArrayList<>();

		ConditionalQuery oneAndOnlyQuery = new ConditionalQuery();
		oneAndOnlyQuery.predicate = new TermCountCondition(1, Integer.MAX_VALUE);
		oneAndOnlyQuery.queryBuilder = defaultQueryBuilder;
		conditionalQueryBuilders.add(oneAndOnlyQuery);
	}

	static class ConditionalQuery {

		Predicate<List<QueryStringTerm>>	predicate;
		ESQueryFactory						queryBuilder;
	}

	@RequiredArgsConstructor
	public static class TermCountCondition implements Predicate<List<QueryStringTerm>> {

		private final int minTermCount;

		private final int maxTermCount;

		@Override
		public boolean test(List<QueryStringTerm> words) {
			return minTermCount <= words.size() && words.size() <= maxTermCount;
		}

	}

	@RequiredArgsConstructor
	public static class PatternCondition implements Predicate<List<QueryStringTerm>> {

		private final Pattern matchPattern;

		public PatternCondition(String regex) {
			matchPattern = Pattern.compile(regex);
		}

		@Override
		public boolean test(List<QueryStringTerm> words) {
			StringBuilder wholeWordString = new StringBuilder();
			Iterator<QueryStringTerm> wordIter = words.iterator();
			while (wordIter.hasNext()) {
				wholeWordString.append(wordIter.next());
				if (wordIter.hasNext()) wholeWordString.append(" ");
			}
			// maybe it could be useful to add an option to apply pattern
			// matching on single words
			return matchPattern.matcher(wholeWordString.toString()).matches();
		}

	}

	@RequiredArgsConstructor
	public static class ComposedPredicate implements Predicate<List<QueryStringTerm>> {

		private final List<Predicate<List<QueryStringTerm>>> collectedPredicates;

		@Override
		public boolean test(List<QueryStringTerm> t) {
			for (Predicate<List<QueryStringTerm>> p : collectedPredicates) {
				if (!p.test(t)) return false;
			}
			return true;
		}

	}

	public Iterator<ESQueryFactory> getMatchingFactories(final List<QueryStringTerm> searchWords) {
		if (searchWords == null || searchWords.size() == 0) return null;

		final List<ESQueryFactory> matchingQueryFactories = new ArrayList<>();
		for (ConditionalQuery condAndQueryFactory : conditionalQueryBuilders) {
			if (condAndQueryFactory.predicate.test(searchWords)) {
				matchingQueryFactories.add(condAndQueryFactory.queryBuilder);
			}
		}

		// XXX: improvement: if first QueryBuilder is a PerFetchQueryBuilder,
		// start pre-fetch phase in a separate thread

		return matchingQueryFactories.iterator();
	}

}
