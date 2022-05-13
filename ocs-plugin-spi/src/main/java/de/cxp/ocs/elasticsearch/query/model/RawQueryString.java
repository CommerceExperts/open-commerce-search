package de.cxp.ocs.elasticsearch.query.model;

import org.apache.lucene.search.BooleanClause.Occur;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RawQueryString implements QueryStringTerm {

	private final String rawQueryString;

	@Override
	public String toQueryString() {
		return rawQueryString;
	}

	@Override
	public String getWord() {
		return rawQueryString;
	}

	@Override
	public Occur getOccur() {
		if (rawQueryString.length() > 0) {
			switch (rawQueryString.charAt(0)) {
				case '-':
					return Occur.MUST_NOT;
				case '+':
					return Occur.MUST;
				case '#':
					return Occur.FILTER;
				default:
					return Occur.SHOULD;
			}
		}
		return Occur.SHOULD;
	}

	@Override
	public String toString() {
		return toQueryString();
	}
}
