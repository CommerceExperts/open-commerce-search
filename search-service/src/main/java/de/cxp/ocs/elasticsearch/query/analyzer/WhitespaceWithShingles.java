package de.cxp.ocs.elasticsearch.query.analyzer;

import java.util.ArrayList;
import java.util.List;

import de.cxp.ocs.elasticsearch.model.query.AnalyzedQuery;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.model.query.MultiTermQuery;
import de.cxp.ocs.elasticsearch.model.query.SingleTermQuery;
import de.cxp.ocs.elasticsearch.model.term.WeightedTerm;
import de.cxp.ocs.spi.search.UserQueryAnalyzer;

public class WhitespaceWithShingles implements UserQueryAnalyzer {

	@Override
	public ExtendedQuery analyze(String userQuery) {
		String[] split = userQuery.toLowerCase().trim().split("\\s+");
		List<WeightedTerm> terms = new ArrayList<>();

		for (int i = 0; i < split.length - 1; i++) {
			terms.add(new WeightedTerm(split[i]));
			terms.add(new WeightedTerm(split[i] + split[i + 1]));
		}
		terms.add(new WeightedTerm(split[split.length - 1]));

		AnalyzedQuery termsQuery = terms.isEmpty() ? new SingleTermQuery(userQuery, terms.get(0)) : new MultiTermQuery(terms);
		return new ExtendedQuery(termsQuery);
	}

}
