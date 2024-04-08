package de.cxp.ocs.elasticsearch.model.query;

import java.util.Collections;
import java.util.List;

import de.cxp.ocs.elasticsearch.model.term.QueryStringTerm;
import de.cxp.ocs.elasticsearch.model.term.WeightedTerm;
import de.cxp.ocs.elasticsearch.model.visitor.QueryTermVisitor;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SingleTermQuery implements AnalyzedQuery {

	private final String inputTerm;

	private final QueryStringTerm term;

	public SingleTermQuery(WeightedTerm weightedTerm) {
		inputTerm = weightedTerm.getRawTerm();
		term = weightedTerm;
	}

	@Override
	public List<String> getInputTerms() {
		return Collections.singletonList(inputTerm);
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

	@Override
	public String toString() {
		return toQueryString();
	}


}
