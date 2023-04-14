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

/**
 * Splits the string by every character that is not a letter, number, dash or underscore (which are considered as
 * word-bind characters)
 * 
 * @author rb@commerce-experts.com
 */
public class NonAlphanumericWordSplitAnalyzer implements UserQueryAnalyzer {

	@Override
	public ExtendedQuery analyze(String userQuery) {
		List<QueryStringTerm> terms = toQueryStringWordList(userQuery.toLowerCase().trim().split("[^\\p{L}\\p{N}-_]+"));
		if (terms.isEmpty()) {
			return ExtendedQuery.MATCH_ALL;
		}
		AnalyzedQuery termsQuery = terms.size() == 1 ? new SingleTermQuery(terms.get(0)) : new MultiTermQuery(terms);
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
