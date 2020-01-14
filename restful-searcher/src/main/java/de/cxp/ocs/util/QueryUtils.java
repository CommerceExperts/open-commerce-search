package de.cxp.ocs.util;

import java.util.Collection;
import java.util.Iterator;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;
import de.cxp.ocs.elasticsearch.query.model.WordAssociation;

public class QueryUtils {

	public static String buildQueryString(Collection<QueryStringTerm> searchTerms, String joinToken) {
		StringBuilder queryString = new StringBuilder();
		Iterator<QueryStringTerm> searchTermIterator = searchTerms.iterator();

		// untrim :)
		if (joinToken.charAt(0) != ' ') joinToken = " " + joinToken;
		if (joinToken.charAt(joinToken.length() - 1) != ' ') joinToken = joinToken + " ";

		while (searchTermIterator.hasNext()) {
			queryString.append(searchTermIterator.next().toQueryString());
			if (searchTermIterator.hasNext()) queryString.append(joinToken);
		}
		return queryString.toString();
	}

	public static String getQueryLabel(Collection<QueryStringTerm> termsUnique) {
		StringBuilder queryLabel = new StringBuilder();
		for (QueryStringTerm qst : termsUnique) {
			queryLabel.append(' ');
			if (qst instanceof WordAssociation) {
				queryLabel.append(getFuzzyTermLabel((WordAssociation) qst));
			}
			else {
				queryLabel.append(qst.getWord());
			}
		}
		return queryLabel.toString().trim();
	}

	public static String getFuzzyTermLabel(WordAssociation correctedWord) {
		if (correctedWord.getRelatedWords().size() == 0) return correctedWord.getOriginalWord();
		StringBuilder fuzzyTermNotation = new StringBuilder("~")
				.append(correctedWord.getOriginalWord())
				.append("=(");
		correctedWord.getRelatedWords().keySet().forEach(rw -> fuzzyTermNotation.append(rw).append('/'));
		fuzzyTermNotation.setCharAt(fuzzyTermNotation.length() - 1, ')');
		return fuzzyTermNotation.toString();
	}

	/**
	 * Make sure both queries are combined as a boolean query with must-clauses.
	 * If one of them already is a boolean query, the other one will be appended
	 * to it.
	 * 
	 * @param q1
	 * @param q2
	 * @return
	 */
	public static QueryBuilder mergeQueries(QueryBuilder q1, QueryBuilder q2) {
		if (q1 == null) {
			return q2;
		}
		else if (q2 == null) {
			return q1;
		}
		else if (q1 instanceof BoolQueryBuilder) {
			((BoolQueryBuilder) q1).must(q2);
			return q1;
		}
		else if (q2 instanceof BoolQueryBuilder) {
			((BoolQueryBuilder) q2).must(q1);
			return q2;
		}
		else {
			return QueryBuilders.boolQuery()
					.must(q1)
					.must(q2);
		}
	}
}
