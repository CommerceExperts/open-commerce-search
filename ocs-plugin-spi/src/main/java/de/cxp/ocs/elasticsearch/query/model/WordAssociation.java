package de.cxp.ocs.elasticsearch.query.model;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.lucene.search.BooleanClause.Occur;

import lombok.*;

/**
 * A term that is associated with other terms (e.g. synonyms). They all will be
 * searched together each one as an optional replacement for the actual word.
 */
@Data
@AllArgsConstructor
@RequiredArgsConstructor
@NoArgsConstructor
public class WordAssociation implements QueryStringTerm {

	@NonNull
	private String	originalWord;
	private Occur	occur	= Occur.MUST;

	private Map<String, QueryStringTerm> relatedWords = new LinkedHashMap<>();

	public WordAssociation(String word, Collection<WeightedWord> values) {
		originalWord = word;
		values.forEach(v -> relatedWords.put(v.getWord(), v));
	}

	public WordAssociation(String word, Occur occur, Collection<WeightedWord> values) {
		this(word, values);
		this.occur = occur;
	}

	public void putOrUpdate(QueryStringTerm newWord) {
		relatedWords.compute(newWord.getWord(),
				(k, existingWord) -> {
					if (existingWord == null)
						return newWord;
					else if (newWord instanceof WeightedWord) {
						if (existingWord instanceof WeightedWord)
							return ((WeightedWord) existingWord).getWeight() < ((WeightedWord) newWord).getWeight() ? newWord : existingWord;
						else
							return (((WeightedWord) newWord).getWeight() > 1f) ? newWord : existingWord;
					}
					else
						return existingWord;
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
		boolean multiTermWord = originalWord.indexOf(' ') >= 0;

		StringBuilder queryString = new StringBuilder(occur.toString())
				.append("(");

		if (multiTermWord) queryString.append("(");
		queryString.append(EscapeUtil.escapeReservedESCharacters(originalWord));
		if (multiTermWord) queryString.append(")");

		for (QueryStringTerm relatedWord : relatedWords.values()) {
			queryString.append(" OR ");

			String relatedQuery = relatedWord.toQueryString();
			// only put brakets around it if is a non-quoted multi-term query
			if (relatedQuery.indexOf(' ') > 0 && !isQuoted(relatedWord)) {
				queryString.append('(').append(relatedQuery).append(')');
			}
			else {
				queryString.append(relatedQuery);
			}
		}
		return queryString.append(")").toString();
	}

	private boolean isQuoted(QueryStringTerm relatedWord) {
		return relatedWord instanceof WeightedWord && ((WeightedWord) relatedWord).isQuoted()
				|| isQuoted(relatedWord.toQueryString());
	}

	private boolean isQuoted(String queryString) {
		return queryString.length() >= 2 && queryString.indexOf(0) == '"' && queryString.indexOf(queryString.length() - 1) == '"';
	}

	@Override
	public String getWord() {
		return originalWord;
	}

	@Override
	public String toString() {
		return toQueryString();
	}

}
