package de.cxp.ocs.elasticsearch.query.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.lucene.search.BooleanClause.Occur;

import de.cxp.ocs.util.ESQueryUtils;
import lombok.Getter;

/**
 * A term that consists of several terms that belong together.
 */
public class MultiTermsTerm implements QueryStringTerm {

	private final Occur					occur;

	@Getter
	private final List<QueryStringTerm> terms;

	public MultiTermsTerm(QueryStringTerm... terms) {
		this.terms = Collections.unmodifiableList(Arrays.asList(terms));
		this.occur = Occur.SHOULD;
	}

	public MultiTermsTerm(List<QueryStringTerm> terms) {
		this(terms, Occur.SHOULD);
	}

	public MultiTermsTerm(List<QueryStringTerm> terms, Occur occur) {
		// ensure it can't be modified by it's original input list and also not when fetched via getter
		this.terms = Collections.unmodifiableList(new ArrayList<>(terms));
		this.occur = occur;
	}
	
	@Override
	public String toQueryString() {
		StringBuilder queryString = new StringBuilder();
		queryString.append(occur.toString());
		queryString.append('(');
		queryString.append(ESQueryUtils.buildQueryString(terms, " "));
		queryString.append(')');
		return queryString.toString();
	}


	@Override
	public String getWord() {
		// XXX this is not correct and might lead to errors if used for shingles
		return ESQueryUtils.buildQueryString(terms, " ");
	}

	@Override
	public Occur getOccur() {
		return occur;
	}

	@Override
	public String toString() {
		return this.toQueryString();
	}

}
