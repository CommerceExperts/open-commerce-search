package de.cxp.ocs.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.json.JSONObject;

import de.cxp.ocs.api.searcher.Searcher;
import de.cxp.ocs.model.index.Attribute;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;
import de.cxp.ocs.model.params.SearchQuery;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.FacetEntry;
import de.cxp.ocs.model.result.ResultHit;
import de.cxp.ocs.model.result.SearchResult;
import de.cxp.ocs.model.result.SearchResultSlice;

@Path("search")
public class ServerResource {

	private static final List<Document> storage = new ArrayList<Document>();

	ServerResource() {

		Document p = new Product("1")
				.set("title", "Incredible shoes")
				.set("size", 43)
				.set("color", Attribute.of("blue"), Attribute.of("red"))
				.set("category", Attribute.of("Men", "_cat1"), Attribute.of("Shoes", "_cat1_1"));
		storage.add(p);

		Document s = new Product("2")
				.set("title", "Magnificent dress")
				.set("size", "L")
				.set("color", Attribute.of("blue"), Attribute.of("red"))
				.set("category", Attribute.of("Women", "_cat2"), Attribute.of("Dresses", "_cat2_1"));

	
		masterWithVariants((Product) s, 
				new Document("32").set("price", 99.9).set("price.discount", 78.9),
				new Document("33").set("price", 45.6).set("type", "var1"));
		storage.add(s);
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response search(@QueryParam("query") String query, @Context UriInfo uriInfo) throws IOException {
		MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();

		Searcher searcher = getSearcher();
		
		// How do we handle custom/additional parameters, do we just keep them inside Query
		// What about sorting, we have to carry it over to facet links, probably makes sense to handle as part of Query?
		// Query q = query != null
		// ? new Query(query, Style.DSL)
		// : new Query(uriInfo.getRequestUri().getQuery(), Style.URL);
				
		// What would be the purpose of SearchParams if we handle sorting inside Query
		// How do we call the offset and result size parameters in the query? We have to carry them over to facet links as well
		SearchQuery params = new SearchQuery()
				.setUserQuery(queryParams.getFirst("userQuery"))
				.setSort(queryParams.getFirst("sort"));
		
		SearchResult result = searcher.find("catalog01.de", params);
		JSONObject r = new JSONObject(result);
		
		return Response.ok(r.toString(2)).build();
	}

	private Searcher getSearcher() {
		return new Searcher() {
			
			@Override
			public SearchResult find(String tenant, SearchQuery searchQuery) throws IOException {
				SearchResult res = new SearchResult().setInputQuery(searchQuery).setTookInMillis(1);
				
				List<ResultHit> hits = new ArrayList<ResultHit>();
				for (Document doc : storage) {
					hits.add(new ResultHit().setDocument(doc).setMatchedQueries(new String[] { searchQuery.userQuery }));
				}
				

				Facet facet = new Facet("color")
						.addEntry(new FacetEntry("red", 1, "color=red"))
						.addEntry(new FacetEntry("blue", 1, "color=blue"));
				
				SearchResultSlice slice = new SearchResultSlice()
											.setHits(hits)
											.setMatchCount(2)
											.setLabel("main")
											.setFacets(Collections.singletonList(facet));
				
				res.setSlices(Collections.singletonList(slice));
				
				return res;
			}
		};
	}

	private static Product masterWithVariants(Product masterProduct, Document... variantProducts) {
		masterProduct.setVariants(variantProducts);
		return masterProduct;
	}	

}
