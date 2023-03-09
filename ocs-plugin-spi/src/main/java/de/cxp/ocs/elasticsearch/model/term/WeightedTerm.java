package de.cxp.ocs.elasticsearch.model.term;

import org.apache.lucene.search.BooleanClause.Occur;

import de.cxp.ocs.elasticsearch.model.util.EscapeUtil;
import lombok.*;

/**
 * A term that accepts a weight and a optional fuzzy operator.
 */
@Data
@AllArgsConstructor
@RequiredArgsConstructor
@NoArgsConstructor
public class WeightedTerm implements QueryStringTerm {

	private final String	QUOTE	= "\"";
	private final String	EMPTY	= "";

	@NonNull
	private String	rawTerm;
	private float	weight		= 1f;
	private boolean	isFuzzy		= false;
	private boolean	isQuoted	= false;
	private Occur	occur		= Occur.SHOULD;

	public WeightedTerm(String term, float weight) {
		this.rawTerm = term;
		this.weight = weight;
	}

	public WeightedTerm(String term, float weight, Occur occur) {
		this.rawTerm = term;
		this.weight = weight;
		this.occur = occur;
	}

	@Override
	public String toQueryString() {
		String optionalQuote = isQuoted ? QUOTE : EMPTY;
		return occur.toString()
				+ optionalQuote
				+ EscapeUtil.escapeReservedESCharacters(rawTerm)
				+ optionalQuote
				+ (isFuzzy ? "~" : "")
				+ (weight != 1f ? "^" + weight : "");
	}

	@Override
	public boolean isEnclosed() {
		return isQuoted;
	}

	@Override
	public String toString() {
		return toQueryString();
	}
}
