package mindshift.search.connector.ocs.mapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

import de.cxp.ocs.model.params.SearchQuery;
import mindshift.search.connector.api.v2.models.SearchRequest;

/**
 * Maps the Mindshift SearchRequest object to OCS SearchQuery + Filters.
 */
public class SearchQueryMapper {

    private final static Logger LOG = LoggerFactory.getLogger(SearchQueryMapper.class);

    private final SearchRequest request;

    /**
     * Constructor of SearchQueryMapper.
     * 
     * @param request
     */
    public SearchQueryMapper(final SearchRequest request) {
        this.request = request;
    }

    /**
     * Builds and returns OCS SearchQuery.
     * 
     * @return
     */
    public SearchQuery getOcsQuery() {
        final SearchQuery ocsQuery = new SearchQuery();
        ocsQuery.setQ(request.getQ());
		if (request.getFetchsize() != null) ocsQuery.setLimit(request.getFetchsize());
		if (request.getOffset() != null) ocsQuery.setOffset(request.getOffset().intValue());
		if (request.getSort() != null) ocsQuery.setSort(translateSortParam(request.getSort()));
        return ocsQuery;
    }

    /**
     * Builds filter hash map for OCS.
     * 
     * @return
     */
    public Map<String, String> getOcsFilters() {
        final Map<String, String> filters = new HashMap<>();
		if (request.getFilters() != null) {
			for (final Entry<String, Object> reqFilterEntry : request.getFilters().entrySet()) {
				if (reqFilterEntry.getValue() instanceof String) {
					filters.put(reqFilterEntry.getKey(), (String) reqFilterEntry.getValue());
				}
				else if (reqFilterEntry.getValue() instanceof String[]) {
					filters.put(reqFilterEntry.getKey(), joinFilterValue((String[]) reqFilterEntry.getValue()));
				}
				else {
					LOG.error("can't handle filter '{}' with value of type '{}'",
							reqFilterEntry.getKey(), reqFilterEntry.getValue().getClass());
				}
			}
        }
        return filters;
    }

	private String joinFilterValue(String[] values) {
		if (values.length == 0) return "";
		if (values.length == 1) return values[0];
		StringBuilder joined = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			if (Strings.isNullOrEmpty(values[i])) continue;
			if (i > 0) joined.append(',');
			joined.append(values[i]);
		}
		return joined.toString();
	}

	/**
	 * translate connector-api sort param to ocs-api sort param
	 * 
	 * @param sort
	 * @return
	 */
    private String translateSortParam(final String sort) {
        final int splitIndex = sort.lastIndexOf('-');
        final String translatedSort;
        if (splitIndex == -1) {
            // sort only contains field name, which is the same behavior in OCS
            translatedSort = sort;
        } else {
            final String order = sort.substring(splitIndex + 1);
            translatedSort = ("desc".equals(order) ? "-" : "") + sort.substring(0, splitIndex);
        }
        return translatedSort;
    }

}
