package de.cxp.ocs.smartsuggest.spi;

import static java.util.Collections.emptySet;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.*;

import org.junit.jupiter.api.Test;

import de.cxp.ocs.smartsuggest.querysuggester.Suggestion;
import de.cxp.ocs.smartsuggest.util.FakeSuggestDataProvider;
import de.cxp.ocs.smartsuggest.util.FakeSuggester;

public class MergingSuggestDataProviderTest {

	private MergingSuggestDataProvider underTest;

	@Test
	public void withSingleProvider() {
		underTest = new MergingSuggestDataProvider(
				Arrays.asList(
						new FakeSuggestDataProvider().putData("index1",
								getSuggestData("keywords",
										simpleSuggestRecord("test 1"),
										simpleSuggestRecord("test 2")))));

		SuggestData result = underTest.loadData("index1");
		assertEquals("merged", result.getType());
		assertEquals(2, result.getSuggestRecords().size());
	}

	@Test
	public void withTwoProvider() {
		underTest = new MergingSuggestDataProvider(
				Arrays.asList(
						new FakeSuggestDataProvider().putData("index1",
								getSuggestData("keywords",
										simpleSuggestRecord("test 1"),
										simpleSuggestRecord("test 2"))),
						new FakeSuggestDataProvider().putData("index1",
								getSuggestData("brand",
										simpleSuggestRecord("test 3"),
										simpleSuggestRecord("test 4")))));

		SuggestData dataResult = underTest.loadData("index1");
		assertEquals("merged", dataResult.getType());
		assertEquals(4, dataResult.getSuggestRecords().size());

		FakeSuggester suggester = new FakeSuggester(dataResult.getSuggestRecords().toArray(new SuggestRecord[0]));
		List<Suggestion> suggestResult = suggester.suggest("t", 10, Collections.singleton("brand"));
		assertEquals(2, suggestResult.size());
		assertEquals("test 3", suggestResult.get(0).getLabel());
		assertEquals("test 4", suggestResult.get(1).getLabel());
	}

	@Test
	public void withTaggedDataProvided() {
		underTest = new MergingSuggestDataProvider(
				Arrays.asList(
						new FakeSuggestDataProvider().putData("index1",
								getSuggestData("type1",
										taggedSuggestRecord("test 1", "tag A"),
										taggedSuggestRecord("test 2", "tag B"))),
						new FakeSuggestDataProvider().putData("index1",
								getSuggestData("brand",
										taggedSuggestRecord("test 3", "tag A"),
										taggedSuggestRecord("test 4", "tag B")))));

		SuggestData dataResult = underTest.loadData("index1");
		assertEquals("merged", dataResult.getType());
		assertEquals(4, dataResult.getSuggestRecords().size());

		FakeSuggester suggester = new FakeSuggester(dataResult.getSuggestRecords().toArray(new SuggestRecord[0]));
		List<Suggestion> suggestResult = suggester.suggest("t", 10, Collections.singleton("tag B"));
		assertEquals(2, suggestResult.size());
		assertEquals("test 2", suggestResult.get(0).getLabel());
		assertEquals("test 4", suggestResult.get(1).getLabel());
	}

	private SuggestData getSuggestData(String type, SuggestRecord... records) {
		return new SuggestData(type, Locale.ROOT, emptySet(), Arrays.asList(records), System.currentTimeMillis());
	}

	private SuggestRecord simpleSuggestRecord(String primaryQuery) {
		SuggestRecord suggestRecord = new SuggestRecord();
		suggestRecord.setPrimaryText(primaryQuery);
		return suggestRecord;
	}

	private SuggestRecord taggedSuggestRecord(String primaryQuery, String tag) {
		SuggestRecord suggestRecord = simpleSuggestRecord(primaryQuery);
		suggestRecord.setTags(Collections.singleton(tag));
		return suggestRecord;
	}
}
