package de.cxp.ocs.elasticsearch.model.term;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public enum Occur {

	SHOULD(""), MUST("+"), MUST_NOT("-"), FILTER("#");

	private final String prefix;

	@Override
	public String toString() {
		return prefix;
	}

	public static Occur fromChar(char c) {
		switch (c) {
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
}
