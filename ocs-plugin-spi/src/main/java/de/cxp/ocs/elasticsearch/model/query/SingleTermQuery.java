package de.cxp.ocs.elasticsearch.model.query;

import java.util.Collections;
import java.util.List;

import de.cxp.ocs.elasticsearch.model.term.QueryStringTerm;
import de.cxp.ocs.elasticsearch.model.visitor.QueryTermVisitor;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SingleTermQuery implements AnalyzedQuery {

	private final QueryStringTerm term;

	@Override
	public List<String> getInputTerms() {
		return Collections.singletonList(term.getRawTerm());
	}

	@Override
	public int getTermCount() {
		return 1;
	}

	@Override
	public String toQueryString() {
		return term.toQueryString();
	}

	@Override
	public void accept(QueryTermVisitor visitor) {
		visitor.visitTerm(term);
	}

}
