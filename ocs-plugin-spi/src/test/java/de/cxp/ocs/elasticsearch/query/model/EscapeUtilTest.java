package de.cxp.ocs.elasticsearch.query.model;

import static de.cxp.ocs.elasticsearch.query.model.EscapeUtil.escapeReservedESCharacters;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class EscapeUtilTest {

	@Test
	public void nullString() {
		assertNull(escapeReservedESCharacters(null));
	}

	@Test
	public void emptyString() {
		assertEquals("", escapeReservedESCharacters(""));
	}

	@Test
	public void normalString() {
		assertEquals("nothing special", escapeReservedESCharacters("nothing special"));
	}

	@Test
	public void singleEscapableCharInString() {
		for (char c : EscapeUtil.charsToEscape) {
			assertEquals("foo \\" + Character.toString(c) + " bar",
					escapeReservedESCharacters("foo " + Character.toString(c) + " bar"));
		}
	}

	@Test
	public void stringWithAnd() {
		assertEquals("golden \\&& apple", escapeReservedESCharacters("golden && apple"));
	}

	@Test
	public void stringWithOr() {
		assertEquals("orange \\|| apple", escapeReservedESCharacters("orange || apple"));
	}

	@Test
	public void escapeAtStart() {
		assertEquals("\\+apple", escapeReservedESCharacters("+apple"));
	}

	@Test
	public void escapeAtEnd() {
		assertEquals("apple\\~", escapeReservedESCharacters("apple~"));
	}

	@Test
	public void escapesEverywhere() {
		assertEquals("\\+my \\\"golden apple\\\"\\^3 \\&& \\!your\\~ \\(mad\\-orange\\) \\= \\{a  b\\} \\* \\[c\\/d\\]  x\\\\y\\?",
				escapeReservedESCharacters("+my \"golden apple\"^3 && !your~ (mad-orange) = {a > b} * [c/d] < x\\y?"));
	}
}
