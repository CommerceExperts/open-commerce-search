package de.cxp.ocs.elasticsearch.query.model;

import de.cxp.ocs.util.StringUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * A term that accepts a weight and a optional fuzzy operator.
 */
@Data
@AllArgsConstructor
@RequiredArgsConstructor
@NoArgsConstructor
public class WeightedWord implements QueryStringTerm {

	@NonNull
	private String	word;
	private float	weight			= 1f;
	// TODO: remove because unused?
	private int		termFrequency	= -1;
	private boolean	isFuzzy			= false;

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
		return StringUtils.escapeReservedESCharacters(word)
				+ (isFuzzy ? "~" : "")
				+ (weight != 1f ? "^" + weight : "");
	}

	@Override
	public String toString() {
		return word;
	}
}
