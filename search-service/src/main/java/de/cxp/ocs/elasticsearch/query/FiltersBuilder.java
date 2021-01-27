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

import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.config.SearchConfiguration;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilterAdapter;
import de.cxp.ocs.elasticsearch.query.filter.NumberResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.NumberResultFilterAdapter;
import de.cxp.ocs.elasticsearch.query.filter.TermResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.TermResultFilterAdapter;

public class FiltersBuilder {

	private final Set<String>	postFilterFacets	= new HashSet<>();
	private FieldConfigIndex	indexedFieldConfig;

	private Map<String, QueryBuilder>	basicFilterQueries	= new HashMap<>();
	private Map<String, QueryBuilder>	postFilterQueries	= new HashMap<>();

	private MasterVariantQuery	joinedBasicFilters;
	private QueryBuilder		joinedPostFilters;

	private static Map<Class<? extends InternalResultFilter>, InternalResultFilterAdapter<? extends InternalResultFilter>> filterAdapters = new HashMap<>(3);
	static {
		filterAdapters.put(NumberResultFilter.class, new NumberResultFilterAdapter());
		filterAdapters.put(TermResultFilter.class, new TermResultFilterAdapter());
	}

	public FiltersBuilder(SearchConfiguration searchConfig, List<InternalResultFilter> filters) {
		for (FacetConfig facet : searchConfig.getFacetConfiguration().getFacets()) {
			if (facet.isMultiSelect() || facet.isShowUnselectedOptions()) postFilterFacets.add(facet.getSourceField());
		}
		indexedFieldConfig = searchConfig.getIndexedFieldConfig();
		// TODO: replace stateful approach by "builder approach":
		// FiltersBuilder should be initialized once and transform the
		// InternalResultFilter list into a "FilterContext" or something like
		// that where it has all the ES specific filter queries
		prepareFilters(filters);
	}


	public MasterVariantQuery getJoinedBasicFilters() {
		if (joinedBasicFilters == null) {
			joinedBasicFilters = buildFilters(basicFilterQueries);
		}
		return joinedBasicFilters;
	}

	public QueryBuilder getJoinedPostFilters() {
		if (joinedPostFilters == null) {
			MasterVariantQuery separatedPostFilters = buildFilters(postFilterQueries);
			joinedPostFilters = mergeQueries(separatedPostFilters.getMasterLevelQuery(), separatedPostFilters
					.getVariantLevelQuery());
		}
		return joinedPostFilters;
	}

	public Map<String, QueryBuilder> getPostFilterQueries() {
		return Collections.unmodifiableMap(postFilterQueries);
	}

	public QueryBuilder allWithPostFilterNamesExcluded(String filterNamePath) {
		QueryBuilder allFilter;
		if (postFilterQueries.isEmpty()) {
			allFilter = QueryBuilders.matchAllQuery();
		}
		else {
			allFilter = getJoinedPostFilters();
			if (!(allFilter instanceof BoolQueryBuilder)) {
				allFilter = QueryBuilders.boolQuery().must(allFilter);
			}
			((BoolQueryBuilder) allFilter).mustNot(QueryBuilders.termsQuery(filterNamePath, postFilterQueries.keySet()));
		}
		return allFilter;
	}

	public static QueryBuilder allButOne(String exclude, Map<String, QueryBuilder> filterQueries) {
		// don't use "remove" or similar on filterQueries,
		// because filterQueries is an UnmodifiableMap
		if (filterQueries.size() == 1 && filterQueries.containsKey(exclude)) {
			return QueryBuilders.matchAllQuery();
		}
		// if there are exactly 2 entries and one is the excluded, set
		// finalQuery to the remaining query later. Setting it to "null" here is
		// the "marker" for this behavior
		QueryBuilder finalQuery;
		if (filterQueries.size() == 2 && filterQueries.containsKey(exclude)) {
			finalQuery = null;
		}
		else {
			finalQuery = QueryBuilders.boolQuery();
		}

		for (Entry<String, QueryBuilder> fq : filterQueries.entrySet()) {
			if (fq.getKey().equals(exclude)) continue;
			// only null if there is only one matching QueryBuilder
			if (finalQuery == null) {
				finalQuery = fq.getValue();
				break;
			}
			else {
				((BoolQueryBuilder) finalQuery).must(fq.getValue());
			}
		}
		return finalQuery;
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

	private boolean isBasicQuery(String fieldName) {
		return !postFilterFacets.contains(fieldName);
	}

	private boolean isPostFilterQuery(String fieldName) {
		return postFilterFacets.contains(fieldName);
	}

	private void prepareFilters(List<InternalResultFilter> filters) {
		if (filters.isEmpty()) return;

		// collect filter queries on master and variant level
		// TODO: very error prone code. Do this
		boolean buildVariantQueryAfterwards = false;
		for (InternalResultFilter filter : filters) {
			@SuppressWarnings("unchecked")
			InternalResultFilterAdapter<? super InternalResultFilter> filterAdapter = (InternalResultFilterAdapter<? super InternalResultFilter>) filterAdapters
					.get(filter.getClass());
			String fieldPrefix = filter.getFieldPrefix();

			QueryBuilder filterQuery = null;
			if (isMasterField(filter.getField())) {
				filterQuery = toFilterQuery(filter, fieldPrefix, filterAdapter);
			}

			if (isVariantField(filter.getField())) {
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

			if (isBasicQuery(filter.getField())) {
				basicFilterQueries.put(filter.getField(), filterQuery);
			}
			else {
				postFilterQueries.put(filter.getField(), filterQuery);
			}
		}
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

	private boolean isMasterField(String field) {
		return indexedFieldConfig.getField(field).map(Field::isMasterLevel).orElse(false);
	}

}
