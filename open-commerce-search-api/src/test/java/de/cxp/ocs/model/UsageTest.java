package de.cxp.ocs.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import de.cxp.ocs.api.indexer.FullIndexer;
import de.cxp.ocs.api.indexer.ImportSession;
import de.cxp.ocs.api.searcher.Searcher;
import de.cxp.ocs.model.index.Attribute;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;
import de.cxp.ocs.model.params.SearchParams;
import de.cxp.ocs.model.params.SortOrder;
import de.cxp.ocs.model.params.Sorting;
import de.cxp.ocs.model.query.Query;
import de.cxp.ocs.model.result.ResultHit;
import de.cxp.ocs.model.result.SearchResult;
import de.cxp.ocs.model.result.SearchResultSlice;

public class UsageTest {
	
	private static final List<Document> storage = new ArrayList<Document>();
	
	@org.junit.jupiter.api.Test
	public void usageTest() throws Exception {

		Document p = new Product("1")
				.set("title", "Incredible shoes")
				.set("size", 43)
				.set("color", Attribute.of("blue"), Attribute.of("red"))
				.set("category", Attribute.of("Men", "_cat1"), Attribute.of("Shoes", "_cat1_1"));

		Document s = new Product("2")
				.set("title", "Magnificent dress")
				.set("size", "L")
				.set("color", Attribute.of("blue"), Attribute.of("red"))
				.set("category", Attribute.of("Women", "_cat2"), Attribute.of("Dresses", "_cat2_1"));

	
		masterWithVariants((Product) s, 
				new Document("32").set("price", 99.9).set("price.discount", 78.9),
				new Document("33").set("price", 45.6).set("type", "var1"));
		
		FullIndexer indexer = getIndexer();
		
		ImportSession session = indexer.startImport("catalog01", Locale.GERMAN);
		indexer.addProducts(session, new Document[] {p, s});
		indexer.done(session);
		
		Searcher searcher = getSearcher();
		
		Query q = new Query("_s=dress&color=red&category=cat1");
		SearchParams params = new SearchParams().withSorting(new Sorting("price", SortOrder.ASC));
		
		SearchResult result = searcher.find("catalog01.de", q, params);
		
		SearchResultSlice main = result.getSlices().iterator().next();
		System.out.println(main);
	}
	
	private Searcher getSearcher() {
		return new Searcher() {
			
			@Override
			public SearchResult find(String tenant, Query query, SearchParams parameters) throws IOException {
				SearchResult res = new SearchResult().setParams(parameters).setTookInMillis(1);
				
				List<ResultHit> hits = new ArrayList<ResultHit>();
				for (Document doc : storage) {
					hits.add(new ResultHit().setDocument(doc).setMatchedQueries(new String[] {query.toString()}));
				}
				SearchResultSlice slice = new SearchResultSlice().setHits(hits);
				res.setSlices(Collections.singletonList(slice));
				
				return res;
			}
		};
	}

	private FullIndexer getIndexer() {
		return new FullIndexer() {
			
			@Override
			public ImportSession startImport(String indexName, Locale locale) throws IllegalStateException {
				return new ImportSession(indexName, indexName + "-tmp");
			}
			
			@Override
			public boolean done(ImportSession session) throws Exception {
				return true;
			}
			
			@Override
			public boolean cancel(ImportSession session) {
				return false;
			}
			
			@Override
			public void addProducts(ImportSession session, Document[] doc) throws Exception {
				storage.addAll(Arrays.asList(doc));
			}
		};
	}

	private static Product masterWithVariants(Product masterProduct, Document... variantProducts) {
		masterProduct.setVariants(variantProducts);
		return masterProduct;
	}	
}