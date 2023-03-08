package de.cxp.ocs.elasticsearch.model.query;

import java.util.Collections;
import java.util.List;

import de.cxp.ocs.elasticsearch.model.visitor.QueryTermVisitor;

public final class MatchAllQuery implements AnalyzedQuery {

	public final static MatchAllQuery INSTANCE = new MatchAllQuery();

	@Override
	public List<String> getInputTerms() {
		return Collections.emptyList();
	}

	@Override
	public int getTermCount() {
		return 0;
	}

	@Override
	public String toQueryString() {
		return "*";
	}

	@Override
	public void accept(QueryTermVisitor visitor) {
		// noop
	}

}
