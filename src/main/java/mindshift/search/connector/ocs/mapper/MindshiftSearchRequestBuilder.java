package mindshift.search.connector.ocs.mapper;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;

import mindshift.search.connector.api.v2.models.SearchRequest;

/**
 * Convenience Builder for mindshift Search Requests.
 * 
 * @author Rudolf Batt
 */
class MindshiftSearchRequestBuilder {

    private final SearchRequest baseRequest;

    /**
     * Constructor for reusable request builder.
     * 
     * @param baseRequest
     */
    public MindshiftSearchRequestBuilder(final SearchRequest baseRequest) {
        this.baseRequest = baseRequest;
    }

    /**
     * Build new {@link SearchRequest} with given sort parameter.
     * 
     * @param sort
     * @return
     */
    public SearchRequest withSort(final String sort) {
        SearchRequest clone = cloneRequest(baseRequest);
        clone.setSort(sort);
        return clone;
    }

    /**
     * Build new {@link SearchRequest} with additional filter.
     * 
     * @param key
     * @param value
     * @return
     */
    public SearchRequest withFilter(final String key, final String value) {
        final SearchRequest clone = cloneRequest(baseRequest);
        clone.putFiltersItem(key, value);
        return clone;
    }

    /**
     * Checks whether base request has that specific filter set.
     * 
     * @param key
     * @param value
     * @return
     */
    public boolean hasFilter(final String key, final String value) {
        final Object filterValue = baseRequest.getFilters().get(key);
        boolean foundFilter = false;
        if (value == null) {
            foundFilter = filterValue == null;
        } else if (filterValue == null) {
            foundFilter = false;
        } else if (filterValue instanceof String[]) {
            foundFilter = Arrays.stream((String[]) filterValue).filter(value::equals).findAny()
                    .isPresent();
        } else if (filterValue instanceof Collection<?>) {
            foundFilter = ((Collection<?>) filterValue).stream().map(Object::toString)
                    .filter(v -> value.equals(v)).findAny().isPresent();
        } else {
            foundFilter = value.equals(filterValue);
        }
        return foundFilter;
    }

    /**
     * Create deep copy of SearchRequest.
     * 
     * @param original
     * @return
     */
    public static SearchRequest cloneRequest(final SearchRequest original) {
        final SearchRequest clone = new SearchRequest();
        for (Method m : SearchRequest.class.getMethods()) {
            if (m.getName().startsWith("set") && m.getParameterCount() == 1) {
                try {
                    Method getter = SearchRequest.class
                            .getMethod(m.getName().replaceFirst("set", "get"));
                    Object property = getter.invoke(original);
                    m.invoke(clone, property);
                } catch (Exception e) {
                    throw new IllegalStateException(
                            "problem mapping a getter method to the setter method '" + m.getName()
                                    + "'",
                            e);
                }
            }
        }
        return clone;
    }

}
