package de.cxp.ocs.elasticsearch.model.query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import de.cxp.ocs.elasticsearch.model.term.QueryStringTerm;
import de.cxp.ocs.elasticsearch.model.util.QueryStringUtil;
import de.cxp.ocs.elasticsearch.model.visitor.QueryTermVisitor;

/**
 * A query that consists of several different analyzed queries.
 * 
 * @author Rudolf Batt
 */
public class MultiVariantQuery implements AnalyzedQuery {

	private final List<String> inputTerms = new ArrayList<>();

	private final List<AnalyzedQuery> queryVariation = new ArrayList<>();

	public MultiVariantQuery(Collection<QueryStringTerm> inputTerms) {
		inputTerms.forEach(term -> this.inputTerms.add(term.getRawTerm()));
	}

	public MultiVariantQuery(Collection<QueryStringTerm> inputTerms, List<AnalyzedQuery> queryVariation) {
		inputTerms.forEach(term -> this.inputTerms.add(term.getRawTerm()));
		this.queryVariation.addAll(queryVariation);
	}

	public MultiVariantQuery(List<String> inputTerms, List<AnalyzedQuery> queryVariation) {
		this.inputTerms.addAll(inputTerms);
		this.queryVariation.addAll(queryVariation);
	}

	public void addQueryVariant(MultiTermQuery multiTermQuery) {
		this.queryVariation.add(multiTermQuery);
	}

	@Override
	public List<String> getInputTerms() {
		return inputTerms;
	}

	@Override
	public String toQueryString() {
		if (queryVariation.isEmpty()) return "";
		return queryVariation.size() == 1 ? queryVariation.get(0).toQueryString() : buildQueryString();
	}

	private String buildQueryString() {
		return QueryStringUtil.buildQueryString(queryVariation.stream().map(this::variantQueryString).iterator(), " OR ");
	}

	private String variantQueryString(AnalyzedQuery variant) {
		String queryString = variant.toQueryString();
		if (queryString.charAt(0) == '(' && queryString.charAt(queryString.length() - 1) == ')' && !(variant instanceof MultiTermQuery)) {
			return queryString;
		}
		else {
			return "(" + queryString + ")";
		}
	}

	@Override
	public void accept(QueryTermVisitor visitor) {
		queryVariation.forEach(visitor::visitSubQuery);
	}
}
