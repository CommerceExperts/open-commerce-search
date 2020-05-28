package io.searchhub.smartsuggest;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.searchhub.smartsuggest.querysuggester.QuerySuggester;
import io.searchhub.smartsuggest.spi.SuggestDataProvider;
import io.searchhub.smartsuggest.spi.SuggestRecord;

class QuerySuggestManagerTest {

	private static final int UPDATE_LATENCY = 2000;

	private String	testTenant1	= "test.1";
	private String	testTenant2	= "test.2";

	private RemoteSuggestDataProviderSimulation				serviceMock	= new RemoteSuggestDataProviderSimulation();
	private QuerySuggestManager querySuggestManager;

	@BeforeEach
	void beforeEach() throws Exception {
		querySuggestManager = new QuerySuggestManager(serviceMock);
		Field updateRateField = QuerySuggestManager.class.getDeclaredField("updateRate");
		updateRateField.setAccessible(true);
		updateRateField.setLong(querySuggestManager, 1);
		
		
	}

	@AfterEach
	void afterEach() {
		querySuggestManager.close();
	}

	@Test
	void basicTest() throws Exception {
		QuerySuggester suggester = querySuggestManager.getQuerySuggester(testTenant1);

		assertThat(suggester).isNotNull();

		assertThat(suggester.suggest("foo")).isEmpty();

		serviceMock.updateSuggestions(testTenant1, Arrays.asList(s("foo", "fnord"), s("bar", "bart")));

		// still return nothing until the UPDATE_LATENCY
		assertThat(suggester.suggest("foo")).isEmpty();
		assertThat(suggester.suggest("bar")).isEmpty();

		// some initialization delay + latency
		Thread.sleep(UPDATE_LATENCY);

		assertThat(suggester.suggest("foo").get(0).getSuggestions()).contains("fnord");
		assertThat(suggester.suggest("bar").get(0).getSuggestions()).contains("bart");
	}

	@Test
	void twoChannelsBasicTest() throws Exception {
		QuerySuggester suggester1 = querySuggestManager.getQuerySuggester(testTenant1);
		QuerySuggester suggester2 = querySuggestManager.getQuerySuggester(testTenant2);

		serviceMock.updateSuggestions(testTenant1, singletonList(s("foo", "fnord")));
		serviceMock.updateSuggestions(testTenant2, singletonList(s("bar", "bart")));

		// both suggesters are updated at the same time
		Thread.sleep(UPDATE_LATENCY);
		assertThat(suggester1.suggest("foo").get(0).getSuggestions()).containsExactly("fnord");
		assertThat(suggester2.suggest("bar").get(0).getSuggestions()).containsExactly("bart");

		// first suggester should not get a delay because of the second suggester
		serviceMock.updateSuggestions(testTenant1, singletonList(s("foo", "foofigher")));
		assertThat(suggester1.suggest("foo").get(0).getSuggestions()).containsExactly("fnord");
		Thread.sleep(UPDATE_LATENCY);
		assertThat(suggester1.suggest("foo").get(0).getSuggestions()).containsExactly("foofigher");
	}

	@Test
	void failingService() throws Exception {
		QuerySuggester suggester = querySuggestManager.getQuerySuggester(testTenant1);
		serviceMock.updateSuggestions(testTenant1, singletonList(s("foo", "fnord")));

		Thread.sleep(UPDATE_LATENCY); // wait shortly until loaded
		assertThat(suggester.suggest("foo").get(0).getSuggestions()).containsExactly("fnord");

		serviceMock.setAvailability(false); // slow service
		try {
			serviceMock.updateSuggestions(testTenant1, singletonList(s("foo", "foofighter")));
			fail("The network must be down!");
		} catch (IllegalStateException isx) {
			assertThat(true).isTrue();
		}
		Thread.sleep(UPDATE_LATENCY);
		// because of unavailability, the suggestions should not be updated
		assertThat(suggester.suggest("foo").get(0).getSuggestions()).containsExactly("fnord");

		serviceMock.setAvailability(true);
		serviceMock.updateSuggestions(testTenant1, singletonList(s("foo", "foofighter")));
		Thread.sleep(UPDATE_LATENCY);
		assertThat(suggester.suggest("foo").get(0).getSuggestions()).containsExactly("foofighter");
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
	void noopChannel() {
		SuggestDataProvider api = mock(SuggestDataProvider.class);

		try (QuerySuggestManager qm = new QuerySuggestManager(api)) {
			QuerySuggester noopMapper = qm.getQuerySuggester("noop");
			assertThat(noopMapper.suggest("foo")).isEmpty();
		}

		verify(api, never()).getLastDataModTime(any());
	}

	@Test
	void destroySuggesterTest() throws Exception {
		serviceMock.updateSuggestions(testTenant1, Arrays.asList(s("foo", "1")));
		QuerySuggester suggester = querySuggestManager.getQuerySuggester(testTenant1, true);
		assertThat(suggester).isNotNull();

		assertThat(suggester.suggest("foo").get(0).getSuggestions()).contains("1");
		
		querySuggestManager.destroyQuerySuggester(testTenant1);
		
		// assert that no suggestions are delivered anymore
		assertThat(suggester.suggest("foo")).isEmpty();
		
		// assert no updates are made anymore
		serviceMock.updateSuggestions(testTenant1, Arrays.asList(s("foo", "2")));
		Thread.sleep(1000);
		assertThat(suggester.suggest("foo")).isEmpty();
	}

	/**
	 * internal method that uses the QueryMapperManager in a wrong way.
	 * 
	 * @param apiStub
	 * @return
	 */
	@SuppressWarnings("resource")
	private QuerySuggester getQuerySuggester(SuggestDataProvider apiStub) {
		return new QuerySuggestManager(apiStub).getQuerySuggester(testTenant1);
	}

	private SuggestRecord s(String variant, String bestMatch) {
		SuggestRecord r = new SuggestRecord();
		r.setPrimaryText(bestMatch);
		r.setSecondaryText(variant);
		return r;
	}

}
