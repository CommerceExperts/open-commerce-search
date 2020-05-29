package de.cxp.ocs.smartsuggest.util;

import java.util.HashMap;
import java.util.Locale;

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
		return commonChars / Math.max(input.length()-inputNonAlphaCharCount, target.length()-targetNonAlphaCharCount);
	}
}
