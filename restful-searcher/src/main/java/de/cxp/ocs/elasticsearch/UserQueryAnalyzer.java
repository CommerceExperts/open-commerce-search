package de.cxp.ocs.elasticsearch;

import java.util.ArrayList;
import java.util.List;

import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;
import de.cxp.ocs.elasticsearch.query.model.WeightedWord;

public interface UserQueryAnalyzer {

	public final static UserQueryAnalyzer defaultAnalyzer = new Whitespace();

	public List<QueryStringTerm> analyze(String userQuery);

	public static List<QueryStringTerm> toQueryStringWordList(String[] words) {
		List<QueryStringTerm> queryWords = new ArrayList<>();
		for (String word : words) {
			queryWords.add(new WeightedWord(word));
		}
		return queryWords;
	}

	public static class Whitespace implements UserQueryAnalyzer {

		@Override
		public List<QueryStringTerm> analyze(String userQuery) {
			return UserQueryAnalyzer.toQueryStringWordList(
					userQuery.toLowerCase().trim().split("\\s+"));
		}

	}

	/**
	 * example:
	 * - input = foo bar ish
	 * - output = foo foobar bar barish ish
	 */
	public static class WhitespaceWithShingles implements UserQueryAnalyzer {

		@Override
		public List<QueryStringTerm> analyze(String userQuery) {
			String[] split = userQuery.toLowerCase().trim().split("\\s+");
			List<QueryStringTerm> withShingles = new ArrayList<>();

			for (int i = 0; i < split.length - 1; i++) {
				withShingles.add(new WeightedWord(split[i]));
				withShingles.add(new WeightedWord(split[i] + split[i + 1]));
			}
			withShingles.add(new WeightedWord(split[split.length - 1]));

			return withShingles;
		}

	}

}
