package de.cxp.ocs.elasticsearch.query.model;

import java.util.Arrays;
import java.util.List;

import org.apache.lucene.search.BooleanClause.Occur;

import lombok.Getter;

public class AlternativeTerm implements QueryStringTerm {

	@Getter
	private final List<QueryStringTerm> alternatives;

	public AlternativeTerm(QueryStringTerm... queryStringTerms) {
		alternatives = Arrays.asList(queryStringTerms);
	}

	@Override
	public String toQueryString() {
		StringBuilder queryString = new StringBuilder('(');
		for (QueryStringTerm term : alternatives) {
			if (queryString.length() > 1) {
				queryString.append(") OR (");
			}
			queryString.append(term.toQueryString());
		}
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
