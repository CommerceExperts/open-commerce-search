package mindshift.search.connector.ocs.util;

import java.lang.reflect.Method;

import lombok.RequiredArgsConstructor;
import mindshift.search.connector.api.v2.models.SearchRequest;

@RequiredArgsConstructor
public class SearchRequestBuilder  {

	private final SearchRequest baseRequest;

	public SearchRequest withSort(String sort) {
		SearchRequest clone = cloneRequest();
		clone.setSort(sort);
		return clone;
	}

	private SearchRequest cloneRequest () {
		SearchRequest clone = new SearchRequest();
		for (Method m : SearchRequest.class.getMethods()) {
			if (m.getName().startsWith("set") && m.getParameterCount() == 1) {
				try {
					Method getter = SearchRequest.class.getMethod(m.getName().replaceFirst("set", "get"));
					Object property = getter.invoke(baseRequest);
					m.invoke(clone, property);
				}
				catch (Exception e) {
					throw new IllegalStateException("problem mapping a getter method to the setter method '"+m.getName()+"'", e);
				}
			}
		}
		return clone;
	}
	
}
