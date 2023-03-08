package de.cxp.ocs.elasticsearch.model.query;

import java.util.*;
import java.util.stream.Collectors;

import de.cxp.ocs.elasticsearch.model.term.QueryStringTerm;
import de.cxp.ocs.elasticsearch.model.util.QueryStringUtil;
import de.cxp.ocs.elasticsearch.model.visitor.QueryTermVisitor;
import lombok.Getter;

/**
 * A term that consists of several terms that belong together.
 * 
 * @author Rudolf Batt
 */
public class MultiTermQuery implements AnalyzedQuery {

	@Getter
	private final List<QueryStringTerm> terms;

	public MultiTermQuery(QueryStringTerm... terms) {
		this.terms = Collections.unmodifiableList(Arrays.asList(terms));
	}

	public MultiTermQuery(Collection<QueryStringTerm> terms) {
		this.terms = Collections.unmodifiableList(new ArrayList<>(terms));
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
		return terms.stream().map(QueryStringTerm::getRawTerm).collect(Collectors.toList());
	}

	@Override
	public void accept(QueryTermVisitor visitor) {
		terms.forEach(visitor::visitTerm);
	}

}
