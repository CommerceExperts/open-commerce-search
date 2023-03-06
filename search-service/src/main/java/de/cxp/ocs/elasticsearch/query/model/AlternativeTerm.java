package de.cxp.ocs.elasticsearch.query.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.search.BooleanClause.Occur;

import de.cxp.ocs.util.ESQueryUtils;
import lombok.Getter;

public class AlternativeTerm implements QueryStringTerm {

	@Getter
	private final List<QueryStringTerm> alternatives;

	public AlternativeTerm(QueryStringTerm... queryStringTerms) {
		alternatives = Arrays.asList(queryStringTerms);
	}

	public AlternativeTerm(List<QueryStringTerm> queryStringTerms) {
		alternatives = new ArrayList<>(queryStringTerms);
	}

	@Override
	public String toQueryString() {
		StringBuilder queryString = new StringBuilder("(");
		queryString.append(ESQueryUtils.buildQueryString(alternatives, " OR "));
		return queryString.append(")").toString();
	}

	@Override
	public String getWord() {
		return alternatives.get(0).getWord();
	}

	@Override
	public Occur getOccur() {
		return Occur.SHOULD;
	}
}
