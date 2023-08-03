package de.cxp.ocs.elasticsearch.query.builder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import de.cxp.ocs.elasticsearch.model.query.AnalyzedQuery;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
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

		Predicate<AnalyzedQuery>	predicate;
		ESQueryFactory				queryBuilder;
	}

	@RequiredArgsConstructor
	public static class TermCountCondition implements Predicate<AnalyzedQuery> {

		private final int minTermCount;

		private final int maxTermCount;

		@Override
		public boolean test(AnalyzedQuery q) {
			return minTermCount <= q.getTermCount() && q.getTermCount() <= maxTermCount;
		}

	}

	@RequiredArgsConstructor
	public static class QueryLengthCondition implements Predicate<AnalyzedQuery> {

		private final int maxQueryLength;

		@Override
		public boolean test(AnalyzedQuery q) { return (q.getInputTerms().stream().mapToInt(String::length).sum() + (q.getInputTerms().size() -1)) <= maxQueryLength; }

	}

	@RequiredArgsConstructor
	public static class PatternCondition implements Predicate<AnalyzedQuery> {

		private final Pattern matchPattern;

		public PatternCondition(String regex) {
			matchPattern = Pattern.compile(regex);
		}

		@Override
		public boolean test(AnalyzedQuery words) {
			StringBuilder wholeWordString = new StringBuilder();
			Iterator<String> wordIter = words.getInputTerms().iterator();
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
	public static class ComposedPredicate implements Predicate<AnalyzedQuery> {

		private final List<Predicate<AnalyzedQuery>> collectedPredicates;

		@Override
		public boolean test(AnalyzedQuery query) {
			for (Predicate<AnalyzedQuery> p : collectedPredicates) {
				if (!p.test(query)) return false;
			}
			return true;
		}

	}

	public List<ESQueryFactory> getMatchingFactories(final ExtendedQuery parsedQuery) {
		if (parsedQuery.getSearchQuery() == null || parsedQuery.getSearchQuery().getTermCount() == 0) return null;

		final List<ESQueryFactory> matchingQueryFactories = new ArrayList<>();
		for (ConditionalQuery condAndQueryFactory : conditionalQueryBuilders) {
			if (condAndQueryFactory.predicate.test(parsedQuery.getSearchQuery())) {
				matchingQueryFactories.add(condAndQueryFactory.queryBuilder);
			}
		}

		// XXX: improvement: if first QueryBuilder is a PerFetchQueryBuilder,
		// start pre-fetch phase in a separate thread

		return matchingQueryFactories;
	}

}
