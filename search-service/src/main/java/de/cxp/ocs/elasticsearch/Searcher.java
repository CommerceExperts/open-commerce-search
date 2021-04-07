package de.cxp.ocs.elasticsearch;

import static de.cxp.ocs.config.FieldConstants.RESULT_DATA;
import static de.cxp.ocs.config.FieldConstants.VARIANTS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortOrder;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.SearchPlugins;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.config.FieldUsage;
import de.cxp.ocs.config.SearchConfiguration;
import de.cxp.ocs.elasticsearch.facets.FacetConfigurationApplyer;
import de.cxp.ocs.elasticsearch.query.FiltersBuilder;
import de.cxp.ocs.elasticsearch.query.MasterVariantQuery;
import de.cxp.ocs.elasticsearch.query.analyzer.WhitespaceAnalyzer;
import de.cxp.ocs.elasticsearch.query.builder.ConditionalQueries;
import de.cxp.ocs.elasticsearch.query.builder.ESQueryFactoryBuilder;
import de.cxp.ocs.elasticsearch.query.builder.MatchAllQueryFactory;
import de.cxp.ocs.elasticsearch.query.filter.FilterContext;
import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;
import de.cxp.ocs.elasticsearch.query.model.WordAssociation;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.result.ResultHit;
import de.cxp.ocs.model.result.SearchResult;
import de.cxp.ocs.model.result.SearchResultSlice;
import de.cxp.ocs.spi.search.ESQueryFactory;
import de.cxp.ocs.spi.search.RescorerProvider;
import de.cxp.ocs.spi.search.UserQueryAnalyzer;
import de.cxp.ocs.spi.search.UserQueryPreprocessor;
import de.cxp.ocs.util.ESQueryUtils;
import de.cxp.ocs.util.InternalSearchParams;
import de.cxp.ocs.util.SearchQueryBuilder;
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

	@NonNull
	private final FieldConfigIndex fieldIndex;

	private final List<UserQueryPreprocessor>	userQueryPreprocessors;
	private final UserQueryAnalyzer				userQueryAnalyzer;

	private final FacetConfigurationApplyer facetApplier;

	private final FiltersBuilder filtersBuilder;

	private final ConditionalQueries queryBuilder;

	private final List<RescorerProvider> rescorers;

	private final SortingHandler sortingHandler;
	
	private ScoringCreator scoringCreator;

	private SpellCorrector spellCorrector;

	private final Timer findTimer;
	private final Timer sqbTimer;
	private final Timer inputWordsTimer;
	private final Timer correctedWordsTimer;
	private final Timer resultTimer;
	private final Timer searchRequestTimer;
	private final DistributionSummary summary;

	public Searcher(RestHighLevelClient restClient, SearchContext searchContext, final MeterRegistry registry, final SearchPlugins plugins) {
		this.restClient = restClient;
		this.config = searchContext.config;
		this.registry = registry;
		this.fieldIndex = searchContext.getFieldConfigIndex();

		findTimer = getTimer("find", config.getIndexName());
		resultTimer = getTimer("buildResult", config.getIndexName());
		sqbTimer = getTimer("stagedSearch", config.getIndexName());
		inputWordsTimer = getTimer("inputWordsSearch", config.getIndexName());
		correctedWordsTimer = getTimer("correctedWordsSearch", config.getIndexName());
		searchRequestTimer = getTimer("executeSearchRequest", config.getIndexName());
		summary = DistributionSummary.builder("stagedSearches").tag("indexName", config.getIndexName())
				.register(registry);

		String queryAnalyzerClazz = config.getQueryProcessing().getUserQueryAnalyzer();
		userQueryAnalyzer = SearchPlugins.initialize(queryAnalyzerClazz, plugins.getUserQueryAnalyzers(), config.getPluginConfiguration().get(queryAnalyzerClazz))
				.orElseGet(WhitespaceAnalyzer::new);
		userQueryPreprocessors = searchContext.userQueryPreprocessors;

		sortingHandler = new SortingHandler(fieldIndex, config.getSortConfigs());
		facetApplier = new FacetConfigurationApplyer(searchContext);
		filtersBuilder = new FiltersBuilder(searchContext);
		scoringCreator = new ScoringCreator(searchContext);
		spellCorrector = initSpellCorrection();
		rescorers = SearchPlugins.initialize(config.getRescorers(), plugins.getRescorerProviders(), config.getPluginConfiguration());

		queryBuilder = new ESQueryFactoryBuilder(restClient, searchContext, plugins.getEsQueryFactories()).build();
	}


	private Timer getTimer(final String name, final String indexName) {
		return Timer.builder(name).tag("indexName", indexName).publishPercentiles(0.5, 0.8, 0.9, 0.95)
				.register(registry);
	}

	private SpellCorrector initSpellCorrection() {
		Set<String> spellCorrectionFields = fieldIndex.getFieldsByUsage(FieldUsage.Search).keySet();
		return new SpellCorrector(spellCorrectionFields.toArray(new String[spellCorrectionFields.size()]));
	}

	/**
	 * 
	 * @param parameters
	 *        internal validated state of the parameters
	 * @param customParams
	 * @return search result
	 * @throws IOException
	 */
	// @Timed(value = "find", percentiles = { 0.5, 0.8, 0.95, 0.98 })
	public SearchResult find(InternalSearchParams parameters, Map<String, String> customParams) throws IOException {

		long start = System.currentTimeMillis();

		Iterator<ESQueryFactory> stagedQueryBuilders;
		List<QueryStringTerm> searchWords;
		if (parameters.userQuery != null && !parameters.userQuery.isEmpty()) {
			String searchQuery = parameters.userQuery;
			for (UserQueryPreprocessor preprocessor : userQueryPreprocessors) {
				searchQuery = preprocessor.preProcess(searchQuery);
			}

			searchWords = userQueryAnalyzer.analyze(searchQuery);
			stagedQueryBuilders = queryBuilder.getMatchingFactories(searchWords);

		} else {
			stagedQueryBuilders = Collections.<ESQueryFactory>singletonList(new MatchAllQueryFactory()).iterator();
			searchWords = Collections.emptyList();
		}

		SearchSourceBuilder searchSourceBuilder = SearchSourceBuilder.searchSource().size(parameters.limit)
				.from(parameters.offset);

		List<SortBuilder<?>> variantSortings = sortingHandler.applySorting(parameters.sortings, searchSourceBuilder);
		setFetchSources(searchSourceBuilder, variantSortings);

		FilterContext filterContext = filtersBuilder.buildFilterContext(parameters.filters);
		QueryBuilder postFilter = filterContext.getJoinedPostFilters();
		if (postFilter != null) {
			searchSourceBuilder.postFilter(postFilter);
		}

		if (parameters.isWithFacets()) {
			List<AggregationBuilder> aggregators = facetApplier.buildAggregators(filterContext);
			if (aggregators != null && aggregators.size() > 0) {
				aggregators.forEach(searchSourceBuilder::aggregation);
			}
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
			ESQueryFactory stagedQueryBuilder = stagedQueryBuilders.next();

			MasterVariantQuery searchQuery = stagedQueryBuilder.createQuery(searchWords);
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

			searchSourceBuilder.query(buildFinalQuery(searchQuery, filterContext.getJoinedBasicFilters(), variantSortings));
			addRescorersFailsafe(parameters, customParams, searchSourceBuilder);
			searchResponse = executeSearchRequest(searchSourceBuilder);

			if (log.isDebugEnabled()) {
				log.debug("Query Builder Nr {} ({}) for query '{}' done in {}ms with {} hits", i, stagedQueryBuilder.getName(),
						parameters.userQuery, sw.getTime(), searchResponse.getHits().getTotalHits().value);
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
					searchQuery = stagedQueryBuilder.createQuery(searchWords);
					searchSourceBuilder.query(buildFinalQuery(searchQuery, filterContext.getJoinedBasicFilters(), variantSortings));
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

		SearchResult searchResult = buildResult(parameters, filterContext, searchResponse);
		searchResult.tookInMillis = System.currentTimeMillis() - start;

		findTimer.record(searchResult.tookInMillis, TimeUnit.MILLISECONDS);

		return searchResult;
	}

	private void addRescorersFailsafe(InternalSearchParams parameters, Map<String, String> customParams, SearchSourceBuilder searchSourceBuilder) {
		Iterator<RescorerProvider> rescorerProviders = rescorers.iterator();
		while (rescorerProviders.hasNext()) {
			RescorerProvider rescorerProvider = rescorerProviders.next();
			try {
				rescorerProvider.get(parameters.userQuery, customParams).ifPresent(searchSourceBuilder::addRescorer);
			}
			catch (Exception e) {
				log.error("RescorerProvider {} caused exception when creating rescorer based on userQuery {} and customParams {}!"
						+ "Will remove it until next configuration reload!",
						rescorerProvider.getClass().getCanonicalName(), parameters.userQuery, customParams, e);
				rescorerProviders.remove();
			}
		}
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

	private SearchResult buildResult(InternalSearchParams parameters, FilterContext filterContext, SearchResponse searchResponse) {
		SearchQueryBuilder linkBuilder = new SearchQueryBuilder(parameters);
		SearchResult searchResult = new SearchResult();
		searchResult.inputURI = SearchQueryBuilder.toLink(parameters).toString();
		searchResult.slices = new ArrayList<>(1);

		resultTimer.record(() -> {
			if (searchResponse != null) {
				SearchResultSlice searchResultSlice = toSearchResult(searchResponse, parameters);
				if (parameters.isWithFacets()) {
					searchResultSlice.facets = facetApplier.getFacets(searchResponse.getAggregations(), searchResultSlice.matchCount, filterContext, linkBuilder);
				}
				searchResult.slices.add(searchResultSlice);
			}
		});
		searchResult.sortOptions = sortingHandler.buildSortOptions(linkBuilder);

		return searchResult;
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

		Map<String, SortOrder> sortedFields = sortingHandler.getSortedNumericFields(parameters);

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
				Comparator.comparing(entry -> fieldIndex.getField(entry.getKey()), (f1, f2) -> {
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

}
