package de.cxp.ocs.elasticsearch.query.analyzer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.cxp.ocs.elasticsearch.query.model.AlternativeTerm;
import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;
import de.cxp.ocs.elasticsearch.query.model.WeightedWord;
import de.cxp.ocs.elasticsearch.query.model.WordAssociation;
import de.cxp.ocs.spi.search.ConfigurableExtension;
import de.cxp.ocs.spi.search.UserQueryAnalyzer;
import de.cxp.ocs.util.StringUtils;

/**
 * Analyzer that combines the Querqy results for the original and the ASCIIfied query.
 * 
 * <p>
 * Settings similar to {@link QuerqyQueryExpander}
 * </p>
 * 
 * @author rudolf.batt@commerce-experts.com
 */
public class AsciifyQuerqyQueryAnalyzer implements UserQueryAnalyzer, ConfigurableExtension {

	final QuerqyQueryExpander querqy = new QuerqyQueryExpander();

	@Override
	public void initialize(Map<String, String> settings) {
		querqy.initialize(settings);
	}

	@Override
	public List<QueryStringTerm> analyze(String userQuery) {
		List<QueryStringTerm> finalAnalyzedTerms;
		List<QueryStringTerm> analyzedTerms = querqy.analyze(userQuery);
		String asciifiedQuery = StringUtils.asciify(userQuery);

		if (!asciifiedQuery.equals(userQuery)) {
			finalAnalyzedTerms = getCombinedAnalyzedTerms(analyzedTerms, asciifiedQuery);
		}
		else {
			finalAnalyzedTerms = analyzedTerms;
		}

		return finalAnalyzedTerms;
	}

	private List<QueryStringTerm> getCombinedAnalyzedTerms(List<QueryStringTerm> analyzedTerms, String asciifiedQuery) {
		List<QueryStringTerm> finalAnalyzedTerms;
		finalAnalyzedTerms = new ArrayList<>();

		Map<String, QueryStringTerm> analyzedTermIndex = new LinkedHashMap<>();
		analyzedTerms.forEach(qterm -> analyzedTermIndex.put(StringUtils.asciify(qterm.getWord()), qterm));

		List<QueryStringTerm> analyzedAsciiTerms = querqy.analyze(asciifiedQuery);
		for (QueryStringTerm asciiTerm : analyzedAsciiTerms) {
			QueryStringTerm origAnalyzedTerm = analyzedTermIndex.remove(asciiTerm.getWord());
			if (origAnalyzedTerm != null) {
				finalAnalyzedTerms.add(mergeTerms(origAnalyzedTerm, asciiTerm));
			}
			else {
				finalAnalyzedTerms.add(asciiTerm);
			}
		}
		return finalAnalyzedTerms;
	}

	private QueryStringTerm mergeTerms(QueryStringTerm term1, QueryStringTerm term2) {
		if (term1.equals(term2)) {
			return term1;
		}
		if (term1 instanceof WordAssociation && term2 instanceof WordAssociation) {
			WordAssociation targetTerm = ((WordAssociation) term1);
			targetTerm.putOrUpdate(new WeightedWord(((WordAssociation) term2).getWord()));
			((WordAssociation) term2).getRelatedWords().values().forEach(targetTerm::putOrUpdate);
			return targetTerm;
		}
		if (term1 instanceof WordAssociation) {
			((WordAssociation) term1).putOrUpdate(term2);
			return term1;
		}
		if (term2 instanceof WordAssociation) {
			((WordAssociation) term2).putOrUpdate(term1);
			return term2;
		}
		
		return new AlternativeTerm(term1, term2);
	}

}
