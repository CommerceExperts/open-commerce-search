package de.cxp.ocs.smartsuggest.util;

import static de.cxp.ocs.smartsuggest.querysuggester.lucene.LuceneQuerySuggester.SHARPENED_GROUP_NAME;
import static de.cxp.ocs.smartsuggest.spi.CommonPayloadFields.PAYLOAD_GROUPMATCH_KEY;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collector;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.Lookup.LookupResult;

import com.google.common.collect.Comparators;

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
	 * Returns the {@code Comparator} that orders suggestion according to
	 * their common prefix to the given 'term'. Queries with a longer common
	 * prefix are preferred.
	 * </p>
	 * 
	 * @param locale
	 *        the locale of the client. Used to load the proper stopwords
	 * @param term
	 *        the term for which to get suggestions
	 * @return the {@code Comparator}
	 */
	public static Comparator<Suggestion> getCommonPrefixComparator(Locale locale, String term) {
		return _getCommonPrefixComparator(locale, term, Suggestion::getLabel);
	}

	private static Comparator<Lookup.LookupResult> getRawCommonPrefixComparator(Locale locale, String term) {
		return _getCommonPrefixComparator(locale, term, r -> r.key.toString());
	}

	private static <T> Comparator<T> _getCommonPrefixComparator(Locale locale, String term, Function<T, String> toStringMethod) {
		return (s1, s2) -> {
			String _term = term.toLowerCase(locale);
			String s1Label = toStringMethod.apply(s1).toLowerCase(locale);
			String s2Label = toStringMethod.apply(s2).toLowerCase(locale);

			int s1CommonPrefix = getCommonPrefixLength(s1Label, _term);
			int s2CommonPrefix = getCommonPrefixLength(s2Label, _term);

			if (s1CommonPrefix == 0 && s2CommonPrefix == 0) {
				// XXX we could improve hat a little more by considering the
				// tokens of the term as well, but that will become costly..
				s1CommonPrefix = getMaxTokenCommonPrefixLength(_term, StringUtils.split(s1Label, ' '), 1);
				s2CommonPrefix = getMaxTokenCommonPrefixLength(_term, StringUtils.split(s2Label, ' '), 1);
			}

			// prefer longer common prefix => desc order
			return Integer.compare(s2CommonPrefix, s1CommonPrefix);
		};
	}

	private static int getMaxTokenCommonPrefixLength(String term, String[] tokens, int tokenOffset) {
		int maxPrefixLength = 0;
		for (int i = tokenOffset; i < tokens.length; i++) {
			int tokenCommonPrefixLength = getCommonPrefixLength(tokens[i], term);
			if (tokenCommonPrefixLength > maxPrefixLength) maxPrefixLength = tokenCommonPrefixLength;
		}
		return maxPrefixLength;
	}

	public static int getCommonPrefixLength(String a, String b) {
		if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0;
		int i = 0;
		for (; i < a.length() && i < b.length(); i++) {
			if (a.charAt(i) != b.charAt(i)) break;
		}
		return i;
	}

	/**
	 * <p>
	 * Returns the {@code Comparator} that orders suggestion with according to
	 * their common chars to the given 'term'. Queries with more common chars
	 * are preferred.
	 * </p>
	 * 
	 * @param locale
	 *        the locale of the client. Used to load the proper stopwords
	 * @param term
	 *        the term for which to get suggestions
	 * @return the {@code Comparator}
	 */
	public static Comparator<Suggestion> getCommonCharsComparator(Locale locale, String term) {
		return _getCommonCharsComparator(locale, term, Suggestion::getLabel);
	}

	private static Comparator<Lookup.LookupResult> getRawCommonCharsComparator(Locale locale, String term) {
		return _getCommonCharsComparator(locale, term, r -> r.key.toString());
	}

	private static <T> Comparator<T> _getCommonCharsComparator(Locale locale, String term, Function<T, String> toStringMethod) {
		return (s1, s2) -> {
			double s1CommonChars = Util.commonChars(locale, toStringMethod.apply(s1), term);
			double s2CommonChars = Util.commonChars(locale, toStringMethod.apply(s2), term);

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

	private static Comparator<? super LookupResult> getRawDescendingWeightComparator() {
		// TODO Auto-generated method stub
		return (s1, s2) -> {
			// prefer higher weight => reverse order
			return Long.compare(s2.value, s1.value);
		};
	}

	/**
	 * Collector to get the first N suggestions by this order:
	 * <ul>
	 * <li>First prefer the ones with more common prefix with the input
	 * term</li>
	 * <li>Then prefer the ones with more common chars with the input term</li>
	 * <li>Finally prefer the ones with higher weight</li>
	 * </ul>
	 * 
	 * @param topK
	 *        amount of suggestions to collect
	 * @param locale
	 *        locale
	 * @param inputTerm
	 *        term of the user
	 * @return a suggestion collector
	 */
	public static Collector<Lookup.LookupResult, ?, List<Lookup.LookupResult>> getTopKFuzzySuggestionCollector(int topK, Locale locale, String inputTerm) {
		return Comparators.least(topK,
				Util.getRawCommonPrefixComparator(locale, inputTerm)
						.thenComparing(Util.getRawCommonCharsComparator(locale, inputTerm))
						.thenComparing(Util.getRawDescendingWeightComparator()));
	}

	/**
	 * Retrieve comparator to optimally order fuzzy suggestions:
	 * <ul>
	 * <li>First prefer the ones with more common prefix with the input
	 * term</li>
	 * <li>Then prefer the ones with more common chars with the input term</li>
	 * <li>Then prefer the ones with higher weight</li>
	 * <li>Finally do natural sorting to get consistent ordering</li>
	 * </ul>
	 * 
	 * @param locale
	 * @param inputTerm
	 * @return
	 */
	public static Comparator<Suggestion> getFuzzySuggestionsComparator(Locale locale, String inputTerm) {
		return Util.getCommonPrefixComparator(locale, inputTerm)
				.thenComparing(Util.getCommonCharsComparator(locale, inputTerm))
				.thenComparing(Util.getDescendingWeightComparator())
				.thenComparing((s1, s2) -> s1.getLabel().compareTo(s2.getLabel()));
	}

	/**
	 * Retrieve comparator to order suggestions within a group:
	 * <ul>
	 * <li>First prefer the ones with higher weight</li>
	 * <li>Then prefer the ones with more common chars with the input term</li>
	 * <li>Finally do natural sorting to get consistent ordering</li>
	 * </ul>
	 * 
	 * @param locale
	 * @param inputTerm
	 * @return
	 */
	public static Comparator<Suggestion> getDefaultComparator(Locale locale, String inputTerm) {
		return Util.getDescendingWeightComparator()
				// in case of same weight, prefer the ones with more common
				// chars
				.thenComparing(Util.getCommonCharsComparator(locale, inputTerm))
				// in case of same weight and similar common chars, do natural
				// ordering
				.thenComparing((s1, s2) -> s1.getLabel().compareTo(s2.getLabel()));
	}
}
