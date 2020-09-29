package mindshift.search.connector.ocs.mapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.cxp.ocs.client.StringUtil;
import de.cxp.ocs.client.models.SearchQuery;
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
        ocsQuery.setLimit(request.getFetchsize());
        ocsQuery.setOffset(request.getOffset().intValue());
        ocsQuery.setSort(translateSortParam(request.getSort()));
        return ocsQuery;
    }

    /**
     * Builds filter hash map for OCS.
     * 
     * @return
     */
    public Map<String, String> getOcsFilters() {
        final Map<String, String> filters = new HashMap<>();
        for (final Entry<String, Object> reqFilterEntry : request.getFilters().entrySet()) {
            if (reqFilterEntry.getValue() instanceof String) {
                filters.put(reqFilterEntry.getKey(), (String) reqFilterEntry.getValue());
            } else if (reqFilterEntry.getValue() instanceof String[]) {
                filters.put(reqFilterEntry.getKey(),
                        StringUtil.join((String[]) reqFilterEntry.getValue(), ","));
            } else {
                LOG.error("can't handle filter '{}' with value of type '{}'",
                        reqFilterEntry.getKey(), reqFilterEntry.getValue().getClass());
            }
        }
        return filters;
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
