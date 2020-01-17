package de.cxp.ocs.elasticsearch.query.builder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;
import de.cxp.ocs.elasticsearch.query.model.WordAssociation;
import de.cxp.ocs.util.QueryUtils;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
class PredictedQuery {

	protected long							matchCount;
	protected Map<String, QueryStringTerm>	termsUnique			= new HashMap<>();
	protected Set<QueryStringTerm>			unknownTerms		= new HashSet<>();
	protected boolean						containsAllTerms	= false;
	protected int							originalTermCount	= 0;
	private String							queryString;
	private int								correctedTermCount	= -1;

	public String getQueryString() {
		if (queryString == null) {
			queryString = QueryUtils.buildQueryString(termsUnique.values(), " ");
		}
		return queryString;
	}

	@Override
	public String toString() {
		return "'" + getQueryString() + "' predicted match count: " + matchCount
				+ "; origTermCount: " + originalTermCount;
	}

	public void setQueryString(String newQueryString) {
		queryString = newQueryString;
	}

	public int getCorrectedTermCount() {
		if (correctedTermCount == -1) {
			correctedTermCount = (int) unknownTerms.stream().filter(q -> (q instanceof WordAssociation)).count();
			correctedTermCount += (int) termsUnique.values().stream().filter(q -> (q instanceof WordAssociation))
					.count();
		}
		return correctedTermCount;
	}

}
