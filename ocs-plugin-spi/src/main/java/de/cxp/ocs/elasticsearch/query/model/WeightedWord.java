package de.cxp.ocs.elasticsearch.query.model;

import org.apache.lucene.search.BooleanClause.Occur;

import lombok.*;

/**
 * A term that accepts a weight and a optional fuzzy operator.
 */
@Data
@AllArgsConstructor
@RequiredArgsConstructor
@NoArgsConstructor
public class WeightedWord implements QueryStringTerm {

	private final String	QUOTE	= "\"";
	private final String	EMPTY	= "";

	@NonNull
	private String	word;
	private float	weight			= 1f;
	// TODO: remove because unused?
	private int		termFrequency	= -1;
	private boolean	isFuzzy			= false;
	private boolean	isQuoted		= false;
	private Occur	occur			= Occur.SHOULD;

	public WeightedWord(String word, float weight) {
		this.word = word;
		this.weight = weight;
	}

	public WeightedWord(String word, float weight, int freq) {
		this.word = word;
		this.weight = weight;
		termFrequency = freq;
	}

	@Override
	public String toQueryString() {
		String optionalQuote = isQuoted ? QUOTE : EMPTY;
		return occur.toString()
				+ optionalQuote
				+ EscapeUtil.escapeReservedESCharacters(word)
				+ optionalQuote
				+ (isFuzzy ? "~" : "")
				+ (weight != 1f ? "^" + weight : "");
	}

	@Override
	public String toString() {
		return toQueryString();
	}
}
