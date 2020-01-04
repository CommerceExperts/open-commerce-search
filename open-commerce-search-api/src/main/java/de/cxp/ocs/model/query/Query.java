package de.cxp.ocs.model.query;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Represent a query as a string, parse a string into a Query.
 * 
 * This implementation works on & delimited key/value pairs, handling each pair as a refinement i.e. attribute/value.
 * The special _s attribute denotes a search phrase
 * 
 * NB this implementation is incomplete
 * 
 * @author pavel
 *
 */
@Data
@Accessors(chain = true)
public class Query {

	private String searchPhrase;
	private List<Refinement> refinements = new ArrayList<Query.Refinement>();
	
	public Query(String query) {
	    String[] pairs = query.split("&");
	    
	    for (String pair : pairs) {
	        int index = pair.indexOf("=");
	        try {
				String attribute = URLDecoder.decode(pair.substring(0, index), "UTF-8");
				String values = URLDecoder.decode(pair.substring(index + 1), "UTF-8");
				
				if (attribute.equals("_s")) {
					searchPhrase = values;
				} else {
					refinements.add(new Refinement(attribute, values.split(",")));
				}
			} catch (UnsupportedEncodingException e) {
				// UTF-8 is supported
			}
	    }
	}
	
	public Query() {
		// TODO Auto-generated constructor stub
	}

	public Query setSearch(String searchPhrase) {
		this.searchPhrase = searchPhrase;
		return this;
	}
	
	public Query addRefinement(String attribute, String... values) {
		refinements.add(new Refinement(attribute, values));
		return this;
	}
	
	@Override
	public String toString() {
		StringBuilder result = new StringBuilder();
		if (searchPhrase != null) {
			result.append("_s=").append(searchPhrase);
		}
		
		for (Refinement r : refinements) {
			if (result.length() > 0) {
				result.append("&");
			}

			result.append("_").append(r.attribute).append("=").append(String.join(",", r.values));
		}
		
		return result.toString();
	}

	private static class Refinement {
		private String attribute;
		private String[] values;

		Refinement(String attribute, String... values) {
			this.attribute = attribute;
			this.values = values;
		}
	}
}
