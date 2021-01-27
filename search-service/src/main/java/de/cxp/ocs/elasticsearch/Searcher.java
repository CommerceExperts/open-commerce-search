package de.cxp.ocs.elasticsearch;

import static de.cxp.ocs.config.FieldConstants.RESULT_DATA;
import static de.cxp.ocs.config.FieldConstants.VARIANTS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.time.StopWatch;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.InnerHitBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder.FilterFunctionBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AbstractAggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

import de.cxp.ocs.config.FacetConfiguration;
import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.config.FieldType;
import de.cxp.ocs.config.FieldUsage;
import de.cxp.ocs.config.SearchConfiguration;
import de.cxp.ocs.config.SortOptionConfiguration;
import de.cxp.ocs.elasticsearch.facets.CategoryFacetCreator;
import de.cxp.ocs.elasticsearch.facets.FacetConfigurationApplyer;
import de.cxp.ocs.elasticsearch.facets.FacetCreator;
import de.cxp.ocs.elasticsearch.facets.NumberFacetCreator;
import de.cxp.ocs.elasticsearch.facets.TermFacetCreator;
import de.cxp.ocs.elasticsearch.facets.VariantFacetCreator;
import de.cxp.ocs.elasticsearch.query.FiltersBuilder;
import de.cxp.ocs.elasticsearch.query.MasterVariantQuery;
import de.cxp.ocs.elasticsearch.query.builder.ConditionalQueryBuilder;
import de.cxp.ocs.elasticsearch.query.builder.ESQueryBuilder;
import de.cxp.ocs.elasticsearch.query.builder.ESQueryBuilderFactory;
import de.cxp.ocs.elasticsearch.query.builder.MatchAllQueryBuilder;
import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;
import de.cxp.ocs.elasticsearch.query.model.WordAssociation;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.result.ResultHit;
import de.cxp.ocs.model.result.SearchResult;
import de.cxp.ocs.model.result.SearchResultSlice;
import de.cxp.ocs.model.result.Sorting;
import de.cxp.ocs.util.ESQueryUtils;
import de.cxp.ocs.util.InternalSearchParams;
import de.cxp.ocs.util.SearchQueryBuilder;
import de.cxp.ocs.util.StringUtils;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Searcher {

	@NonNull
	private final RestHighLevelClient restClient;

	@NonNull
	private final SearchConfiguration config;

	@NonNull
	private final MeterRegistry registry;

	// TODO: make configurable
	private final List<FacetCreator> facetCreators = new ArrayList<>();

	private final FacetConfigurationApplyer facetApplier;

	private final ConditionalQueryBuilder queryBuilder;

	private final Map<String, Field> sortFields;
	private final Map<String, SortOptionConfiguration>	sortFieldConfig;

	private ScoringCreator scoringCreator;

	private SpellCorrector spellCorrector;

	private final Timer findTimer;
	private final Timer sortTimer;
	private final Timer sqbTimer;
	private final Timer inputWordsTimer;
	private final Timer correctedWordsTimer;
	private final Timer resultTimer;
	private final Timer searchRequestTimer;
	private final DistributionSummary summary;

	public Searcher(RestHighLevelClient restClient, SearchConfiguration config, final MeterRegistry registry) {
		this.restClient = restClient;
		this.config = config;
		this.registry = registry;

		findTimer = getTimer("find", config.getIndexName());
		sortTimer = getTimer("applySort", config.getIndexName());
		resultTimer = getTimer("buildResult", config.getIndexName());
		sqbTimer = getTimer("stagedSearch", config.getIndexName());
		inputWordsTimer = getTimer("inputWordsSearch", config.getIndexName());
		correctedWordsTimer = getTimer("correctedWordsSearch", config.getIndexName());
		searchRequestTimer = getTimer("executeSearchRequest", config.getIndexName());
		summary = DistributionSummary.builder("stagedSearches").tag("indexName", config.getIndexName())
				.register(registry);

		facetApplier = new FacetConfigurationApplyer(config);
		scoringCreator = new ScoringCreator(config);
		sortFields = fetchSortFields();
		sortFieldConfig = config.getSortConfigs().stream().collect(Collectors.toMap(SortOptionConfiguration::getField, s -> s));
		spellCorrector = initSpellCorrection();
		initializeFacetCreators(config);

		queryBuilder = new ESQueryBuilderFactory(restClient, config.getIndexName(), config).build();
	}

	private Timer getTimer(final String name, final String indexName) {
		return Timer.builder(name).tag("indexName", indexName).publishPercentiles(0.5, 0.8, 0.9, 0.95)
				.register(registry);
	}

	private SpellCorrector initSpellCorrection() {
		Set<String> spellCorrectionFields = config.getIndexedFieldConfig().getFieldsByUsage(FieldUsage.Search).keySet();
		return new SpellCorrector(spellCorrectionFields.toArray(new String[spellCorrectionFields.size()]));
	}

	private Map<String, Field> fetchSortFields() {
		Map<String, Field> tempSortFields = config.getIndexedFieldConfig().getFieldsByUsage(FieldUsage.Sort);
		return Collections.unmodifiableMap(tempSortFields);
	}

	// TODO: extract into utility/factory
	private void initializeFacetCreators(SearchConfiguration config) {
		// TODO: for multi-select facets, add collect specialized facetCreators
		// and exclude explicit facets from general facet creators
		// Map<FieldType, FacetCreator> facetCreatorsMap = new HashMap<>();
		// Map<FieldType, FacetCreator> variantFacetCreatorsMap = new
		// HashMap<>();

		// List<String> explicitTermFacets = new ArrayList<>();
		// List<String> explicitNumberFacets = new ArrayList<>();
		// for (FacetConfig facetConfig :
		// config.getFacetConfiguration().getFacets()) {
		// Field sourceField =
		// config.getFieldConfiguration().getFields().get(facetConfig.getSourceField());
		// FacetCreator explicitFacetCreator =
		// createExplicitFacetCreator(sourceField);
		// explicitFacetCreator.setFacetConfig(...);
		// explicitFacetCreator.setExplicitFacet(...);
		// facetCreators.add(explicitFacetCreator); ..or for variant facets
		// explicitTermFacets.add(facetName);
		// }

		// facetCreators.addAll(facetCreatorsMap.values());
		// facetCreators.add(new
		// VariantFacetCreator(variantFacetCreatorsMap.values()));

		for (FacetConfig fc : config.getFacetConfiguration().getFacets()) {
			Optional<Field> facetField = config.getIndexedFieldConfig().getField(fc.getSourceField());
			if (facetField.map(f -> FieldType.category.equals(f.getType())).orElse(false)) {
				facetCreators.add(new CategoryFacetCreator(fc));
				break;
			}
		}

		FacetConfiguration facetConf = config.getFacetConfiguration();
		facetCreators.add(new TermFacetCreator(facetConf).setMaxFacets(facetConf.getMaxFacets()));
		facetCreators.add(new NumberFacetCreator(facetConf).setMaxFacets(facetConf.getMaxFacets()));
		facetCreators.add(new VariantFacetCreator(
				Arrays.asList(new TermFacetCreator(facetConf).setMaxFacets(facetConf.getMaxFacets()),
						new NumberFacetCreator(facetConf).setMaxFacets(facetConf.getMaxFacets()))));
	}

	// private FacetCreator createExplicitFacetCreator(Field field) {
	// switch (type) {
	// case number:
	// return new NumberFacetCreator();
	// case string:
	// return new TermFacetCreator();
	// case category:
	// return new CategoryFacetCreator();
	// default:
	// return null;
	// }
	// }

	/**
	 * 
	 * @param parameters
	 *        internal validated state of the parameters
	 * @return search result
	 * @throws IOException
	 */
	// @Timed(value = "find", percentiles = { 0.5, 0.8, 0.95, 0.98 })
	public SearchResult find(InternalSearchParams parameters) throws IOException {

		long start = System.currentTimeMillis();

		FiltersBuilder filtersBuilder = new FiltersBuilder(config, parameters.filters);

		Iterator<ESQueryBuilder> stagedQueryBuilders;
		List<QueryStringTerm> searchWords;
		if (parameters.userQuery != null && !parameters.userQuery.isEmpty()) {
			String asciiFoldedUserQuery = StringUtils.asciify(parameters.userQuery);
			searchWords = UserQueryAnalyzer.defaultAnalyzer.analyze(asciiFoldedUserQuery);
			stagedQueryBuilders = queryBuilder.getStagedQueryBuilders(searchWords);
		} else {
			stagedQueryBuilders = Collections.<ESQueryBuilder>singletonList(new MatchAllQueryBuilder()).iterator();
			searchWords = Collections.emptyList();
		}

		SearchSourceBuilder searchSourceBuilder = SearchSourceBuilder.searchSource().size(parameters.limit)
				.from(parameters.offset);

		MasterVariantQuery basicFilters = filtersBuilder.buildBasicFilters();

		List<SortBuilder<?>> variantSortings = applySorting(parameters.sortings, searchSourceBuilder);
		setFetchSources(searchSourceBuilder, variantSortings);

		QueryBuilder postFilter = filtersBuilder.buildPostFilters();
		if (postFilter != null) {
			searchSourceBuilder.postFilter(postFilter);
		}

		List<AggregationBuilder> aggregators = buildAggregators(parameters);
		if (aggregators != null && aggregators.size() > 0) {
			aggregators.forEach(searchSourceBuilder::aggregation);
		}

		// TODO: add a "hint" to the params for follow-up searches or at least a
		// cache to pick the correct query for known search terms

		// staged search: try each query builder until we get a result
		// + try and use spell correction with first query
		int i = 0;
		SearchResponse searchResponse = null;
		Map<String, WordAssociation> correctedWords = null;
		Sample sqbSample = Timer.start(registry);
		while ((searchResponse == null || searchResponse.getHits().getTotalHits().value == 0)
				&& stagedQueryBuilders.hasNext()) {
			StopWatch sw = new StopWatch();
			sw.start();
			Sample inputWordsSample = Timer.start(registry);
			ESQueryBuilder stagedQueryBuilder = stagedQueryBuilders.next();

			MasterVariantQuery searchQuery = stagedQueryBuilder.buildQuery(searchWords);
			if (log.isTraceEnabled()) {
				log.trace("query builder nr {}: {}: match query = {}", i, stagedQueryBuilder.getName(),
						searchQuery == null ? "NULL"
								: searchQuery.getMasterLevelQuery().toString().replaceAll("[\n\\s]+", " "));
			}
			if (searchQuery == null)
				continue;

			if (correctedWords == null && spellCorrector != null
					&& stagedQueryBuilder.allowParallelSpellcheckExecution()
					&& (!searchQuery.isWithSpellCorrection() || stagedQueryBuilders.hasNext())) {
				searchSourceBuilder.suggest(spellCorrector.buildSpellCorrectionQuery(parameters.userQuery));
			} else {
				searchSourceBuilder.suggest(null);
			}

			searchSourceBuilder.query(buildFinalQuery(searchQuery, basicFilters, variantSortings));
			searchResponse = executeSearchRequest(searchSourceBuilder);

			if (log.isDebugEnabled()) {
				log.debug("Query Builder Nr {} ({}) done in {}ms with {} hits", i, stagedQueryBuilder.getName(),
						sw.getTime(), searchResponse.getHits().getTotalHits().value);
			}
			inputWordsSample.stop(inputWordsTimer);

			// if we don't have any hits, but there's a chance to get corrected
			// words, then enrich the search words with the corrected words
			if (searchResponse.getHits().getTotalHits().value == 0 && correctedWords == null && spellCorrector != null
					&& searchResponse.getSuggest() != null) {
				Sample correctedWordsSample = Timer.start(registry);
				correctedWords = spellCorrector.extractRelatedWords(searchWords, searchResponse.getSuggest());
				if (correctedWords.size() > 0) {
					searchWords = SpellCorrector.toListWithAllTerms(searchWords, correctedWords);
				}

				// if the current query builder didn't take corrected words into
				// account, then try again with corrected words
				if (correctedWords.size() > 0 && !searchQuery.isWithSpellCorrection()) {
					searchQuery = stagedQueryBuilder.buildQuery(searchWords);
					searchSourceBuilder.query(buildFinalQuery(searchQuery, basicFilters, variantSortings));
					searchResponse = executeSearchRequest(searchSourceBuilder);
				}
				correctedWordsSample.stop(correctedWordsTimer);
			}

			if (searchResponse.getHits().getTotalHits().value == 0 && searchQuery.isAcceptNoResult()) {
				break;
			}

			i++;
		}
		sqbSample.stop(sqbTimer);
		summary.record(i);

		SearchResult searchResult = buildResult(parameters, searchResponse, new SearchQueryBuilder(parameters));
		searchResult.tookInMillis = System.currentTimeMillis() - start;

		findTimer.record(searchResult.tookInMillis, TimeUnit.MILLISECONDS);

		return searchResult;
	}

	private SearchResponse executeSearchRequest(SearchSourceBuilder searchSourceBuilder) throws IOException {
		Sample sample = Timer.start(registry);
		SearchResponse searchResponse;
		{
			SearchRequest searchRequest = new SearchRequest(config.getIndexName())
					.searchType(SearchType.QUERY_THEN_FETCH).source(searchSourceBuilder);
			searchResponse = restClient.search(searchRequest, RequestOptions.DEFAULT);
		}
		sample.stop(searchRequestTimer);
		return searchResponse;
	}

	private SearchResult buildResult(InternalSearchParams parameters, SearchResponse searchResponse,
			SearchQueryBuilder linkBuilder) {
		SearchResult searchResult = new SearchResult();
		searchResult.inputURI = SearchQueryBuilder.toLink(parameters).toString();
		searchResult.slices = new ArrayList<>(1);

		resultTimer.record(() -> {
			if (searchResponse != null) {
				SearchResultSlice searchResultSlice = toSearchResult(searchResponse, parameters);
				searchResultSlice.facets = facetApplier.getFacets(searchResponse.getAggregations(), facetCreators,
						searchResultSlice.matchCount, parameters.filters, linkBuilder);
				searchResult.slices.add(searchResultSlice);
			}
		});
		searchResult.sortOptions = buildSortOptions(linkBuilder);

		return searchResult;
	}

	private List<Sorting> buildSortOptions(SearchQueryBuilder linkBuilder) {
		List<Sorting> sortings = new ArrayList<>();
		for (Field sortField : sortFields.values()) {
			// XXX check why both level might not work
			// if (sortField.isBothLevel())
			// continue;
			SortOptionConfiguration sortOptionConfiguration = sortFieldConfig.get(sortField.getName());
			de.cxp.ocs.model.result.SortOrder[] sortOrders = sortOptionConfiguration == null ? de.cxp.ocs.model.result.SortOrder.values()
					: sortOptionConfiguration.getShownOrders();
			for (de.cxp.ocs.model.result.SortOrder order : sortOrders) {
				sortings.add(new Sorting(sortField.getName(), order, linkBuilder.isSortingActive(sortField, order),
						linkBuilder.withSortingLink(sortField, order)));
			}
		}
		return sortings;
	}

	private void setFetchSources(SearchSourceBuilder searchSourceBuilder, List<SortBuilder<?>> variantSortings) {
		List<String> includeFields = new ArrayList<>();
		includeFields.add(FieldConstants.RESULT_DATA + ".*");
		// TODO: return search data only if configured
		includeFields.add(FieldConstants.SEARCH_DATA + ".*");
		if (variantSortings.size() > 0) {
			includeFields.add(FieldConstants.SORT_DATA + ".*");
		}

		searchSourceBuilder.fetchSource(includeFields.toArray(new String[includeFields.size()]), null);
	}

	/**
	 * Applies sort definitions onto the searchSourceBuilder and if some of these
	 * sorts also apply to the variant level, it will create these sort definitions
	 * and return them as list.
	 * 
	 * @param parameters
	 * @param searchSourceBuilder
	 * @return a list of potential variant sorts
	 */
	private List<SortBuilder<?>> applySorting(List<Sorting> sortings, SearchSourceBuilder searchSourceBuilder) {
		return sortTimer.record(() -> {
			List<SortBuilder<?>> variantSortings = new ArrayList<>();
			for (Sorting sorting : sortings) {
				Field sortField = sortFields.get(sorting.field);
				if (sortField != null) {
					SortOptionConfiguration sortConf = sortFieldConfig.get(sortField.getName());
					String missingParam = sortConf != null ? sortConf.getMissing() : null;

					searchSourceBuilder.sort(SortBuilders.fieldSort(FieldConstants.SORT_DATA + "." + sorting.field)
							.order(sorting.sortOrder == null ? SortOrder.ASC : SortOrder.fromString(sorting.sortOrder.name()))
							.missing(missingParam));

					if (sortField.isVariantLevel()) {
						variantSortings.add(
								SortBuilders
										.fieldSort(FieldConstants.VARIANTS + "." + FieldConstants.SORT_DATA + "." + sorting.field)
										.order(sorting.sortOrder == null ? SortOrder.ASC : SortOrder.fromString(sorting.sortOrder.name()))
										.missing(missingParam));
					}
				} else {
					log.debug("tried to sort by an unsortable field {}", sorting.field);
				}
			}
			return variantSortings;
		});
	}

	private QueryBuilder buildFinalQuery(MasterVariantQuery searchQuery, MasterVariantQuery basicFilters,
			List<SortBuilder<?>> variantSortings) {
		QueryBuilder masterLevelQuery = ESQueryUtils.mergeQueries(searchQuery.getMasterLevelQuery(),
				basicFilters.getMasterLevelQuery());

		boolean variantQueryMustMatch = false;
		QueryBuilder varFilterQuery = basicFilters.getVariantLevelQuery();
		if (varFilterQuery == null) {
			varFilterQuery = QueryBuilders.matchAllQuery();
		} else {
			// if variant query contains hard filters, it should go into a
			// boolean must clause
			variantQueryMustMatch = true;
		}

		FilterFunctionBuilder[] masterScoringFunctions = scoringCreator.getScoringFunctions(false);
		if (masterScoringFunctions.length > 0) {
			masterLevelQuery = QueryBuilders.functionScoreQuery(masterLevelQuery, masterScoringFunctions)
					.boostMode(scoringCreator.getBoostMode()).scoreMode(scoringCreator.getScoreMode())
					.maxBoost(masterScoringFunctions.length);
		}

		QueryBuilder variantsMatchQuery;
		if (variantSortings.isEmpty() && searchQuery.getVariantLevelQuery() != null) {
			// if sorting is available, scoring and boosting not necessary
			variantsMatchQuery = QueryBuilders.boolQuery().must(varFilterQuery)
					.should(searchQuery.getVariantLevelQuery().boost(2f));

			FilterFunctionBuilder[] variantScoringFunctions = scoringCreator.getScoringFunctions(true);
			if (variantScoringFunctions.length > 0) {
				variantsMatchQuery = QueryBuilders.functionScoreQuery(variantsMatchQuery, variantScoringFunctions);
			}
		} else {
			variantsMatchQuery = varFilterQuery;
		}

		NestedQueryBuilder variantLevelQuery = QueryBuilders
				.nestedQuery(FieldConstants.VARIANTS, variantsMatchQuery, ScoreMode.Avg)
				.innerHit(getVariantInnerHits(variantSortings));

		if (variantQueryMustMatch) {
			return ESQueryUtils.mergeQueries(masterLevelQuery, variantLevelQuery);
		}

		// in case the variant query has no required filter, we only use it in a
		// should clause to accept products without variants as well!
		if (masterLevelQuery instanceof BoolQueryBuilder) {
			return ((BoolQueryBuilder) masterLevelQuery).should(variantLevelQuery);
		} else {
			return QueryBuilders.boolQuery().must(masterLevelQuery).should(variantLevelQuery);
		}
	}

	private InnerHitBuilder getVariantInnerHits(List<SortBuilder<?>> variantSortings) {
		InnerHitBuilder variantInnerHits = new InnerHitBuilder().setSize(1).setFetchSourceContext(
				new FetchSourceContext(true, new String[] { VARIANTS + "." + RESULT_DATA + ".*" }, null));
		if (!variantSortings.isEmpty()) {
			variantInnerHits.setSorts(variantSortings);
		}
		return variantInnerHits;
	}

	private SearchResultSlice toSearchResult(SearchResponse search, InternalSearchParams parameters) {
		SearchHits searchHits = search.getHits();
		SearchResultSlice srSlice = new SearchResultSlice();
		// XXX think about building parameters according to the actual performed
		// search (e.g. with relaxed query or with implicit set filters)
		srSlice.resultLink = SearchQueryBuilder.toLink(parameters).toString();
		srSlice.matchCount = searchHits.getTotalHits().value;

		Map<String, SortOrder> sortedFields = getSortedNumericFields(parameters);

		ArrayList<ResultHit> resultHits = new ArrayList<>();
		for (SearchHit hit : searchHits.getHits()) {
			SearchHits variantHits = hit.getInnerHits().get("variants");
			SearchHit variantHit = null;
			if (variantHits.getHits().length > 0) {
				variantHit = variantHits.getAt(0);
			}

			ResultHit resultHit = new ResultHit().setDocument(getResultDocument(hit, variantHit))
					.setIndex(hit.getIndex()).setMatchedQueries(hit.getMatchedQueries());

			addSortFieldPrefix(hit, resultHit, sortedFields);

			resultHits.add(resultHit);
		}
		srSlice.hits = resultHits;
		srSlice.nextOffset = parameters.offset + searchHits.getHits().length;
		return srSlice;
	}

	private Map<String, SortOrder> getSortedNumericFields(InternalSearchParams parameters) {
		Map<String, SortOrder> sortedNumberFields = new HashMap<>();
		for (Sorting sorting : parameters.sortings) {
			Field sortingField = sortFields.get(sorting.field);
			if (sortingField != null && FieldType.number.equals(sortingField.getType())) {
				sortedNumberFields.put(sorting.field, SortOrder.fromString(sorting.sortOrder.name()));
			}
		}
		return sortedNumberFields;
	}

	/**
	 * If we sort by a numeric value (e.g. price) and there are several different
	 * values at a product for that given field (e.g. multiple prices from the
	 * variants), then add a prefix "from" or "to" depending on sort order.
	 * 
	 * The goal is to show "from 10€" if sorted by price ascending and "to 59€" if
	 * sorted by price descending.
	 * 
	 * @param hit
	 * @param resultHit
	 * @param sortedFields
	 */
	@SuppressWarnings("unchecked")
	private void addSortFieldPrefix(SearchHit hit, ResultHit resultHit, Map<String, SortOrder> sortedFields) {
		Object sortData = hit.getSourceAsMap().get(FieldConstants.SORT_DATA);
		if (sortData != null && sortData instanceof Map && sortedFields.size() > 0) {
			sortedFields.forEach((fieldName, order) -> {
				resultHit.document.getData().computeIfPresent(fieldName, (fn, v) -> {
					Object fieldSortData = ((Map<String, Object>) sortData).get(fn);
					if (fieldSortData != null && fieldSortData instanceof Collection
							&& ((Collection<?>) fieldSortData).size() > 1) {
						resultHit.document.set(fn + "_prefix", SortOrder.ASC.equals(order) ? "{from}" : "{to}");
						// collection is already sorted asc/desc: first value is
						// the relevant one
						return ((Collection<?>) fieldSortData).iterator().next();
					}
					return v;
				});
			});
		}
	}

	private Document getResultDocument(SearchHit hit, SearchHit variantHit) {
		Map<String, Object> resultFields = new HashMap<>();
		for (String sourceDataField : new String[] { FieldConstants.RESULT_DATA, FieldConstants.SEARCH_DATA }) {
			putDataIntoResult(hit, resultFields, sourceDataField);
			if (variantHit != null) {
				putDataIntoResult(variantHit, resultFields, sourceDataField);
			}
		}

		// TODO: Only for development purposes, remove in production to save
		// performance!!
		resultFields.entrySet().stream().sorted(
				Comparator.comparing(entry -> config.getIndexedFieldConfig().getField(entry.getKey()), (f1, f2) -> {
					if (!f1.isPresent()) {
						return 1;
					} else if (!f2.isPresent()) {
						return -1;
					}
					int o1 = f1.get().getUsage().contains(FieldUsage.Score) ? 1 : 0;
					int o2 = f2.get().getUsage().contains(FieldUsage.Score) ? 1 : 0;

					if (o1 == 0 && o1 == o2) {
						return String.CASE_INSENSITIVE_ORDER.compare(f1.get().getName(), f2.get().getName());
					}
					return Integer.compare(o2, o1);
				})).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue,
						LinkedHashMap::new));

		return new Document(hit.getId()).setData(resultFields);
	}

	@SuppressWarnings("unchecked")
	private void putDataIntoResult(SearchHit hit, Map<String, Object> resultFields, String sourceDataField) {
		Object sourceData = hit.getSourceAsMap().get(sourceDataField);
		if (sourceData != null && sourceData instanceof Map) {
			resultFields.putAll((Map<String, Object>) sourceData);
		}
	}

	private List<AggregationBuilder> buildAggregators(InternalSearchParams parameters) {
		// TODO: instead passing the search params, the filters-builder should
		// be enough
		List<AggregationBuilder> aggregators = new ArrayList<>();
		for (FacetCreator fc : facetCreators) {
			AbstractAggregationBuilder<?> aggregationBuilder = fc.buildAggregation(parameters);
			if (aggregationBuilder != null) {
				aggregators.add(aggregationBuilder);
			}
		}
		return aggregators;
	}
}
