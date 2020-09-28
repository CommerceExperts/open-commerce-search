package mindshift.search.connector.ocs.mapper;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import mindshift.search.connector.api.v2.models.SearchRequest;

class MindshiftSearchRequestBuilder {

	private final SearchRequest baseRequest;

	public MindshiftSearchRequestBuilder(SearchRequest baseRequest) {
		this.baseRequest = baseRequest;
	}

	public SearchRequest withSort(String sort) {
		SearchRequest clone = cloneRequest(baseRequest);
		clone.setSort(sort);
		return clone;
	}
	
	public SearchRequest withFilter(String key, String value) {
		SearchRequest clone = cloneRequest(baseRequest);
		clone.putFiltersItem(key, value);
		return clone;
	}

	public boolean hasFilter(String key, final String value) {
		Object filterValue = baseRequest.getFilters().get(key);
		if (value == null) return filterValue == null;
		if (filterValue == null) return false;

		if (filterValue instanceof String[]) {
			Optional<String> findAny = Arrays.stream((String[]) filterValue).filter(value::equals).findAny();
			return findAny.isPresent();
		}
		else if (filterValue instanceof Collection<?>) {
			Optional<String> findAny = ((Collection<?>) filterValue).stream().map(Object::toString).filter(v -> value.equals(v)).findAny();
			return findAny.isPresent();
		}
		else {
			return value.equals(filterValue);
		}
	}

	public static SearchRequest cloneRequest(SearchRequest original) {
		SearchRequest clone = new SearchRequest();
		for (Method m : SearchRequest.class.getMethods()) {
			if (m.getName().startsWith("set") && m.getParameterCount() == 1) {
				try {
					Method getter = SearchRequest.class.getMethod(m.getName().replaceFirst("set", "get"));
					Object property = getter.invoke(original);
					m.invoke(clone, property);
				}
				catch (Exception e) {
					throw new IllegalStateException("problem mapping a getter method to the setter method '" + m.getName() + "'", e);
				}
			}
		}
		return clone;
	}

}
