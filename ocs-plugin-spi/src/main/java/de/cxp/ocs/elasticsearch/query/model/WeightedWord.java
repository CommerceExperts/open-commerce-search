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
	private boolean	isFuzzy			= false;
	private boolean	isQuoted		= false;
	private Occur	occur			= Occur.SHOULD;

	@Deprecated(forRemoval = true)
	private int		termFrequency	= 0;

	public WeightedWord(String word, float weight) {
		this.word = word;
		this.weight = weight;
	}

	public WeightedWord(String word, float weight, Occur occur) {
		this.word = word;
		this.weight = weight;
		this.occur = occur;
	}

	public WeightedWord(String word, float weight, boolean isFuzzy, boolean isQuoted, Occur occur) {
		this.word = word;
		this.weight = weight;
		this.occur = occur;
		this.isFuzzy = isFuzzy;
		this.isQuoted = isQuoted;
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
