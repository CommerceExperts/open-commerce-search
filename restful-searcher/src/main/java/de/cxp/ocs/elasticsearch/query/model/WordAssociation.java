package de.cxp.ocs.elasticsearch.query.model;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
@NoArgsConstructor
public class WordAssociation implements QueryStringTerm {

	@NonNull
	private String originalWord;

	private Map<String, WeightedWord> relatedWords = new HashMap<>();

	public WordAssociation(String word, Collection<WeightedWord> values) {
		originalWord = word;
		values.forEach(v -> relatedWords.put(v.getWord(), v));
	}

	public void putOrUpdate(WeightedWord newWord) {
		relatedWords.compute(newWord.getWord(),
				(k, correctedWord) -> {
					if (correctedWord == null)
						return newWord;
					else if (correctedWord.getWeight() < newWord.getWeight())
						return newWord;
					else
						return correctedWord;
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
		StringBuilder queryString = new StringBuilder("(")
				.append(QueryStringTerm.escape(originalWord));
		for (WeightedWord relatedWord : relatedWords.values()) {
			queryString.append(" OR ").append('"').append(relatedWord.toQueryString().replace("^", "\"^"));
		}
		return queryString.append(")").toString();
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
