package de.cxp.ocs.elasticsearch.model.query;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import de.cxp.ocs.elasticsearch.model.term.QueryStringTerm;
import de.cxp.ocs.elasticsearch.model.term.WeightedTerm;
import de.cxp.ocs.elasticsearch.model.util.QueryStringUtil;
import de.cxp.ocs.elasticsearch.model.visitor.QueryTermVisitor;
import lombok.Getter;
import lombok.NonNull;

/**
 * A term that consists of several terms that belong together.
 * 
 * @author Rudolf Batt
 */
public class MultiTermQuery implements AnalyzedQuery {

	@NonNull
	private final Collection<String> inputTerms;

	@Getter
	private final List<QueryStringTerm> terms;

	public MultiTermQuery(Collection<WeightedTerm> terms) {
		inputTerms = terms.stream().map(QueryStringTerm::getRawTerm).collect(Collectors.toList());
		this.terms = List.copyOf(terms);
	}

	public MultiTermQuery(Collection<String> keySet, Collection<QueryStringTerm> analyzedTerms) {
		this.inputTerms = keySet;
		this.terms = List.copyOf(analyzedTerms);
	}

	@Override
	public String toQueryString() {
		return QueryStringUtil.buildQueryString(terms, " ");
	}

	@Override
	public String toString() {
		return this.toQueryString();
	}

	@Override
	public List<String> getInputTerms() {
		return List.copyOf(inputTerms);
	}

	@Override
	public void accept(QueryTermVisitor visitor) {
		terms.forEach(visitor::visitTerm);
	}

}
