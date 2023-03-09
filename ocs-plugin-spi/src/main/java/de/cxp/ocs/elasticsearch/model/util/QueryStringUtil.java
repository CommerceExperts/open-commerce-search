package de.cxp.ocs.elasticsearch.model.util;

import java.util.Collection;
import java.util.Iterator;

import de.cxp.ocs.elasticsearch.model.term.QueryStringTerm;

public class QueryStringUtil {

	public static String buildQueryString(Collection<QueryStringTerm> searchTerms, String joinToken) {
		return buildQueryString(searchTerms.stream().map(QueryStringTerm::toQueryString).iterator(), joinToken);
	}

	public static String buildQueryString(Iterator<String> searchTermIterator, String joinToken) {
		StringBuilder queryString = new StringBuilder();

		// untrim :)
		if (joinToken.charAt(0) != ' ') joinToken = " " + joinToken;
		if (joinToken.charAt(joinToken.length() - 1) != ' ') joinToken = joinToken + " ";

		while (searchTermIterator.hasNext()) {
			queryString.append(searchTermIterator.next());
			if (searchTermIterator.hasNext()) queryString.append(joinToken);
		}
		return queryString.toString();
	}
}
