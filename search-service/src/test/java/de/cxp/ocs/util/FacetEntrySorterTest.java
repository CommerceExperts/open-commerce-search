package de.cxp.ocs.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Iterator;

import org.junit.jupiter.api.Test;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig.ValueOrder;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.FacetEntry;

public class FacetEntrySorterTest {

	private final Facet testFacet = new Facet("random")
			.addEntry(new FacetEntry("28 z", "z28", 82, "", false))
			.addEntry(new FacetEntry("2 B", "b2", 12, "", false))
			.addEntry(new FacetEntry("Alpha", "a", 1, "", false))
			.addEntry(new FacetEntry("Beta", "a", 1, "", false))
			.addEntry(new FacetEntry("1 A", "a1", 11, "", false))
			.addEntry(new FacetEntry("1.2 A", "a12", 12, "", false))
			.addEntry(new FacetEntry("4 D", "d4", 41, "", false));

	@Test
	public void testNoopSorter() {
		FacetEntry firstEntry = testFacet.getEntries().get(0);
		FacetEntrySorter.NOOP.sort(testFacet);
		assertEquals(firstEntry, testFacet.getEntries().get(0));
	}

	@Test
	public void testNoopSorterInit() {
		FacetEntry firstEntry = testFacet.getEntries().get(0);
		FacetEntrySorter.of(null, 12).sort(testFacet);
		assertEquals(firstEntry, testFacet.getEntries().get(0));
	}

	@Test
	public void testAlphaNumSorter() {
		FacetEntrySorter.of(ValueOrder.ALPHANUM_ASC, 12).sort(testFacet);
		assertEquals("1 A", testFacet.getEntries().get(0).key);
	}

	@Test
	public void testAlphaNumDescSorter() {
		FacetEntrySorter.of(ValueOrder.ALPHANUM_DESC, 12).sort(testFacet);
		assertEquals("Beta", testFacet.getEntries().get(0).key);
	}

	@Test
	public void testHumanNumSorter() {
		FacetEntrySorter.of(ValueOrder.HUMAN_NUMERIC_ASC, 12).sort(testFacet);
		Iterator<FacetEntry> facetEntryIterator = testFacet.getEntries().iterator();
		assertEquals("1 A", facetEntryIterator.next().getKey());
		assertEquals("1.2 A", facetEntryIterator.next().getKey());
		assertEquals("2 B", facetEntryIterator.next().getKey());
		assertEquals("4 D", facetEntryIterator.next().getKey());
		assertEquals("28 z", facetEntryIterator.next().getKey());
		assertEquals("Alpha", facetEntryIterator.next().getKey());
		assertEquals("Beta", facetEntryIterator.next().getKey());
	}
}
