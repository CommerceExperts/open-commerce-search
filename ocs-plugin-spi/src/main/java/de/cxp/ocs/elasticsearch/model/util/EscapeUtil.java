package de.cxp.ocs.elasticsearch.model.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class EscapeUtil {

	final static char escapeChar = '\\';

	final static Set<Character> charsToEscape = new HashSet<>(Arrays.asList('+', '-', '=', '!', '(', ')', '{', '}', '[', ']', '^', '"', '~', '*', '?',
			':', escapeChar, '/'));

	public static String escapeReservedESCharacters(String value) {
		if (value == null) return null;
		if (value.isEmpty()) return value;

		StringBuilder escaped = new StringBuilder(value.length());
		char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			if (charsToEscape.contains(chars[i])) {
				escaped.append(escapeChar).append(chars[i]);
			}
			// chars that can't be escaped has to be deleted
			else if (chars[i] == '<' || chars[i] == '>') {
				continue;
			}
			// escape ||
			else if (chars[i] == '|' && i + 1 < chars.length && chars[i + 1] == '|') {
				escaped.append(escapeChar).append(chars[i]).append(chars[++i]);
			}
			// escape &&
			else if (chars[i] == '&' && i + 1 < chars.length && chars[i + 1] == '&') {
				escaped.append(escapeChar).append(chars[i]).append(chars[++i]);
			}
			else {
				escaped.append(chars[i]);
			}
		}
		return escaped.toString();
	}
}
