package de.cxp.ocs.elasticsearch.query.analyzer;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import de.cxp.ocs.elasticsearch.model.query.AnalyzedQuery;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.model.query.MultiTermQuery;
import de.cxp.ocs.elasticsearch.model.query.SingleTermQuery;
import de.cxp.ocs.elasticsearch.model.term.WeightedTerm;
import de.cxp.ocs.spi.search.UserQueryAnalyzer;

/**
 * Splits the string by every character that is not a letter, number, dash or underscore (which are considered as
 * word-bind characters)
 * 
 * @author rb@commerce-experts.com
 */
public class NonAlphanumericWordSplitAnalyzer implements UserQueryAnalyzer {

	public static String BIND_CHARS = "-_.";

	@Override
	public ExtendedQuery analyze(String userQuery) {
		List<WeightedTerm> terms = toQueryStringWordList(userQuery.toLowerCase().trim().split("[^\\p{L}\\p{N}" + BIND_CHARS + "]+"));
		if (terms.isEmpty()) {
			return ExtendedQuery.MATCH_ALL;
		}
		AnalyzedQuery termsQuery = terms.size() == 1 ? new SingleTermQuery(terms.get(0)) : new MultiTermQuery(terms);
		return new ExtendedQuery(termsQuery);
	}

	public static List<WeightedTerm> toQueryStringWordList(String[] words) {
		List<WeightedTerm> queryWords = new ArrayList<>(words.length);
		for (String word : words) {
			word = StringUtils.strip(word, BIND_CHARS);
			if (word.isEmpty()) continue;

			queryWords.add(new WeightedTerm(word));
		}
		return queryWords;
	}
}
