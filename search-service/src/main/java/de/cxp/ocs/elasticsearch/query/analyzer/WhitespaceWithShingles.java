package de.cxp.ocs.elasticsearch.query.analyzer;

import java.util.ArrayList;
import java.util.List;

import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;
import de.cxp.ocs.elasticsearch.query.model.WeightedWord;
import de.cxp.ocs.spi.search.UserQueryAnalyzer;

public class WhitespaceWithShingles implements UserQueryAnalyzer {

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