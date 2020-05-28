package io.searchhub.smartsuggest.querysuggester;

import static java.util.Collections.emptySet;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

import lombok.Data;

@Data
public class Suggestion implements Serializable, Comparable<Suggestion> {

	private static final long serialVersionUID = 2L;

	private final Set<String>	variants;
	private final String		bestMatch;
	private final int			weight;
	private final boolean		promoted;
	private final Set<String>	contexts;

	public Suggestion(
			String bestMatch,
			Set<String> variants,
			int weight,
			boolean promoted) {
		this(bestMatch, variants, weight, promoted, emptySet());
	}

	public Suggestion(String bestMatch,
			Set<String> variants,
			int weight,
			boolean promoted,
			Set<String> contexts) {
		this.bestMatch = bestMatch;
		this.variants = variants;
		this.weight = weight;
		this.promoted = promoted;
		this.contexts = contexts;
	}

	@Deprecated
	public Suggestion(String variant, String masterQuery, int weight, boolean promoted) {
		this(masterQuery, Collections.singleton(variant), weight, promoted, emptySet());
	}

	@Deprecated
	public Suggestion(String variant, String masterQuery, int weight, boolean promoted, Set<String> context) {
		this(masterQuery, Collections.singleton(variant), weight, promoted, context);
	}

	/**
	 * Calculate a new weight based on the other Mapping details
	 *
	 * @param weight
	 *        the initial weight
	 * @param promoted
	 *        a flag indicating whether the suggestion should have higher
	 *        priority than others
	 * @param variant
	 *        the user query variant
	 * @return a weight depending on the other details
	 */
	// TODO reintroduce a proper weight calculation including correctness score
	@SuppressWarnings("unused")
	private int calculateWeight(int weight, boolean promoted, String variant) {
		int result = weight;
		if (promoted) {
			result *= 1000;
		}
		// final String[] words = variant.split(" ");
		// result *= words.length;
		return result;
	}

	@Override
	public int compareTo(Suggestion other) {
		int byWeight = Integer.compare(other.weight, weight);
		if (byWeight == 0) {
			int byPromoted = Boolean.compare(other.promoted, promoted);
			if (byPromoted == 0) {
				int byMasterQuery = bestMatch.compareTo(other.bestMatch);
				return byMasterQuery;
			}
			else {
				return byPromoted;
			}
		}
		return byWeight;
	}
}
