package de.cxp.ocs.elasticsearch.facets.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;

public class NumericFacetEntryBuilderTest {

	@Test
	public void testNoFormattingConfig() {
		FacetConfig facetConfig = new FacetConfig();
		assertEquals("< 5", new NumericFacetEntryBuilder(null, 5).setFirstEntry(true).getLabel(facetConfig));
		assertEquals("5 - 12", new NumericFacetEntryBuilder(5, 12).getLabel(facetConfig));
		assertEquals("> 12", new NumericFacetEntryBuilder(12, null).setLastEntry(true).getLabel(facetConfig));
	}
	
	@Test
	public void testWithUnit() {
		FacetConfig facetConfig = facetConfig().unit(" €").build();
		assertEquals("< 5 €", new NumericFacetEntryBuilder(null, 5).setFirstEntry(true).getLabel(facetConfig));
		assertEquals("5 € - 12 €", new NumericFacetEntryBuilder(5, 12).getLabel(facetConfig));
		assertEquals("> 12 €", new NumericFacetEntryBuilder(12, null).setLastEntry(true).getLabel(facetConfig));
	}

	@Test
	public void testWithUnitAsPrefix() {
		FacetConfig facetConfig = facetConfig().unit("£").unitAsPrefix().build();
		assertEquals("< £5", new NumericFacetEntryBuilder(null, 5).setFirstEntry(true).getLabel(facetConfig));
		assertEquals("£5 - £12", new NumericFacetEntryBuilder(5, 12).getLabel(facetConfig));
		assertEquals("> £12", new NumericFacetEntryBuilder(12, null).setLastEntry(true).getLabel(facetConfig));
	}

	@Test
	public void testWithDecimalsNoConfig() {
		FacetConfig facetConfig = new FacetConfig();
		assertEquals("< 5.25", new NumericFacetEntryBuilder(null, 5.25).setFirstEntry(true).getLabel(facetConfig));
		assertEquals("5.25 - 12.75", new NumericFacetEntryBuilder(5.25, 12.75).getLabel(facetConfig));
		assertEquals("> 12.75", new NumericFacetEntryBuilder(12.75321, null).setLastEntry(true).getLabel(facetConfig));
	}

	@Test
	public void testWithDecimalsFloorRounded() {
		FacetConfig facetConfig = facetConfig().decimals(1).roundDown().build();
		assertEquals("< 5", new NumericFacetEntryBuilder(null, 5.25).setFirstEntry(true).getLabel(facetConfig));
		assertEquals("5 - 12", new NumericFacetEntryBuilder(5.25, 12.75).getLabel(facetConfig));
		assertEquals("> 12", new NumericFacetEntryBuilder(12.75321, null).setLastEntry(true).getLabel(facetConfig));
	}

	@Test
	public void testWithDecimalsCeilRounded() {
		FacetConfig facetConfig = facetConfig().decimals(1).roundUp().build();
		assertEquals("< 6", new NumericFacetEntryBuilder(null, 5.25).setFirstEntry(true).getLabel(facetConfig));
		assertEquals("6 - 13", new NumericFacetEntryBuilder(5.25, 12.75).getLabel(facetConfig));
		assertEquals("> 13", new NumericFacetEntryBuilder(12.75321, null).setLastEntry(true).getLabel(facetConfig));
	}

	@Test
	public void testWithDecimalsNaturalRounded() {
		FacetConfig facetConfig = facetConfig().decimals(1).round().build();
		assertEquals("< 5", new NumericFacetEntryBuilder(null, 5.25).setFirstEntry(true).getLabel(facetConfig));
		assertEquals("5 - 13", new NumericFacetEntryBuilder(5.25, 12.74).getLabel(facetConfig));
		assertEquals("> 13", new NumericFacetEntryBuilder(12.75321, null).setLastEntry(true).getLabel(facetConfig));
	}

	@Test
	public void testWithDecimalsInclusiveRanges() {
		FacetConfig facetConfig = facetConfig().decimals(1).upperBoundAdjustValue(-0.01).build();
		assertEquals("< 5.2", new NumericFacetEntryBuilder(null, 5.2).setFirstEntry(true).getLabel(facetConfig));
		assertEquals("5.2 - 12.7", new NumericFacetEntryBuilder(5.25, 12.75).getLabel(facetConfig));
		assertEquals("> 12.8", new NumericFacetEntryBuilder(12.75321, null).setLastEntry(true).getLabel(facetConfig));
	}

	@Test
	public void testWithDecimalsNotRounded() {
		// restricting decimals uses a number formatter that does natural rounding on a decimal level
		FacetConfig facetConfig = facetConfig().decimals(1).build();
		assertEquals("< 5.2", new NumericFacetEntryBuilder(null, 5.25).setFirstEntry(true).getLabel(facetConfig));
		assertEquals("5.2 - 12.7", new NumericFacetEntryBuilder(5.25, 12.74).getLabel(facetConfig));
		assertEquals("> 12.8", new NumericFacetEntryBuilder(12.75321, null).setLastEntry(true).getLabel(facetConfig));
	}

	@Test
	public void testWithTwoDecimalsNotRounded() {
		FacetConfig facetConfig = facetConfig().decimals(2).build();
		assertEquals("< 5.25", new NumericFacetEntryBuilder(null, 5.25).setFirstEntry(true).getLabel(facetConfig));
		assertEquals("5.25 - 12.75", new NumericFacetEntryBuilder(5.25, 12.754).getLabel(facetConfig));
		assertEquals("> 12.76", new NumericFacetEntryBuilder(12.755, null).setLastEntry(true).getLabel(facetConfig));
	}

	@Test
	public void testWithTwoDecimalsAndInclusiveRanges() {
		FacetConfig facetConfig = facetConfig().decimals(2).upperBoundAdjustValue(-0.01).noLowerBoundPrefix("<= ").build();
		assertEquals("<= 5.24", new NumericFacetEntryBuilder(null, 5.25).setFirstEntry(true).getLabel(facetConfig));
		assertEquals("5.25 - 12.75", new NumericFacetEntryBuilder(5.25, 12.76).getLabel(facetConfig));
		assertEquals("> 12.76", new NumericFacetEntryBuilder(12.76, null).setLastEntry(true).getLabel(facetConfig));
	}

	@Test
	public void testWithDecimalsInclusiveRangesWithNaturalRounding() {
		FacetConfig facetConfig = facetConfig().decimals(1).upperBoundAdjustValue(-0.01).round().build();
		assertEquals("< 5", new NumericFacetEntryBuilder(null, 5.2).setFirstEntry(true).getLabel(facetConfig));
		assertEquals("5 - 13", new NumericFacetEntryBuilder(5.25, 12.75).getLabel(facetConfig));
		assertEquals("> 13", new NumericFacetEntryBuilder(12.75321, null).setLastEntry(true).getLabel(facetConfig));
	}

	@Test
	public void testDifferentPreAndSuffixes() {
		FacetConfig facetConfig = facetConfig()
				.noLowerBoundPrefix("up to ").noLowerBoundSuffix(" (exclusive)")
				.noUpperBoundPrefix("from ").noUpperBoundSuffix(" (inclusive)")
				.intervalSeparator(" < ")
				.unit("$")
				.build();
		assertEquals("up to 5$ (exclusive)", new NumericFacetEntryBuilder(null, 5).setFirstEntry(true).getLabel(facetConfig));
		assertEquals("5$ < 12$", new NumericFacetEntryBuilder(5, 12).getLabel(facetConfig));
		assertEquals("from 12$ (inclusive)", new NumericFacetEntryBuilder(12, null).setLastEntry(true).getLabel(facetConfig));
	}

	private FacetConfigBuilder facetConfig() {
		return new FacetConfigBuilder();
	}

	private static class FacetConfigBuilder {

		Map<String, Object> metaData = new HashMap<>();

		public FacetConfigBuilder lowerBoundAdjustValue(double adjust) {
			metaData.put("lowerBoundAdjustValue", String.valueOf(adjust));
			return this;
		}

		public FacetConfigBuilder upperBoundAdjustValue(double adjust) {
			metaData.put("upperBoundAdjustValue", String.valueOf(adjust));
			return this;
		}

		public FacetConfigBuilder decimals(int decimals) {
			metaData.put("decimals", String.valueOf(decimals));
			return this;
		}

		public FacetConfigBuilder round() {
			metaData.put("round", "true");
			return this;
		}

		public FacetConfigBuilder roundUp() {
			metaData.put("round", "up");
			return this;
		}

		public FacetConfigBuilder roundDown() {
			metaData.put("round", "down");
			return this;
		}

		public FacetConfigBuilder noLowerBoundPrefix(String val) {
			metaData.put("noLowerBoundPrefix", val);
			return this;
		}

		public FacetConfigBuilder noLowerBoundSuffix(String val) {
			metaData.put("noLowerBoundSuffix", val);
			return this;
		}

		public FacetConfigBuilder intervalSeparator(String val) {
			metaData.put("intervalSeparator", val);
			return this;
		}

		public FacetConfigBuilder noUpperBoundPrefix(String val) {
			metaData.put("noUpperBoundPrefix", val);
			return this;
		}

		public FacetConfigBuilder noUpperBoundSuffix(String val) {
			metaData.put("noUpperBoundSuffix", val);
			return this;
		}

		public FacetConfigBuilder unit(String val) {
			metaData.put("unit", val);
			return this;
		}

		public FacetConfigBuilder unitAsPrefix() {
			metaData.put("unitAsPrefix", "true");
			return this;
		}

		public FacetConfig build() {
			return new FacetConfig().setMetaData(metaData);
		}
	}
}
