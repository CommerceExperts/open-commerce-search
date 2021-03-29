package de.cxp.ocs.elasticsearch.query;

import static de.cxp.ocs.config.FieldConstants.VARIANTS;
import static de.cxp.ocs.util.ESQueryUtils.mergeQueries;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import com.google.common.base.Functions;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.elasticsearch.query.filter.FilterContext;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilterAdapter;
import de.cxp.ocs.elasticsearch.query.filter.NumberResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.NumberResultFilterAdapter;
import de.cxp.ocs.elasticsearch.query.filter.TermResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.TermResultFilterAdapter;

public class FiltersBuilder {

	private final Set<String>	postFilterFacets	= new HashSet<>();
	private FieldConfigIndex	indexedFieldConfig;


	private static Map<Class<? extends InternalResultFilter>, InternalResultFilterAdapter<? extends InternalResultFilter>> filterAdapters = new HashMap<>(3);
	static {
		filterAdapters.put(NumberResultFilter.class, new NumberResultFilterAdapter());
		filterAdapters.put(TermResultFilter.class, new TermResultFilterAdapter());
	}

	public FiltersBuilder(SearchContext context) {
		for (FacetConfig facet : context.config.getFacetConfiguration().getFacets()) {
			if (facet.isMultiSelect() || facet.isShowUnselectedOptions()) postFilterFacets.add(facet.getSourceField());
		}
		indexedFieldConfig = context.getFieldConfigIndex();
	}

	public FilterContext buildFilterContext(List<InternalResultFilter> filters) {
		Map<String, InternalResultFilter> filtersByName = filters.stream().collect(Collectors.toMap(f -> f.getField().getName(), Functions.identity()));

		if (filters.isEmpty()) return new FilterContext(filtersByName);

		Map<String, QueryBuilder> basicFilterQueries = new HashMap<>();
		Map<String, QueryBuilder> postFilterQueries = new HashMap<>();

		// collect filter queries on master and variant level
		for (InternalResultFilter filter : filters) {
			@SuppressWarnings("unchecked")
			InternalResultFilterAdapter<? super InternalResultFilter> filterAdapter = (InternalResultFilterAdapter<? super InternalResultFilter>) filterAdapters
					.get(filter.getClass());
			String fieldPrefix = filter.getFieldPrefix();

			QueryBuilder filterQuery = null;
			if (filter.getField().isMasterLevel()) {
				filterQuery = toFilterQuery(filter, fieldPrefix, filterAdapter);
			}

			if (filter.getField().isVariantLevel()) {
				fieldPrefix = VARIANTS + "." + fieldPrefix;
				if (filterQuery == null) {
					filterQuery = toFilterQuery(filter, fieldPrefix, filterAdapter);
				}
				else {
					// if a filter applies to both levels, then build a
					// boolean-should query (both field matches are wanted)
					filterQuery = QueryBuilders.boolQuery()
							.should(filterQuery)
							.should(toFilterQuery(filter, fieldPrefix, filterAdapter));
				}
			}

			if (isBasicQuery(filter.getField().getName())) {
				basicFilterQueries.put(filter.getField().getName(), filterQuery);
			}
			else {
				postFilterQueries.put(filter.getField().getName(), filterQuery);
			}
		}
		MasterVariantQuery postFilterQuery = buildFilters(postFilterQueries);
		QueryBuilder joinedPostFilters = mergeQueries(postFilterQuery.getMasterLevelQuery(), postFilterQuery
				.getVariantLevelQuery());

		return new FilterContext(
				filtersByName,
				Collections.unmodifiableMap(basicFilterQueries),
				Collections.unmodifiableMap(postFilterQueries),
				buildFilters(basicFilterQueries),
				joinedPostFilters);
	}

	private boolean isBasicQuery(String fieldName) {
		return !postFilterFacets.contains(fieldName);
	}

	private MasterVariantQuery buildFilters(Map<String, QueryBuilder> filterQueries) {
		// collect filters and combine into master and variant filters
		QueryBuilder variantFilters = null;
		QueryBuilder masterFilters = null;
		for (Entry<String, QueryBuilder> nestedFieldFilters : filterQueries.entrySet()) {
			if (isVariantField(nestedFieldFilters.getKey())) {
				variantFilters = mergeQueries(variantFilters, nestedFieldFilters.getValue());
			}
			else {
				masterFilters = mergeQueries(masterFilters, nestedFieldFilters.getValue());
			}
		}

		return new MasterVariantQuery(masterFilters, variantFilters, false, true);
	}

	private QueryBuilder toFilterQuery(InternalResultFilter filter, String fieldPrefix, InternalResultFilterAdapter<? super InternalResultFilter> filterAdapter) {
		if (filter.isNestedFilter()) {
			return QueryBuilders.nestedQuery(
					fieldPrefix,
					filterAdapter.getAsQuery(fieldPrefix + ".", filter),
					ScoreMode.None);
		}
		else {
			return filterAdapter.getAsQuery(fieldPrefix, filter);
		}
	}

	private boolean isVariantField(String field) {
		return indexedFieldConfig.getField(field).map(Field::isVariantLevel).orElse(false);
	}

}
