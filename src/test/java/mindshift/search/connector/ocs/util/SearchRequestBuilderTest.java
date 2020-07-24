package mindshift.search.connector.ocs.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import mindshift.search.connector.api.v2.models.SearchRequest;

public class SearchRequestBuilderTest {

	@Test
	public void testClone() {
		SearchRequest request = getFullyLoadedSearchRequest();
		
		SearchRequestBuilder underTest = new SearchRequestBuilder(request);
		// use same sort param, so the objects still should be equals
		SearchRequest clone = underTest.withSort(request.getSort());

		assertFalse(clone == request);
		assertEquals(request, clone);
	}
	
	@Test
	public void testChangingSortParameter() {
		SearchRequest request = getFullyLoadedSearchRequest();
		
		SearchRequestBuilder underTest = new SearchRequestBuilder(request);
		SearchRequest clone = underTest.withSort("title");

		assertTrue(clone.getSort().equals("title"));
		assertFalse(request.getSort().equals(clone.getSort()));
		assertFalse(clone == request);
	}

	private SearchRequest getFullyLoadedSearchRequest() {
		SearchRequest request = new SearchRequest();
		request.setAssortment("mind_sk_SK");
		request.setFetchsize(12);
		request.setFilters(Collections.singletonMap("price", "34-59"));
		request.setId("125264zghs");
		request.setLocale("sk");
		request.setOffset(6L);
		request.setQ("foobar");
		request.setSort("relevance");
		request.setType("product");
		request.setSubtype("what");
		return request;
	}
	
}
