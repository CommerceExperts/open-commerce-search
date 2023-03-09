package de.cxp.ocs.elasticsearch.query.analyzer;

import java.util.ArrayList;
import java.util.List;

import de.cxp.ocs.elasticsearch.model.query.AnalyzedQuery;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.model.term.QueryStringTerm;
import de.cxp.ocs.elasticsearch.model.visitor.QueryTermVisitor;

public class AnalyzerUtil {

	public static List<QueryStringTerm> extractTerms(ExtendedQuery analyzedQuery) {
		List<QueryStringTerm> result = new ArrayList<>();
		analyzedQuery.getSearchQuery().accept(new QueryTermVisitor() {

			@Override
			public void visitTerm(QueryStringTerm term) {
				result.add(term);
			}

			@Override
			public void visitSubQuery(AnalyzedQuery term) {
				term.accept(this);
			}
		});
		result.addAll(analyzedQuery.getFilters());
		return result;
	}
}
