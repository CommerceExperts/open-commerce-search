package de.cxp.ocs.elasticsearch.query.filter;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import de.cxp.ocs.elasticsearch.model.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.TextMatchQuery;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class FilterContext {

	private final static Map<String, QueryBuilder> NO_FILTER = Collections.emptyMap();

	private final static TextMatchQuery<QueryBuilder> NO_QUERY = new TextMatchQuery<>(null, null, false, true);

	@Getter
	private final Map<String, InternalResultFilter> internalFilters;

	@Getter
	private final Map<String, QueryBuilder> basicFilterQueries;

	@Getter
	private final Map<String, QueryBuilder> postFilterQueries;

	@Getter
	private final TextMatchQuery<QueryBuilder> joinedBasicFilters;

	@Getter
	private final QueryBuilder joinedPostFilters;

	@Getter
	private final QueryBuilder variantPostFilters;

	public FilterContext(Map<String, InternalResultFilter> internalFilters) {
		this(internalFilters, NO_FILTER, NO_FILTER, NO_QUERY, null, null);
	}

	/**
	 * Join filterQueries to a single QueryBuilder but exclude
	 * the one specified with "exclude".
	 * 
	 * @param exclude
	 *        the filter query that should not be joined.
	 * @param filterQueries
	 *        all the filter queries that should be joined
	 * @return a merged QueryBuilder
	 */
	public static Optional<QueryBuilder> joinAllButOne(String exclude, Map<String, QueryBuilder> filterQueries) {
		// don't use "remove" or similar on filterQueries,
		// because filterQueries is an UnmodifiableMap
		if (filterQueries.size() == 1 && filterQueries.containsKey(exclude)) {
			return Optional.empty();
		}
		// if there are exactly 2 entries and one is the excluded, set
		// finalQuery to the remaining query
		QueryBuilder finalQuery;
		if (filterQueries.size() == 2 && filterQueries.containsKey(exclude)) {
			finalQuery = filterQueries.entrySet().stream()
					.filter(entry -> !exclude.equals(entry.getKey()))
					.findFirst()
					.get().getValue();
		}
		else {
			finalQuery = QueryBuilders.boolQuery();
			for (Entry<String, QueryBuilder> fq : filterQueries.entrySet()) {
				if (fq.getKey().equals(exclude)) continue;
				((BoolQueryBuilder) finalQuery).must(fq.getValue());
			}
		}
		return Optional.of(finalQuery);
	}
}
