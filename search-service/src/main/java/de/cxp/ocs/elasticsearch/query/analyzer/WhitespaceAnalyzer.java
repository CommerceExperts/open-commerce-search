package de.cxp.ocs.elasticsearch.query.analyzer;

import java.util.ArrayList;
import java.util.List;

import de.cxp.ocs.elasticsearch.model.query.AnalyzedQuery;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.model.query.MultiTermQuery;
import de.cxp.ocs.elasticsearch.model.query.SingleTermQuery;
import de.cxp.ocs.elasticsearch.model.term.WeightedTerm;
import de.cxp.ocs.spi.search.UserQueryAnalyzer;

public class WhitespaceAnalyzer implements UserQueryAnalyzer {

	@Override
	public ExtendedQuery analyze(String userQuery) {
		List<WeightedTerm> terms = toQueryStringWordList(userQuery.toLowerCase().trim().split("\\s+"));
		if (terms.isEmpty()) {
			return ExtendedQuery.MATCH_ALL;
		}
		AnalyzedQuery termsQuery = terms.size() == 1 ? new SingleTermQuery(userQuery, terms.get(0)) : new MultiTermQuery(terms);
		return new ExtendedQuery(termsQuery);
	}

	public static List<WeightedTerm> toQueryStringWordList(String[] words) {
		List<WeightedTerm> queryWords = new ArrayList<>(words.length);
		for (String word : words) {
			queryWords.add(new WeightedTerm(word));
		}
		return queryWords;
	}
}
