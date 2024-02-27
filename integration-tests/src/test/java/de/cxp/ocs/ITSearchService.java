package de.cxp.ocs;

import static de.cxp.ocs.OCSStack.getImportClient;
import static de.cxp.ocs.OCSStack.getSearchClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import de.cxp.ocs.model.index.Attribute;
import de.cxp.ocs.model.params.SearchQuery;
import de.cxp.ocs.model.result.*;

@ExtendWith({ OCSStack.class })
public class ITSearchService {

	private final static String indexName = "searcher_test";

	@BeforeAll
	public static void prepareData() throws Exception {
		assert new DataIndexer(getImportClient()).indexTestData(indexName);
	}

	@Test
	public void testStandardSearch() throws Exception {
		SearchResult searchResult = getSearchClient().search(indexName, new SearchQuery().setQ("bike"), Collections.emptyMap());
		assertThat(searchResult.inputURI).contains("q=bike");
		assertThat(searchResult.slices.size()).isEqualTo(1);
		assertThat(searchResult.tookInMillis).isGreaterThan(0);
		assertThat(searchResult.sortOptions)
				.anyMatch(s -> s.getField().equals("title") && s.getSortOrder().equals(SortOrder.ASC))
				.anyMatch(s -> s.getField().equals("title") && s.getSortOrder().equals(SortOrder.DESC));

		SearchResultSlice mainSlice = searchResult.slices.get(0);
		assertThat(mainSlice.facets)
				.anyMatch(f -> f.getFieldName().equals("brand"))
				.anyMatch(f -> f.getFieldName().equals("brand") && f.getEntries().stream().anyMatch(fe -> fe.key.equals("Barfoo")))

				.anyMatch(f -> f.getFieldName().equals("category"))
				.anyMatch(f -> f.getFieldName().equals("category") && f.getEntries().get(0).key.equals("Sport"))
				.anyMatch(f -> f.getFieldName().equals("category") && f.getEntries().get(0) instanceof HierarchialFacetEntry
						&& ((HierarchialFacetEntry) f.getEntries().get(0)).children.size() > 0);

		assertThat(mainSlice.hits)
				.anyMatch(hit -> hit.getDocument().id.equals("001"))
				.anyMatch(hit -> hit.getDocument().id.equals("002"))
				.anyMatch(hit -> hit.getDocument().id.equals("003"));
	}

	@Test
	public void testVariantPicking() throws Exception {
		SearchResult searchResult1 = getSearchClient().search(indexName, new SearchQuery().setQ("striped"), Collections.singletonMap("color", "black and yellow"));

		assertThat(searchResult1.slices.get(0).hits)
				.anyMatch(hit -> hit.getDocument().id.equals("004"))
				.anyMatch(hit -> hit.getDocument().id.equals("006"))
				.anyMatch(hit -> hit.getDocument().id.equals("006") && hit.getDocument().data.get("size") == null);

		SearchResult searchResult2 = getSearchClient().search(indexName, new SearchQuery().setQ("striped"), Collections.singletonMap("size", "31"));
		assertThat(searchResult2.slices.get(0).hits)
				.noneMatch(hit -> hit.getDocument().id.equals("004"))
				.anyMatch(hit -> hit.getDocument().id.equals("006"))
				.anyMatch(hit -> hit.getDocument().id.equals("006")
						&& ("31".equals(hit.getDocument().data.get("size")) || "31".equals(((Attribute) hit.getDocument().data.get("size")).getValue())));
	}

	@Test
	public void testVariant_PickIfDrilledDown_ForMultiSelectAttribute() throws Exception {
		{
			SearchResult searchResult = getSearchClient().search(indexName, new SearchQuery().setQ("skirt"), Collections.emptyMap());

			Optional<ResultHit> opt_hit005 = searchResult.slices.get(0).hits.stream().filter(hit -> hit.getDocument().id.equals("005")).findFirst();
			assertTrue(opt_hit005.isPresent());
			ResultHit hit005 = opt_hit005.orElse(null);

			// attributes of main product
			assertThat(hit005.getDocument().data.get("color")).isNull();
			assertThat(hit005.getDocument().data.get("price")).isEqualTo(25.5);
		}

		{
			SearchResult searchResult = getSearchClient().search(indexName, new SearchQuery().setQ("skirt"), Collections.singletonMap("color", "black"));
			Optional<ResultHit> opt_hit005 = searchResult.slices.get(0).hits.stream().filter(hit -> hit.getDocument().id.equals("005")).findFirst();
			assertTrue(opt_hit005.isPresent());
			ResultHit hit005 = opt_hit005.orElse(null);

			assertThat(hit005.getDocument().data.get("color")).asInstanceOf(type(Attribute.class)).extracting("value").isEqualTo("black");
			assertThat(hit005.getDocument().data.get("price")).isEqualTo(25.5);
		}
	}

	@Test
	public void testVariant_PickAlways_ForMultiSelectAttribute() throws Exception {
		String searchTenant2 = indexName + "_2";

		{
			SearchResult searchResult = getSearchClient().search(searchTenant2, new SearchQuery().setQ("skirt"), Collections.emptyMap());

			Optional<ResultHit> opt_hit005 = searchResult.slices.get(0).hits.stream().filter(hit -> hit.getDocument().id.equals("005")).findFirst();
			assertTrue(opt_hit005.isPresent());
			ResultHit hit005 = opt_hit005.orElse(null);

			assertThat(hit005.getDocument().data.get("color")).asInstanceOf(type(Attribute.class)).extracting("value").isEqualTo("pink");
			assertThat(hit005.getDocument().data.get("price")).isEqualTo(20.0);
		}

		{
			SearchResult searchResult = getSearchClient().search(searchTenant2, new SearchQuery().setQ("skirt"), Collections.singletonMap("color", "black"));
			Optional<ResultHit> opt_hit005 = searchResult.slices.get(0).hits.stream().filter(hit -> hit.getDocument().id.equals("005")).findFirst();
			assertTrue(opt_hit005.isPresent());
			ResultHit hit005 = opt_hit005.orElse(null);

			assertThat(hit005.getDocument().data.get("color")).asInstanceOf(type(Attribute.class)).extracting("value").isEqualTo("black");
			assertThat(hit005.getDocument().data.get("price")).isEqualTo(25.5);
		}
	}

	@Test
	public void testFacetlessFilterSearch() throws Exception {
		// negated filter
		SearchResult searchResult1 = getSearchClient().search(indexName, new SearchQuery(), Collections.singletonMap("stock", "!0"));
		assertThat(searchResult1.slices.get(0).hits)
				.noneMatch(hit -> hit.getDocument().id.equals("005"))
				.noneMatch(hit -> hit.getDocument().id.equals("006"));

		// with query
		SearchResult searchResult2 = getSearchClient().search(indexName, new SearchQuery().setQ("Apparel"), Collections.singletonMap("stock", "0"));
		assertThat(searchResult2.slices.get(0).hits)
				.anyMatch(hit -> hit.getDocument().id.equals("005"))
				.anyMatch(hit -> hit.getDocument().id.equals("006"));

		// with range filter
		SearchResult searchResult3 = getSearchClient().search(indexName, new SearchQuery().setQ("Apparel"), Collections.singletonMap("stock", "1-1000"));
		assertTrue(searchResult3.slices.get(0).matchCount > 0L);
		assertThat(searchResult3.slices.get(0).hits)
				.noneMatch(hit -> hit.getDocument().id.equals("005"))
				.noneMatch(hit -> hit.getDocument().id.equals("006"));
	}
}
