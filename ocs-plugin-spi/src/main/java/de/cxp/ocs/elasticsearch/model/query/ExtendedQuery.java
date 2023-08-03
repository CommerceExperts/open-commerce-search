package de.cxp.ocs.elasticsearch.model.query;

import java.util.Collections;
import java.util.List;

import de.cxp.ocs.elasticsearch.model.term.QueryStringTerm;
import de.cxp.ocs.elasticsearch.model.util.QueryStringUtil;
import de.cxp.ocs.elasticsearch.model.visitor.QueryTermVisitor;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * A query that consists of an analyzed query extended by filters (and maybe more in the future).
 * 
 * @author Rudolf Batt
 */
@Data
@RequiredArgsConstructor
public class ExtendedQuery implements AnalyzedQuery {

	public final static ExtendedQuery MATCH_ALL = new ExtendedQuery(MatchAllQuery.INSTANCE);

	@NonNull
	private final AnalyzedQuery	searchQuery;

	@NonNull
	private final List<QueryStringTerm> filters;

	// Coming Soon: boosted query

	public ExtendedQuery(AnalyzedQuery searchQuery) {
		this.searchQuery = searchQuery;
		filters = Collections.emptyList();
	}

	public boolean isEmpty() {
		return searchQuery.getTermCount() == 0 && filters.size() == 0;
	}

	@Override
	public List<String> getInputTerms() {
		return searchQuery.getInputTerms();
	}

	@Override
	public String toQueryString() {
		StringBuilder queryStringBuilder = new StringBuilder();
		queryStringBuilder.append(searchQuery.toQueryString());
		if (filters.size() > 0) {
			queryStringBuilder.append(' ').append(QueryStringUtil.buildQueryString(filters, " "));
		}
		return queryStringBuilder.toString();
	}

	@Override
	public void accept(QueryTermVisitor visitor) {
		searchQuery.accept(visitor);
		filters.forEach(visitor::visitTerm);
	}
}
