package de.cxp.ocs.elasticsearch.model.term;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * A concept that consists of several tokens that have a specific meaning in their context.
 * The separate tokens/terms/words should not be used separately.
 * <p>
 * Useful for languages that name things using several words,
 * i.e. "iron flat"(en), "fer à repasser"(fr), "plancha de ropa"(es)
 * 
 * @author Rudolf Batt
 */
@Accessors(chain = true)
public class ConceptTerm implements QueryStringTerm {

	private final List<QueryStringTerm> terms = new ArrayList<>();

	@Setter
	private float weight = 1f;

	@Setter
	@Getter
	private Occur occur = Occur.SHOULD;

	@Setter
	private boolean isQuoted = false;

	public ConceptTerm(Collection<QueryStringTerm> terms) {
		this.terms.addAll(terms);
	}

	@Override
	public String toQueryString() {
		StringBuilder queryString = new StringBuilder();
		queryString.append(occur.toString());
		queryString.append(isQuoted ? '"' : '(');
		joinTerms(queryString);
		queryString.append(isQuoted ? '"' : ')');
		if (weight != 1f) {
			queryString.append('^').append(weight);
		}
		return queryString.toString();
	}

	@Override
	public boolean isEnclosed() {
		return true;
	}

	@Override
	public String getRawTerm() {
		StringBuilder rawTerms = new StringBuilder();
		joinTerms(rawTerms);
		return rawTerms.toString();
	}

	private void joinTerms(StringBuilder queryString) {
		Iterator<QueryStringTerm> termIterator = terms.iterator();
		while (termIterator.hasNext()) {
			queryString.append(termIterator.next().toQueryString());
			if (termIterator.hasNext()) {
				queryString.append(' ');
			}
		}
	}

}
