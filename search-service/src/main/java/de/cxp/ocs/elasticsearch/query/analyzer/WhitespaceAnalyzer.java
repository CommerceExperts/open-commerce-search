package de.cxp.ocs.elasticsearch.query.analyzer;

import java.util.ArrayList;
import java.util.List;

import de.cxp.ocs.elasticsearch.model.query.AnalyzedQuery;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.model.query.MultiTermQuery;
import de.cxp.ocs.elasticsearch.model.query.SingleTermQuery;
import de.cxp.ocs.elasticsearch.model.term.QueryStringTerm;
import de.cxp.ocs.elasticsearch.model.term.WeightedTerm;
import de.cxp.ocs.spi.search.UserQueryAnalyzer;

public class WhitespaceAnalyzer implements UserQueryAnalyzer {

	@Override
	public ExtendedQuery analyze(String userQuery) {
		List<QueryStringTerm> terms = toQueryStringWordList(userQuery.toLowerCase().trim().split("\\s+"));
		AnalyzedQuery termsQuery = terms.size() == 0 ? new SingleTermQuery(terms.get(0)) : new MultiTermQuery(terms);
		return new ExtendedQuery(termsQuery);
	}

	public static List<QueryStringTerm> toQueryStringWordList(String[] words) {
		List<QueryStringTerm> queryWords = new ArrayList<>(words.length);
		for (String word : words) {
			queryWords.add(new WeightedTerm(word));
		}
		return queryWords;
	}
}
