package de.cxp.ocs;

import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;
import de.cxp.ocs.model.params.SearchQuery;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.FacetEntry;
import de.cxp.ocs.model.result.SearchResult;
import de.cxp.ocs.model.result.SearchResultSlice;
import de.cxp.ocs.util.DataIndexer;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.rnorth.ducttape.unreliables.Unreliables;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static de.cxp.ocs.OCSStack.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Slf4j
@ExtendWith({ OCSStack.class })
public class ITPartialUpdates {

	private final static String indexName = "partial_update_test";

	@BeforeAll
	public static void prepareData() throws Exception {
		assertTrue(new DataIndexer(getImportClient()).indexTestData(indexName) > 0);
	}

	@Test
	public void testAdd() throws Exception {
		getImportClient()
				.putDocuments(indexName, false, null, Collections.singletonList(
								new Document().setId("101").set("title", "Add Test")));
		
		flushIndex();
		
		Document fetchedDoc = getSearchClient().getDocument(indexName, "101");
		assertThat(fetchedDoc).isNotNull();
		assertThat(fetchedDoc.data.get("title")).isEqualTo("Add Test");
	}

	@Test
	public void testPatchSearchableField() throws Exception {
		String newBrandName = "Shisano";
		
		Supplier<SearchResult> searchCall = () -> doSimpleSearch(newBrandName.toLowerCase());
		SearchResultSlice searchResultSlice = searchCall.get().slices.get(0);
		// either nothing is found or because of some super fuzzy query, too
		// much was found
		assumeTrue(searchResultSlice.matchCount == 0 || searchResultSlice.matchCount > 1);

		getImportClient()
				.patchDocuments(indexName, Arrays.asList(
						new Document("001").set("brand", newBrandName),
						// modify another document's title from another brand to get the brand facet
						// with 2 items from which 1 must be the new one
						new Document("003").set("title", "bike light compatible with " + newBrandName)));

		flushIndex();
		
		Unreliables.retryUntilTrue(10, TimeUnit.SECONDS, () -> getSearchClient().getDocument(indexName, "001").data.get("brand").equals(newBrandName));
		assertThat(getSearchClient().getDocument(indexName, "001").data.get("brand")).isEqualTo(newBrandName);

		Unreliables.retryUntilTrue(10, TimeUnit.SECONDS, () -> searchCall.get().slices.get(0).matchCount > 0);
		SearchResultSlice searchResultSlice2 = searchCall.get().slices.get(0);

		assertThat(searchResultSlice2.matchCount).isEqualTo(2);

		// since brand is also a field used for facets, check that as well
		Optional<Facet> brandFacet = searchResultSlice2.facets.stream().filter(facet -> facet.fieldName.equals("brand"))
				.findFirst();
		assertTrue(brandFacet.isPresent(),
				() -> "expecting brand facet present, but was not part of facets: "+
						searchResultSlice2.facets.stream().map(Facet::getFieldName).collect(Collectors.joining(" ")));
		assertTrue(brandFacet.get().entries.stream().anyMatch(entry -> newBrandName.equals(entry.getKey())),
				() -> "expecting brand facet to contain '" + newBrandName + "' but was not part of entries:"
						+ brandFacet.get().entries.stream().map(FacetEntry::getKey).collect(Collectors.joining(" ")));
	}

	@Test
	public void testPatchVariantPrice() throws Exception {
		getImportClient()
				.patchProducts(indexName, Collections.singletonList(
						new Product("005").setVariants(new Document[] { new Document().setId("005_0").set("price", 21.5) })));
		flushIndex();

		Document patchedDocument = getSearchClient().getDocument(indexName, "005");

		assertThat(patchedDocument).isInstanceOf(Product.class);
		Product patchedProduct = ((Product) patchedDocument);

		assertThat(patchedProduct.variants).hasSize(2);
		Document patchedVariant = patchedProduct.variants[0].getId().equals("005_0") ? patchedProduct.variants[0] : patchedProduct.variants[1];

		assertThat(patchedVariant.data.get("price")).isEqualTo(21.5);
	}

	private SearchResult doSimpleSearch(String query) {
		try {
			return getSearchClient().search(indexName, new SearchQuery().setQ(query), null);
		}
		catch (Exception e) {
			log.error("", e);
			return null;
		}
	}

	private void flushIndex() throws IOException {
		getElasticsearchClient().performRequest(new Request("POST", indexName + "/_flush/synced"));
	}

}
