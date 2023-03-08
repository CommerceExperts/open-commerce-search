package de.cxp.ocs.elasticsearch.model.visitor;

import java.util.function.Consumer;

import de.cxp.ocs.elasticsearch.model.query.AnalyzedQuery;
import de.cxp.ocs.elasticsearch.model.term.QueryStringTerm;

public abstract class AbstractTermVisitor implements QueryTermVisitor {

	@Override
	public void visitSubQuery(AnalyzedQuery query) {
		query.accept(this);
	}

	public static QueryTermVisitor forEachTerm(Consumer<QueryStringTerm> termConcumer) {
		return new AbstractTermVisitor() {

			@Override
			public void visitTerm(QueryStringTerm term) {
				termConcumer.accept(term);
			}
		};
	}

}
