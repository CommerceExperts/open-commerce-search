package de.cxp.ocs.elasticsearch.query.analyzer;

import java.util.*;

import de.cxp.ocs.elasticsearch.model.query.MultiVariantQuery;
import de.cxp.ocs.elasticsearch.model.query.AnalyzedQuery;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.model.term.QueryStringTerm;
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
	public ExtendedQuery analyze(String userQuery) {
		ExtendedQuery finalAnalyzedTerms;
		ExtendedQuery querqyResult1 = querqy.analyze(userQuery);
		String asciifiedQuery = StringUtils.asciify(userQuery);

		if (!asciifiedQuery.equals(userQuery)) {
			ExtendedQuery querqyResult2 = querqy.analyze(asciifiedQuery);
			finalAnalyzedTerms = getCombinedQueries(querqyResult1, querqyResult2);
		}
		else {
			finalAnalyzedTerms = querqyResult1;
		}

		return finalAnalyzedTerms;
	}

	private ExtendedQuery getCombinedQueries(ExtendedQuery querqyResult1, ExtendedQuery querqyResult2) {
		List<QueryStringTerm> filters;
		if (querqyResult1.getFilters().size() == 0) {
			filters = querqyResult2.getFilters();
		}
		else if (querqyResult2.getFilters().size() == 0) {
			filters = querqyResult1.getFilters();
		}
		else {
			Set<QueryStringTerm> deduplicatedFilters = new LinkedHashSet<>();
			deduplicatedFilters.addAll(querqyResult1.getFilters());
			deduplicatedFilters.addAll(querqyResult2.getFilters());
			filters = new ArrayList<>(deduplicatedFilters);
		}

		AnalyzedQuery combinedQuery;
		AnalyzedQuery searchQuery1 = querqyResult1.getSearchQuery();
		AnalyzedQuery searchQuery2 = querqyResult2.getSearchQuery();
		if (searchQuery1.equals(searchQuery2)) {
			combinedQuery = searchQuery1;
		}
		else {
			combinedQuery = new MultiVariantQuery(searchQuery1.getInputTerms(), Arrays.asList(searchQuery1, searchQuery2));
		}

		return new ExtendedQuery(combinedQuery, filters);
	}

}
