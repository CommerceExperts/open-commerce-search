package de.cxp.ocs.elasticsearch.model.visitor;

import de.cxp.ocs.elasticsearch.model.query.AnalyzedQuery;
import de.cxp.ocs.elasticsearch.model.term.QueryStringTerm;

public interface QueryTermVisitor {

	void visitTerm(QueryStringTerm term);

	void visitSubQuery(AnalyzedQuery query);

}
