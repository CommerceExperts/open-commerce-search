package de.cxp.ocs.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import de.cxp.ocs.util.TraceOptions.TraceFlag;

public class TraceOptionsTest {

	@Test
	public void testNoValidParam() {
		TraceOptions[] noOpts = new TraceOptions[] {
				TraceOptions.parse(""),
				TraceOptions.OFF,
				TraceOptions.parse(null),
				TraceOptions.parse("foo"),
				TraceOptions.parse("foo"),
				TraceOptions.parse("Request+EsQuery")
		};
		for (int i = 0; i < noOpts.length; i++) {
			for (TraceFlag f : TraceFlag.values()) {
				assertFalse(noOpts[i].isSet(f), "unexpected flag set in opts " + i + ": " + f);
			}
		}
	}

	@Test
	public void testLowercaseParam() {
		assertTrue(TraceOptions.parse("request").isSet(TraceFlag.Request));
		assertFalse(TraceOptions.parse("request,esQuery").isSet(TraceFlag.EsQuery));

		assertTrue(TraceOptions.parse("request,esQuery").isSet(TraceFlag.Request));
		assertTrue(TraceOptions.parse("request,esQuery").isSet(TraceFlag.EsQuery));
	}

	@Test
	public void testStandardParam() {
		assertTrue(TraceOptions.parse("Request").isSet(TraceFlag.Request));
		assertFalse(TraceOptions.parse("EsQuery, Request").isSet(TraceFlag.EsQuery));

		assertTrue(TraceOptions.parse("EsQuery,Request").isSet(TraceFlag.Request));
		assertTrue(TraceOptions.parse("EsQuery,Request").isSet(TraceFlag.EsQuery));
	}
}
