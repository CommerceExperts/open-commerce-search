package de.cxp.ocs.smartsuggest;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import de.cxp.ocs.smartsuggest.QuerySuggestManager.QuerySuggestManagerBuilder;
import de.cxp.ocs.smartsuggest.querysuggester.CompoundQuerySuggester;
import de.cxp.ocs.smartsuggest.querysuggester.QuerySuggester;
import de.cxp.ocs.smartsuggest.querysuggester.QuerySuggesterProxy;
import de.cxp.ocs.smartsuggest.querysuggester.Suggestion;
import de.cxp.ocs.smartsuggest.spi.SuggestDataProvider;
import de.cxp.ocs.smartsuggest.spi.SuggestRecord;

class QuerySuggestManagerTest {

	private static final int UPDATE_LATENCY = 2000;

	private String	testTenant1	= "test.1";
	private String	testTenant2	= "test.2";

	private RemoteSuggestDataProviderSimulation	serviceMock	= new RemoteSuggestDataProviderSimulation();
	private QuerySuggestManager					querySuggestManager;

	@AfterEach
	void afterEach() {
		if (querySuggestManager != null) querySuggestManager.close();
	}

	@Test
	void basicTest() throws Exception {
		serviceMock.updateSuggestions(testTenant1, Collections.emptyList());
		querySuggestManager = QuerySuggestManager.builder()
				.withSuggestDataProvider(serviceMock)
				.updateRateUnbound(1)
				.build();

		QuerySuggester suggester = querySuggestManager.getQuerySuggester(testTenant1);

		assertThat(suggester).isNotNull();

		assertThat(suggester.suggest("foo")).isEmpty();

		serviceMock.updateSuggestions(testTenant1, Arrays.asList(s("foo", "fnord"), s("bar", "bart")));

		// still return nothing until the UPDATE_LATENCY
		assertThat(suggester.suggest("foo")).isEmpty();
		assertThat(suggester.suggest("bar")).isEmpty();

		// some initialization delay + latency
		Thread.sleep(UPDATE_LATENCY);

		assertThat(suggester.suggest("foo").get(0).getLabel()).isEqualTo("fnord");
		assertThat(suggester.suggest("bar").get(0).getLabel()).isEqualTo("bart");
	}

	@Test
	void twoChannelsBasicTest() throws Exception {
		querySuggestManager = QuerySuggestManager.builder()
				.withSuggestDataProvider(serviceMock)
				.updateRateUnbound(1)
				.build();

		serviceMock.updateSuggestions(testTenant1, singletonList(s("foo", "fnord")));
		serviceMock.updateSuggestions(testTenant2, singletonList(s("bar", "bart")));

		QuerySuggester suggester1 = querySuggestManager.getQuerySuggester(testTenant1);
		QuerySuggester suggester2 = querySuggestManager.getQuerySuggester(testTenant2);

		// both suggesters are updated at the same time
		Thread.sleep(UPDATE_LATENCY);
		assertThat(suggester1.suggest("foo").get(0).getLabel()).isEqualTo("fnord");
		assertThat(suggester2.suggest("bar").get(0).getLabel()).isEqualTo("bart");

		// first suggester should not get a delay because of the second suggester
		serviceMock.updateSuggestions(testTenant1, singletonList(s("foo", "foofigher")));
		assertThat(suggester1.suggest("foo").get(0).getLabel()).isEqualTo("fnord");
		Thread.sleep(UPDATE_LATENCY);
		assertThat(suggester1.suggest("foo").get(0).getLabel()).isEqualTo("foofigher");
	}

	@Test
	void failingService() throws Exception {
		querySuggestManager = QuerySuggestManager.builder()
				.withSuggestDataProvider(serviceMock)
				.updateRateUnbound(1)
				.build();

		serviceMock.updateSuggestions(testTenant1, singletonList(s("foo", "fnord")));
		QuerySuggester suggester = querySuggestManager.getQuerySuggester(testTenant1, true);

		assertThat(suggester.suggest("foo").get(0).getLabel()).isEqualTo("fnord");

		serviceMock.setAvailability(false); // slow service
		try {
			serviceMock.updateSuggestions(testTenant1, singletonList(s("foo", "foofighter")));
			fail("The network must be down!");
		}
		catch (IllegalStateException isx) {
			assertThat(true).isTrue();
		}
		Thread.sleep(UPDATE_LATENCY);
		// because of unavailability, the suggestions should not be updated
		assertThat(suggester.suggest("foo").get(0).getLabel()).isEqualTo("fnord");

		serviceMock.setAvailability(true);
		serviceMock.updateSuggestions(testTenant1, singletonList(s("foo", "foofighter")));
		Thread.sleep(UPDATE_LATENCY);
		assertThat(suggester.suggest("foo").get(0).getLabel()).isEqualTo("foofighter");
	}

	// run this manually to test if the JVM is shutdown although the
	// QueryMapperManager is not closed properly
	public static void main(String[] args) throws Exception {
		new QuerySuggestManagerTest().testThrowAwayManager();
	}

	@Test
	void testThrowAwayManager() throws Exception {
		QuerySuggester querySuggester = getQuerySuggester(new RemoteSuggestDataProviderSimulation());

		System.gc();
		Thread.sleep(10);
		querySuggester.suggest("foo");
	}

	@Test
	void noopChannel() throws IOException {
		SuggestDataProvider api = mock(SuggestDataProvider.class);
		QuerySuggestManagerBuilder querySuggestManagerBuilder = QuerySuggestManager.builder()
				.withSuggestDataProvider(api)
				.updateRateUnbound(1);

		try (QuerySuggestManager qm = querySuggestManagerBuilder.build()) {
			QuerySuggester noopMapper = qm.getQuerySuggester("noop");
			assertThat(noopMapper.suggest("foo")).isEmpty();
		}

		verify(api, never()).getLastDataModTime(any());
	}

	@Test
	void destroySuggesterTest() throws Exception {
		querySuggestManager = QuerySuggestManager.builder()
				.withSuggestDataProvider(serviceMock)
				.updateRateUnbound(1)
				.build();

		serviceMock.updateSuggestions(testTenant1, Arrays.asList(s("foo", "1")));
		QuerySuggester suggester = querySuggestManager.getQuerySuggester(testTenant1, true);
		assertThat(suggester).isNotNull();

		assertThat(suggester.suggest("foo").get(0).getLabel()).isEqualTo("1");

		querySuggestManager.destroyQuerySuggester(testTenant1);

		// assert that no suggestions are delivered anymore
		assertThat(suggester.suggest("foo")).isEmpty();

		// assert no updates are made anymore
		serviceMock.updateSuggestions(testTenant1, Arrays.asList(s("foo", "2")));
		Thread.sleep(1000);
		assertThat(suggester.suggest("foo")).isEmpty();
	}

	@Test
	void multipleDataProviders() throws Exception {
		RemoteSuggestDataProviderSimulation dp1 = new RemoteSuggestDataProviderSimulation();
		RemoteSuggestDataProviderSimulation dp2 = new RemoteSuggestDataProviderSimulation();
		SuggestDataProvider mock = mock(SuggestDataProvider.class);

		querySuggestManager = QuerySuggestManager.builder()
				.withSuggestDataProvider(dp1)
				.withSuggestDataProvider(dp2)
				.withSuggestDataProvider(mock)
				.updateRateUnbound(1)
				.build();

		// subtest 1: only one data provider has data
		dp1.updateSuggestions("index1", Arrays.asList(new SuggestRecord("query1", "matching text", null, null, 10L)));
		QuerySuggester querySuggester1 = querySuggestManager.getQuerySuggester("index1", true);

		assertTrue(querySuggester1 instanceof QuerySuggesterProxy, "but was instanceof " + querySuggester1.getClass().getCanonicalName());
		verify(mock, times(1)).hasData("index1");

		List<Suggestion> suggestions1 = querySuggester1.suggest("text");
		assertThat(suggestions1).size().isEqualTo(1);
		assertThat(suggestions1).allMatch(s -> "query1".equals(s.getLabel())).as("query1 as label expected");

		// subtest 2: two data providers have data
		dp1.updateSuggestions("index2", Arrays.asList(new SuggestRecord("query 1.2", "arbitrary matching text", null, null, 10L)));
		dp2.updateSuggestions("index2", Arrays.asList(new SuggestRecord("query 2.2", "more matching text", null, null, 10L)));
		QuerySuggester querySuggester2 = querySuggestManager.getQuerySuggester("index2", true);

		verify(mock, times(1)).hasData("index2");
		assertTrue(querySuggester2 instanceof CompoundQuerySuggester, "but was instanceof " + querySuggester1.getClass().getCanonicalName());
		List<Suggestion> suggestions2 = querySuggester2.suggest("tex");
		assertThat(suggestions2).size().isEqualTo(2);
		assertThat(suggestions2).allMatch(s -> s.getLabel().endsWith("2"));
	}

	/**
	 * internal method that uses the QueryMapperManager in a wrong way.
	 * 
	 * @param apiStub
	 * @return
	 */
	@SuppressWarnings("resource")
	private QuerySuggester getQuerySuggester(SuggestDataProvider apiStub) {
		return QuerySuggestManager.builder()
				.withSuggestDataProvider(apiStub)
				.updateRateUnbound(1)
				.build()
				.getQuerySuggester(testTenant1);
	}

	private SuggestRecord s(String variant, String bestMatch) {
		SuggestRecord r = new SuggestRecord();
		r.setPrimaryText(bestMatch);
		r.setSecondaryText(variant);
		return r;
	}

}
