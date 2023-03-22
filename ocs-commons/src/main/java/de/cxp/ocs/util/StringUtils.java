package de.cxp.ocs.util;

import java.io.IOException;
import java.io.Reader;

import org.apache.lucene.analysis.CharFilter;

public final class StringUtils {

	private StringUtils() {}

	/**
	 * see <a href=
	 * "https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-query-string-query.html#_reserved_characters">reserved
	 * characters at Elastic documentation</a>
	 * 
	 * @param text
	 *        that should be escaped
	 * @return that text with the reserved characters escaped
	 */
	public static String escapeReservedESCharacters(String text) {
		return org.apache.commons.lang3.StringUtils.replaceEach(text,
				new String[] { " +", " -", "=", "&&", "||", "!", "(", ")", "{", "}", "[", "]", "^", "\"", "~", "*", "?",
						":", "\\", "/", "<", ">" },
				new String[] { " \\+", " \\-", "\\=", "\\&&", "\\||", "\\!", "\\(", "\\)", "\\{", "\\}", "\\[", "\\]",
						"\\^", "\\\"", "\\~", "\\*", "\\?", "\\:", "\\\\", "\\/", "", "" });
		// the chars < and > are removed entirely
	}

	/**
	 * Converts alphabetic, numeric, and symbolic Unicode characters which are
	 * not in the first 127 ASCII characters (the "Basic Latin" Unicode block)
	 * into their ASCII equivalents, if one exists.
	 * 
	 * @param s
	 *        the string to convert.
	 * @return the converted string.
	 */
	public static String asciify(String s) {
		char[] c = s.toCharArray();
		StringBuilder b = new StringBuilder();
		for (char element : c) {
			b.append(translate(element));
		}
		return b.toString();
	}

	public static CharFilter asAsciifyCharFilter(Reader in) {
		return new AsciifyCharFilter(in);
	}

	private static class AsciifyCharFilter extends CharFilter {

		public AsciifyCharFilter(Reader in) {
			super(in);
		}

		@Override
		protected int correct(int currentOff) {
			return currentOff; // we don't change the length of the string
		}

		@Override
		public int read(char[] cbuf, int off, int len) throws IOException {
			final int charsRead = input.read(cbuf, off, len);
			if (charsRead > 0) {
				final int end = off + charsRead;
				while (off < end) {
					cbuf[off] = translate(cbuf[off]);
					off++;
				}
			}
			return charsRead;
		}

		@Override
		public int read() throws IOException {
			int ch = input.read();
			return (int) translate((char) ch);
		}

	}

	/**
	 * Translate the given unicode char in the closest ASCII representation
	 * NOTE: this function deals only with latin-1 supplement and latin-1
	 * extended code charts
	 */
	private static char translate(char c) {
		switch (c) {
			case '\u00C0':
			case '\u00C1':
			case '\u00C2':
			case '\u00C3':
				// case '\u00C4':
			case '\u00C5':
			case '\u00E0':
			case '\u00E1':
			case '\u00E2':
			case '\u00E3':
				// case '\u00E4':
			case '\u00E5':
			case '\u0100':
			case '\u0101':
			case '\u0102':
			case '\u0103':
			case '\u0104':
			case '\u0105':
				return 'a';
			case '\u00C7':
			case '\u00E7':
			case '\u0106':
			case '\u0107':
			case '\u0108':
			case '\u0109':
			case '\u010A':
			case '\u010B':
			case '\u010C':
			case '\u010D':
				return 'c';
			case '\u00D0':
			case '\u00F0':
			case '\u010E':
			case '\u010F':
			case '\u0110':
			case '\u0111':
				return 'd';
			case '\u00C8':
			case '\u00C9':
			case '\u00CA':
			case '\u00CB':
			case '\u00E8':
			case '\u00E9':
			case '\u00EA':
			case '\u00EB':
			case '\u0112':
			case '\u0113':
			case '\u0114':
			case '\u0115':
			case '\u0116':
			case '\u0117':
			case '\u0118':
			case '\u0119':
			case '\u011A':
			case '\u011B':
				return 'e';
			case '\u011C':
			case '\u011D':
			case '\u011E':
			case '\u011F':
			case '\u0120':
			case '\u0121':
			case '\u0122':
			case '\u0123':
				return 'g';
			case '\u0124':
			case '\u0125':
			case '\u0126':
			case '\u0127':
				return 'h';
			case '\u00CC':
			case '\u00CD':
			case '\u00CE':
			case '\u00CF':
			case '\u00EC':
			case '\u00ED':
			case '\u00EE':
			case '\u00EF':
			case '\u0128':
			case '\u0129':
			case '\u012A':
			case '\u012B':
			case '\u012C':
			case '\u012D':
			case '\u012E':
			case '\u012F':
			case '\u0130':
			case '\u0131':
				return 'i';
			case '\u0134':
			case '\u0135':
				return 'j';
			case '\u0136':
			case '\u0137':
			case '\u0138':
				return 'k';
			case '\u0139':
			case '\u013A':
			case '\u013B':
			case '\u013C':
			case '\u013D':
			case '\u013E':
			case '\u013F':
			case '\u0140':
			case '\u0141':
			case '\u0142':
				return 'l';
			case '\u00D1':
			case '\u00F1':
			case '\u0143':
			case '\u0144':
			case '\u0145':
			case '\u0146':
			case '\u0147':
			case '\u0148':
			case '\u0149':
			case '\u014A':
			case '\u014B':
				return 'n';
			case '\u00D2':
			case '\u00D3':
			case '\u00D4':
			case '\u00D5':
				// case '\u00D6':
			case '\u00D8':
			case '\u00F2':
			case '\u00F3':
			case '\u00F4':
			case '\u00F5':
				// case '\u00F6':
			case '\u00F8':
			case '\u014C':
			case '\u014D':
			case '\u014E':
			case '\u014F':
			case '\u0150':
			case '\u0151':
				return 'o';
			case '\u0154':
			case '\u0155':
			case '\u0156':
			case '\u0157':
			case '\u0158':
			case '\u0159':
				return 'r';
			case '\u015A':
			case '\u015B':
			case '\u015C':
			case '\u015D':
			case '\u015E':
			case '\u015F':
			case '\u0160':
			case '\u0161':
			case '\u017F':
				return 's';
			case '\u0162':
			case '\u0163':
			case '\u0164':
			case '\u0165':
			case '\u0166':
			case '\u0167':
				return 't';
			case '\u00D9':
			case '\u00DA':
			case '\u00DB':
				// case '\u00DC':
			case '\u00F9':
			case '\u00FA':
			case '\u00FB':
				// case '\u00FC':
			case '\u0168':
			case '\u0169':
			case '\u016A':
			case '\u016B':
			case '\u016C':
			case '\u016D':
			case '\u016E':
			case '\u016F':
			case '\u0170':
			case '\u0171':
			case '\u0172':
			case '\u0173':
				return 'u';
			case '\u0174':
			case '\u0175':
				return 'w';
			case '\u00DD':
			case '\u00FD':
			case '\u00FF':
			case '\u0176':
			case '\u0177':
			case '\u0178':
				return 'y';
			case '\u0179':
			case '\u017A':
			case '\u017B':
			case '\u017C':
			case '\u017D':
			case '\u017E':
				return 'z';
		}
		return c;
	}
}
