package de.cxp.ocs.elasticsearch.model.term;

import lombok.RequiredArgsConstructor;

/**
 * Use carefully - don't use for userinput directly.
 *
 * XXX: think about replacing it
 */
@RequiredArgsConstructor
public class RawTerm implements QueryStringTerm {

	private final String rawQueryTerm;

	@Override
	public String toQueryString() {
		return rawQueryTerm;
	}

	@Override
	public String getRawTerm() {
		return rawQueryTerm;
	}

	@Override
	public Occur getOccur() {
		if (rawQueryTerm.length() > 0) {
			return Occur.fromChar(rawQueryTerm.charAt(0));
		}
		return Occur.SHOULD;
	}

	@Override
	public boolean isEnclosed() {
		return rawQueryTerm.charAt(0) == '(' || rawQueryTerm.charAt(0) == '"';
	}

	@Override
	public String toString() {
		return toQueryString();
	}
}
