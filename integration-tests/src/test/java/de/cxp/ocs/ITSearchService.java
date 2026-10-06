package de.cxp.ocs;

import static de.cxp.ocs.OCSStack.getImportClient;
import static de.cxp.ocs.OCSStack.getSearchClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import de.cxp.ocs.model.index.Attribute;
import de.cxp.ocs.model.params.SearchQuery;
import de.cxp.ocs.model.result.*;
import de.cxp.ocs.util.DataIndexer;

@ExtendWith({ OCSStack.class })
public class ITSearchService {

	private final static String indexName = "searcher_test";

	@BeforeAll
	public static void prepareData() throws Exception {
		assertTrue(new DataIndexer(getImportClient()).indexTestData(indexName) > 0);
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

		SearchResultSlice mainSlice = searchResult.slices.getFirst();
		assertThat(mainSlice.facets)
				.anyMatch(f -> f.getFieldName().equals("brand"))
				.anyMatch(f -> f.getFieldName().equals("brand") && f.getEntries().stream().anyMatch(fe -> fe.key.equals("Barfoo")))

				.anyMatch(f -> f.getFieldName().equals("category"))
				.anyMatch(f -> f.getFieldName().equals("category") && f.getEntries().getFirst().key.equals("Sport"))
				.anyMatch(f -> f.getFieldName().equals("category") && f.getEntries().getFirst() instanceof HierarchialFacetEntry
						&& !((HierarchialFacetEntry) f.getEntries().getFirst()).children.isEmpty());

		assertThat(mainSlice.hits)
				.anyMatch(hit -> hit.getDocument().id.equals("001"))
				.anyMatch(hit -> hit.getDocument().id.equals("002"))
				.anyMatch(hit -> hit.getDocument().id.equals("003"));
	}

	@Test
	public void testDualLevelFilter() throws Exception {
		// 1. case: main-level filter
		SearchResult searchResult1 = getSearchClient().search(indexName, new SearchQuery(), Collections.singletonMap("brand", "Fluffy Unicorn"));
		assertThat(searchResult1.slices.getFirst().hits)
				.anyMatch(hit -> hit.getDocument().id.equals("005"))
				.anyMatch(hit -> hit.getDocument().id.equals("008"));

		// 2. case: main-level filter + both-level filter
		SearchResult searchResult2 = getSearchClient().search(indexName, new SearchQuery(), Map.of("brand", "Fluffy Unicorn", "price", "10-100"));
		assertThat(searchResult2.slices.getFirst().hits)
				.anyMatch(hit -> hit.getDocument().id.equals("005"))
				.anyMatch(hit -> hit.getDocument().id.equals("008"))
				// variant-selection "pick-if-drilled-down": now expect the variant that patches the according price filter
				.anyMatch(hit -> hit.getDocument().id.equals("008") && "gold".equals(((Attribute) hit.getDocument().data.get("color")).getValue()));

		// 3. case: main-level filter + 2 both level filter
		SearchResult searchResult3 = getSearchClient().search(indexName, new SearchQuery(), Map.of("brand", "Fluffy Unicorn",
				"price", "20-25", "color", "pink"));
		// we expect the one product to return that has main.price=20 and color=pink
		assertEquals(1L, searchResult3.slices.getFirst().matchCount);
		assertThat(searchResult3.slices.getFirst().hits)
				.anyMatch(hit -> hit.getDocument().id.equals("005")
						&& hit.getDocument().data.get("price").equals(20.0)
						&& "pink".equals(((Attribute) hit.getDocument().data.get("color")).getValue()));

		// 4. case: main-level filter + 2 both-level filter that should remove all results
		SearchResult searchResult4 = getSearchClient().search(indexName, new SearchQuery(), Map.of("brand", "Fluffy Unicorn",
				// there is a variant with that price, and there is a variant with that color, but not a variant that meets both attributes
				"price", "0-10", "color", "gold"));
		assertEquals(0L, searchResult4.slices.getFirst().matchCount);
	}

	@Test
	public void testVariantPicking() throws Exception {
		SearchResult searchResult1 = getSearchClient().search(indexName, new SearchQuery().setQ("striped"), Collections.singletonMap("color", "black and yellow"));

		assertThat(searchResult1.slices.getFirst().hits)
				.anyMatch(hit -> hit.getDocument().id.equals("004"))
				.anyMatch(hit -> hit.getDocument().id.equals("006"))
				.anyMatch(hit -> hit.getDocument().id.equals("006") && hit.getDocument().data.get("size") == null);

		SearchResult searchResult2 = getSearchClient().search(indexName, new SearchQuery().setQ("striped"), Collections.singletonMap("size", "31"));
		assertThat(searchResult2.slices.getFirst().hits)
				.noneMatch(hit -> hit.getDocument().id.equals("004"))
				.anyMatch(hit -> hit.getDocument().id.equals("006"))
				.anyMatch(hit -> hit.getDocument().id.equals("006")
						&& ("31".equals(hit.getDocument().data.get("size")) || "31".equals(((Attribute) hit.getDocument().data.get("size")).getValue())));
	}

	@Test
	public void testVariant_PickIfDrilledDown_ForMultiSelectAttribute() throws Exception {
		{
			SearchResult searchResult = getSearchClient().search(indexName, new SearchQuery().setQ("skirt"), Collections.emptyMap());

			Optional<ResultHit> opt_hit005 = searchResult.slices.getFirst().hits.stream().filter(hit -> hit.getDocument().id.equals("005")).findFirst();
			assertTrue(opt_hit005.isPresent());
			ResultHit hit005 = opt_hit005.orElse(null);

			// attributes of main product
			assertThat(hit005.getDocument().data.get("color")).isNull();
			assertThat(hit005.getDocument().data.get("price")).isEqualTo(25.5);
		}

		{
			SearchResult searchResult = getSearchClient().search(indexName, new SearchQuery().setQ("skirt"), Collections.singletonMap("color", "black"));
			Optional<ResultHit> opt_hit005 = searchResult.slices.getFirst().hits.stream().filter(hit -> hit.getDocument().id.equals("005")).findFirst();
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

			Optional<ResultHit> opt_hit005 = searchResult.slices.getFirst().hits.stream().filter(hit -> hit.getDocument().id.equals("005")).findFirst();
			assertTrue(opt_hit005.isPresent());
			ResultHit hit005 = opt_hit005.orElse(null);

			assertThat(hit005.getDocument().data.get("color")).asInstanceOf(type(Attribute.class)).extracting("value").isEqualTo("pink");
			assertThat(hit005.getDocument().data.get("price")).isEqualTo(20.0);
		}

		{
			SearchResult searchResult = getSearchClient().search(searchTenant2, new SearchQuery().setQ("skirt"), Collections.singletonMap("color", "black"));
			Optional<ResultHit> opt_hit005 = searchResult.slices.getFirst().hits.stream().filter(hit -> hit.getDocument().id.equals("005")).findFirst();
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
		assertThat(searchResult1.slices.getFirst().hits)
				.noneMatch(hit -> hit.getDocument().id.equals("005"))
				.noneMatch(hit -> hit.getDocument().id.equals("006"));

		// with query
		SearchResult searchResult2 = getSearchClient().search(indexName, new SearchQuery().setQ("Apparel"), Collections.singletonMap("stock", "0"));
		assertThat(searchResult2.slices.getFirst().hits)
				.anyMatch(hit -> hit.getDocument().id.equals("005"))
				.anyMatch(hit -> hit.getDocument().id.equals("006"));

		// with range filter
		SearchResult searchResult3 = getSearchClient().search(indexName, new SearchQuery().setQ("Apparel"), Collections.singletonMap("stock", "1-1000"));
		assertTrue(searchResult3.slices.getFirst().matchCount > 0L);
		assertThat(searchResult3.slices.getFirst().hits)
				.noneMatch(hit -> hit.getDocument().id.equals("005"))
				.noneMatch(hit -> hit.getDocument().id.equals("006"));
	}

	@Test
	public void testRemoveOnSingleFullCoverageFacetElement() throws Exception {
		SearchResult searchResult = getSearchClient().search(indexName, new SearchQuery().setQ("avarel"), Collections.emptyMap());
		assertThat(searchResult.slices.getFirst().hits)
				.anyMatch(hit -> hit.getDocument().id.equals("004"))
				.anyMatch(hit -> hit.getDocument().id.equals("006"));

		assertThat(searchResult.slices.getFirst().facets).noneMatch(facet -> "brand".equals(facet.fieldName));
	}
}
