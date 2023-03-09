package de.cxp.ocs.elasticsearch.model.term;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.lucene.search.BooleanClause.Occur;

import lombok.*;

/**
 * A term that is associated with other terms (e.g. synonyms). They all will be
 * searched together each one as an optional replacement for the actual word.
 * 
 * @author Rudolf Batt
 */
@AllArgsConstructor
@RequiredArgsConstructor
@NoArgsConstructor
public class AssociatedTerm implements QueryStringTerm {

	@Getter
	@NonNull
	private QueryStringTerm	mainTerm;

	@Getter
	private Occur	occur	= Occur.SHOULD;

	@Getter
	private Map<String, QueryStringTerm> relatedTerms = new LinkedHashMap<>();

	public AssociatedTerm(QueryStringTerm term1, QueryStringTerm term2) {
		this.mainTerm = term1;
		relatedTerms.put(term2.getRawTerm(), term2);
	}

	public AssociatedTerm(QueryStringTerm term, Collection<QueryStringTerm> values) {
		this.mainTerm = term;
		values.forEach(v -> relatedTerms.put(v.getRawTerm(), v));
	}

	public AssociatedTerm(QueryStringTerm mainTerm, Occur occur, Collection<QueryStringTerm> values) {
		this(mainTerm, values);
		this.occur = occur;
	}

	public void putOrUpdate(QueryStringTerm associatedTerm) {
		relatedTerms.compute(associatedTerm.getRawTerm(),
				(k, existingWord) -> {
					if (existingWord == null)
						return associatedTerm;
					else if (associatedTerm instanceof WeightedTerm) {
						if (existingWord instanceof WeightedTerm)
							return ((WeightedTerm) existingWord).getWeight() < ((WeightedTerm) associatedTerm).getWeight() ? associatedTerm : existingWord;
						else
							return (((WeightedTerm) associatedTerm).getWeight() > 1f) ? associatedTerm : existingWord;
					}
					else
						return associatedTerm;
				});
	}

	/**
	 * builds a query like
	 * 
	 * <pre>
	 * (a OR "aa"^0.8 OR "aaa"^0.7)
	 * </pre>
	 * 
	 * using the related words. Related words are always quoted to prevent
	 * analysis.
	 */
	@Override
	public String toQueryString() {
		StringBuilder queryStringBuilder = new StringBuilder(occur.toString())
				.append("(");

		appendTerm(mainTerm, queryStringBuilder);

		for (QueryStringTerm relatedWord : relatedTerms.values()) {
			queryStringBuilder.append(" OR ");
			appendTerm(relatedWord, queryStringBuilder);
		}
		return queryStringBuilder.append(")").toString();
	}

	@Override
	public boolean isEnclosed() {
		return true;
	}

	private void appendTerm(QueryStringTerm term, StringBuilder queryStringBuilder) {
		// only put brakets around it if is a non-quoted multi-term query
		String termQueryString = term.toQueryString();
		boolean hasWhitespace = termQueryString.indexOf(' ') >= 0;
		if (hasWhitespace && !term.isEnclosed()) queryStringBuilder.append("(");
		queryStringBuilder.append(termQueryString);
		if (hasWhitespace && !term.isEnclosed()) queryStringBuilder.append(")");
	}

	@Override
	public String getRawTerm() {
		return mainTerm.getRawTerm();
	}

	@Override
	public String toString() {
		return toQueryString();
	}

}
