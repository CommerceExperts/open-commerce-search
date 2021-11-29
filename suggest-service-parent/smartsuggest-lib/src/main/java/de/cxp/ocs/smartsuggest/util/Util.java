package de.cxp.ocs.smartsuggest.util;

import static de.cxp.ocs.smartsuggest.querysuggester.lucene.LuceneQuerySuggester.SHARPENED_GROUP_NAME;
import static de.cxp.ocs.smartsuggest.spi.CommonPayloadFields.PAYLOAD_GROUPMATCH_KEY;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;

import de.cxp.ocs.smartsuggest.querysuggester.Suggestion;
import de.cxp.ocs.smartsuggest.querysuggester.lucene.LuceneQuerySuggester;
import lombok.NonNull;

public class Util {

	public static final String APP_NAME = "smartsuggest";

	/**
	 * Returns the common chars of an input string compared to an target string.
	 * The logic works directional, so it's important to always compare to the
	 * same target and change the input
	 * 
	 * @param locale
	 *        the locale used to any string normalizations
	 * @param input
	 *        the input string to compare against the target
	 * @param target
	 *        the target string
	 * @return a value between {@code 1} and {@code 0}, where {@code 1} means
	 *         all chars are common and {@code 0} means no chars are common.
	 */
	public static double commonChars(@NonNull Locale locale, @NonNull String input, @NonNull String target) {

		input = input.trim().toLowerCase(locale);
		target = target.trim().toLowerCase(locale);

		if (input.isEmpty() || target.isEmpty()) {
			return 0.0;
		}
		else if (input.equals(target)) {
			return 1.0;
		}

		int inputNonAlphaCharCount = 0;
		HashMap<Character, Integer> inputChars = new HashMap<>(input.length());
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);

			if (!Character.isAlphabetic(c)) {
				inputNonAlphaCharCount++;
				continue;
			}
			c = Character.toLowerCase(c);
			inputChars.compute(c, (chr, count) -> count == null ? 1 : count + 1);
		}

		int targetNonAlphaCharCount = 0;
		double commonChars = 0;
		for (int i = 0; i < target.length(); i++) {
			char c = target.charAt(i);
			if (!Character.isAlphabetic(c)) {
				targetNonAlphaCharCount++;
				continue;
			}
			Integer hasMatch = inputChars.computeIfPresent(c, (chr, count) -> count - 1);
			if (hasMatch != null && hasMatch >= 0) {
				commonChars += 1;
			}
		}
		return commonChars / Math.max(input.length() - inputNonAlphaCharCount, target.length() - targetNonAlphaCharCount);
	}

	/**
	 * <p>
	 * Returns the {@code Comparator} that orders suggestion with according to
	 * their common
	 * chars to the given 'term'. Queries with more common chars are preferred.
	 * </p>
	 * 
	 * @param locale
	 *        the locale of the client. Used to load the proper stopwords
	 * @param term
	 *        the term for which to get suggestions
	 * @return the {@code Comparator} for fuzzy suggestions.
	 */
	public static Comparator<Suggestion> getCommonCharsComparator(Locale locale, String term) {
		return (s1, s2) -> {
			final double s1CommonChars = Util.commonChars(locale, s1.getLabel(), term);
			final double s2CommonChars = Util.commonChars(locale, s2.getLabel(), term);
			// prefer more common chars => desc order
			return Double.compare(s2CommonChars, s1CommonChars);
		};
	}

	/**
	 * Returns the {@code Comparator} used to sort suggestions by their weight,
	 * where queries of the group
	 * {@value LuceneQuerySuggester#SHARPENED_GROUP_NAME} are preferred over all
	 * others (=max weight).
	 * 
	 * @return the {@code Comparator} for sharpened suggestions.
	 */
	public static Comparator<Suggestion> getDescendingWeightComparator() {
		return (s1, s2) -> {
			// sharpened queries do not have a weight. they must be prefered
			// everytime
			String matchGroup1 = s1.getPayload().get(PAYLOAD_GROUPMATCH_KEY);
			String matchGroup2 = s2.getPayload().get(PAYLOAD_GROUPMATCH_KEY);
			if (SHARPENED_GROUP_NAME.equals(matchGroup1) || SHARPENED_GROUP_NAME.equals(matchGroup2)) {
				return matchGroup1.equals(matchGroup2)
						? 0
						: (SHARPENED_GROUP_NAME.equals(matchGroup1) ? -1 : 1);
			}
			// prefer higher weight => reverse order
			return Long.compare(s2.getWeight(), s1.getWeight());
		};
	}
}
