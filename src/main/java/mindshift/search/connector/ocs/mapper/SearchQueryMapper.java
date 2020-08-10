package mindshift.search.connector.ocs.mapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import de.cxp.ocs.client.StringUtil;
import de.cxp.ocs.client.models.SearchQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mindshift.search.connector.api.v2.models.SearchRequest;

/**
 * Maps the Mindshift SearchRequest object to OCS SearchQuery + Filters.
 */
@Slf4j
@RequiredArgsConstructor
public class SearchQueryMapper {

	final SearchRequest request;

	public SearchQuery getOcsQuery() {
		SearchQuery ocsQuery = new SearchQuery();
		ocsQuery.setQ(request.getQ());
		ocsQuery.setLimit(request.getFetchsize());
		ocsQuery.setOffset(request.getOffset().intValue());
		ocsQuery.setSort(translateSortParam(request.getSort()));
		return ocsQuery;
	}

	public Map<String, String> getOcsFilters() {
		Map<String, String> filters = new HashMap<>();
		for (Entry<String, Object> reqFilterEntry : request.getFilters().entrySet()) {
			if (reqFilterEntry.getValue() instanceof String) {
				filters.put(reqFilterEntry.getKey(), (String) reqFilterEntry.getValue());
			}
			else if (reqFilterEntry.getValue() instanceof String[]) {
				filters.put(reqFilterEntry.getKey(), StringUtil.join((String[]) reqFilterEntry.getValue(), ","));
			}
			else {
				log.error("can't handle filter '{}' with value of type '{}'", reqFilterEntry.getKey(), reqFilterEntry.getValue().getClass());
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
	private String translateSortParam(String sort) {
		int splitIndex = sort.lastIndexOf('-');
		if (splitIndex == -1) {
			// sort only contains field name, which is the same behavior in OCS
			return sort;
		}
		else {
			String order = sort.substring(splitIndex + 1);
			return (order.equals("desc") ? "-" : "") + sort.substring(0, splitIndex);
		}
	}

}
