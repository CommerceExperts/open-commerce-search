package de.cxp.ocs.elasticsearch.query.analyzer;

import java.util.ArrayList;
import java.util.List;

import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;
import de.cxp.ocs.elasticsearch.query.model.WeightedWord;
import de.cxp.ocs.spi.search.UserQueryAnalyzer;

public class WhitespaceAnalyzer implements UserQueryAnalyzer {

	@Override
	public List<QueryStringTerm> analyze(String userQuery) {
		return toQueryStringWordList(
				userQuery.toLowerCase().trim().split("\\s+"));
	}

	public static List<QueryStringTerm> toQueryStringWordList(String[] words) {
		List<QueryStringTerm> queryWords = new ArrayList<>(words.length);
		for (String word : words) {
			queryWords.add(new WeightedWord(word));
		}
		return queryWords;
	}
}
