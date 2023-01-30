package de.cxp.ocs.elasticsearch;

import static de.cxp.ocs.config.FieldConstants.RESULT_DATA;
import static de.cxp.ocs.config.FieldConstants.VARIANTS;
import static de.cxp.ocs.util.SearchParamsParser.parseFilters;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.*;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder.FilterFunctionBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.rescore.QueryRescorerBuilder;
import org.elasticsearch.search.rescore.RescorerBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.SearchPlugins;
import de.cxp.ocs.config.*;
import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.elasticsearch.facets.FacetConfigurationApplyer;
import de.cxp.ocs.elasticsearch.mapper.ResultMapper;
import de.cxp.ocs.elasticsearch.mapper.VariantPickingStrategy;
import de.cxp.ocs.elasticsearch.prodset.HeroProductHandler;
import de.cxp.ocs.elasticsearch.query.FiltersBuilder;
import de.cxp.ocs.elasticsearch.query.MasterVariantQuery;
import de.cxp.ocs.elasticsearch.query.analyzer.WhitespaceAnalyzer;
import de.cxp.ocs.elasticsearch.query.builder.ConditionalQueries;
import de.cxp.ocs.elasticsearch.query.builder.ESQueryFactoryBuilder;
import de.cxp.ocs.elasticsearch.query.builder.MatchAllQueryFactory;
import de.cxp.ocs.elasticsearch.query.filter.FilterContext;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.model.QueryFilterTerm;
import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;
import de.cxp.ocs.elasticsearch.query.model.WordAssociation;
import de.cxp.ocs.model.params.ProductSet;
import de.cxp.ocs.model.result.ResultHit;
import de.cxp.ocs.model.result.SearchResult;
import de.cxp.ocs.model.result.SearchResultSlice;
import de.cxp.ocs.spi.search.ESQueryFactory;
import de.cxp.ocs.spi.search.RescorerProvider;
import de.cxp.ocs.spi.search.UserQueryAnalyzer;
import de.cxp.ocs.spi.search.UserQueryPreprocessor;
import de.cxp.ocs.util.ESQueryUtils;
import de.cxp.ocs.util.InternalSearchParams;
import de.cxp.ocs.util.SearchParamsParser;
import de.cxp.ocs.util.SearchQueryBuilder;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Searcher {

	private static final Marker QUERY_MARKER = MarkerFactory.getMarker("QUERY");

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

	private final Set<String>				preferredVariantAttributes;
	private final VariantPickingStrategy	variantPickingStrategy;

	private final Timer					findTimer;
	private final Timer					sqbTimer;
	private final Timer					inputWordsTimer;
	private final Timer					correctedWordsTimer;
	private final Timer					resultTimer;
	private final Timer					searchRequestTimer;
	private final DistributionSummary	summary;

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

		preferredVariantAttributes = initVariantHandling();
		variantPickingStrategy = VariantPickingStrategy.valueOf(config.getVariantPickingStrategy());
	}

	private Timer getTimer(final String name, final String indexName) {
		return Timer.builder(name)
				.tag("indexName", indexName)
				.publishPercentiles(0.5, 0.8, 0.9, 0.95)
				.register(registry);
	}

	private SpellCorrector initSpellCorrection() {
		Set<String> spellCorrectionFields = fieldIndex.getFieldsByUsage(FieldUsage.SEARCH).keySet();
		return new SpellCorrector(spellCorrectionFields.toArray(new String[spellCorrectionFields.size()]));
	}

	private Set<String> initVariantHandling() {
		return config.getFacetConfiguration().getFacets().stream()
				.filter(FacetConfig::isPreferVariantOnFilter)
				.map(FacetConfig::getSourceField)
				.filter(facetField -> fieldIndex.getField(facetField).map(Field::isVariantLevel).orElse(false))
				.collect(Collectors.toSet());
	}

	public SearchResult find(InternalSearchParams parameters) throws IOException {
		Sample findTimerSample = Timer.start(Clock.SYSTEM);
		Iterator<ESQueryFactory> stagedQueryBuilders;
		List<QueryStringTerm> searchWords;

		String preprocessedQuery = null;
		if (!parameters.includeMainResult) {
			stagedQueryBuilders = Collections.<ESQueryFactory> singletonList(new MatchAllQueryFactory()).iterator();
			searchWords = Collections.emptyList();
		}
		else if (parameters.userQuery != null && !parameters.userQuery.isEmpty()) {
			preprocessedQuery = parameters.userQuery;
			for (UserQueryPreprocessor preprocessor : userQueryPreprocessors) {
				preprocessedQuery = preprocessor.preProcess(preprocessedQuery);
			}

			searchWords = userQueryAnalyzer.analyze(preprocessedQuery);
			searchWords = handleFiltersOnFields(parameters, searchWords);

			stagedQueryBuilders = queryBuilder.getMatchingFactories(searchWords);
		}
		else {
			stagedQueryBuilders = Collections.<ESQueryFactory> singletonList(new MatchAllQueryFactory()).iterator();
			searchWords = Collections.emptyList();
		}

		FilterContext filterContext = filtersBuilder.buildFilterContext(parameters.filters, parameters.querqyFilters, parameters.withFacets);
		List<SortBuilder<?>> variantSortings = sortingHandler.getVariantSortings(parameters.sortings);

		SearchSourceBuilder searchSourceBuilder = buildBasicSearchSourceBuilder(parameters, filterContext, variantSortings);

		// TODO: add a cache to pick the correct query for known search terms

		// staged search: try each query builder until we get a result
		// + try and use spell correction with first query
		int i = 0;
		SearchResponse searchResponse = null;
		Map<String, WordAssociation> correctedWords = null;
		Sample sqbSample = Timer.start(registry);

		Optional<QueryBuilder> heroProductsQuery = HeroProductHandler.getHeroQuery(parameters);
		boolean isResultSufficient = false;
		while ((searchResponse == null || !isResultSufficient) && stagedQueryBuilders.hasNext()) {
			StopWatch sw = new StopWatch();
			sw.start();
			Sample inputWordsSample = Timer.start(registry);
			ESQueryFactory stagedQueryBuilder = stagedQueryBuilders.next();

			MasterVariantQuery searchQuery = stagedQueryBuilder.createQuery(searchWords);
			if (log.isTraceEnabled()) {
				log.trace("query nr {}: {}: match query = {}", i, stagedQueryBuilder.getName(),
						searchQuery == null ? "NULL"
								: searchQuery.getMasterLevelQuery().toString().replaceAll("[\n\\s]+", " "));
			}
			if (searchQuery == null) continue;

			// this can be the case if arranged search is requested
			// with "includeMainResult=false" but without any valid product set!
			if (searchQuery.getMasterLevelQuery() == null && heroProductsQuery.isEmpty())
				continue;

			if (correctedWords == null && spellCorrector != null
					&& stagedQueryBuilder.allowParallelSpellcheckExecution()
					&& (!searchQuery.isWithSpellCorrection() || stagedQueryBuilders.hasNext())) {
				searchSourceBuilder.suggest(spellCorrector.buildSpellCorrectionQuery(parameters.userQuery));
			}
			else {
				searchSourceBuilder.suggest(null);
			}

			if (parameters.excludedIds != null && parameters.excludedIds.size() > 0) {
				BoolQueryBuilder masterLevelQueryWithExcludes = ESQueryUtils.mapToBoolQueryBuilder(searchQuery.getMasterLevelQuery())
						.mustNot(QueryBuilders.idsQuery().addIds(parameters.excludedIds.toArray(new String[0])));
				searchQuery.setMasterLevelQuery(masterLevelQueryWithExcludes);
			}

			searchSourceBuilder.query(buildFinalQuery(searchQuery, heroProductsQuery, filterContext, variantSortings));

			if (log.isTraceEnabled()) {
				log.trace(QUERY_MARKER, "{ \"user_query\": \"{}\", \"query\": {} }", parameters.userQuery, searchSourceBuilder.toString().replaceAll("[\n\\s]+", " "));
			}

			searchResponse = executeSearchRequest(searchSourceBuilder);

			if (log.isDebugEnabled()) {
				log.debug("query nr {} ({}) for user-query '{}' done in {}ms with {} hits", i, stagedQueryBuilder.getName(),
						parameters.userQuery, sw.getTime(), searchResponse.getHits().getTotalHits().value);
			}
			inputWordsSample.stop(inputWordsTimer);

			isResultSufficient = isResultSufficient(searchResponse, parameters);

			// if we don't have any hits, but there's a chance to get corrected
			// words, then enrich the search words with the corrected words
			if (!isResultSufficient && correctedWords == null && spellCorrector != null && searchResponse.getSuggest() != null) {
				Sample correctedWordsSample = Timer.start(registry);
				correctedWords = spellCorrector.extractRelatedWords(searchWords, searchResponse.getSuggest());
				if (correctedWords.size() > 0) {
					searchWords = SpellCorrector.toListWithAllTerms(searchWords, correctedWords);
				}

				// if the current query builder didn't take corrected words into
				// account, then try again with corrected words
				if (correctedWords.size() > 0 && !searchQuery.isWithSpellCorrection()) {
					searchQuery = stagedQueryBuilder.createQuery(searchWords);
					searchSourceBuilder
							.query(buildFinalQuery(searchQuery, heroProductsQuery, filterContext, variantSortings));
					searchResponse = executeSearchRequest(searchSourceBuilder);
				}
				correctedWordsSample.stop(correctedWordsTimer);
			}

			if (!isResultSufficient && searchQuery.isAcceptNoResult()) {
				break;
			}

			i++;
		}
		sqbSample.stop(sqbTimer);

		SearchResult searchResult = buildResult(parameters, filterContext, searchResponse);

		if (preprocessedQuery != null) {
			searchResult.meta.put("preprocessedQuery", preprocessedQuery);
			searchResult.meta.put("analyzedQuery", StringUtils.join(searchWords));
		}

		summary.record(i);
		findTimerSample.stop(findTimer);

		return searchResult;
	}

	private SearchSourceBuilder buildBasicSearchSourceBuilder(InternalSearchParams parameters, FilterContext filterContext, List<SortBuilder<?>> variantSortings) {
		SearchSourceBuilder searchSourceBuilder = SearchSourceBuilder.searchSource().size(parameters.limit)
				.from(parameters.offset);
		sortingHandler.applySorting(parameters.sortings, searchSourceBuilder);

		if (searchSourceBuilder.sorts() == null || searchSourceBuilder.sorts().isEmpty()) {
			addRescorersFailsafe(parameters, searchSourceBuilder);
		}

		setFetchSources(searchSourceBuilder, variantSortings, parameters.withResultData);

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
		return searchSourceBuilder;
	}

	private boolean isResultSufficient(SearchResponse searchResponse, InternalSearchParams parameters) {
		if (!parameters.includeMainResult)
			return true;

		long totalHits = searchResponse.getHits().getTotalHits().value;
		if (totalHits == 0)
			return false;

		boolean hasFilters = parameters.filters.size() > 0;
		int heroProductCount = parameters.heroProductSets == null ? 0
				: Arrays.stream(parameters.heroProductSets).mapToInt(ProductSet::getSize).sum();
		if (!hasFilters || totalHits > heroProductCount) {
			return totalHits > heroProductCount;
		}
		// if there are hits beyond the current page, they are either all hero-products
		// and it's impossible to check if there are any non-hero products found or we
		// definitely have enough non-hero results
		else if (totalHits > parameters.offset + parameters.limit) {
			return true;
		}

		// With filters it is also very likely that the hero products have been
		// filtered. Therefore we cannot compare sizes anymore and have to check if
		// there is at least 1 non-hero product in the result.
		// (filtering hero products at the "resolve step" is not an option, since it
		// would change the hero-product set which is not desired)

		// so we go trough the returned hits and check if there is one that was not
		// boosted by a hero-product query.
		boolean foundNonHeroProduct = false;
		for (SearchHit hit : searchResponse.getHits().getHits()) {
			boolean isHeroMatch = false;
			for (String matchedQueryName : hit.getMatchedQueries()) {
				if (matchedQueryName.startsWith(HeroProductHandler.QUERY_NAME_PREFIX)) {
					isHeroMatch = true;
					break;
				}
			}
			if (!isHeroMatch) {
				foundNonHeroProduct = true;
				break;
			}
		}

		return foundNonHeroProduct;
	}

	/**
	 * For simple word filters the returned searchWords are used
	 * For any special Querqy style filtering they are put into the parameters object
	 * @param parameters
	 * @param searchWords
	 * @return
	 */
	private List<QueryStringTerm> handleFiltersOnFields(InternalSearchParams parameters, List<QueryStringTerm> searchWords) {
		// Pull all QueryFilterTerm items into a list of its own
		List<QueryStringTerm> remainingSearchWords = new ArrayList<>();

		Map<String, String> filtersAsMap = searchWords.stream()
			.filter(searchWord -> searchWord instanceof QueryFilterTerm || !remainingSearchWords.add(searchWord))
			// Generate the filters and add them
			.map(term -> (QueryFilterTerm) term)
			// TODO: support exclude filters
				.collect(Collectors.toMap(QueryFilterTerm::getField, qf -> toParameterStyle(qf), (word1, word2) -> word1 + SearchQueryBuilder.VALUE_DELIMITER + word2));

		parameters.querqyFilters = convertFiltersMapToInternalResultFilters(filtersAsMap);

		return remainingSearchWords;
	}

	private String toParameterStyle(QueryFilterTerm queryFilter) {
		if (Occur.MUST_NOT.equals(queryFilter.getOccur())) {
			return SearchParamsParser.NEGATE_FILTER_PREFIX + queryFilter.getWord();
		}
		else {
			return queryFilter.getWord();
		}
	}

	private List<InternalResultFilter> convertFiltersMapToInternalResultFilters(Map<String, String> additionalFilters) {
		List<InternalResultFilter> convertedFilters = new ArrayList<>();
		for (String key : additionalFilters.keySet()) {
			convertedFilters = parseFilters(Collections.singletonMap(key, additionalFilters.get(key)), fieldIndex, config.getLocale());
		}
		return convertedFilters;
	}

	private void addRescorersFailsafe(InternalSearchParams parameters, SearchSourceBuilder searchSourceBuilder) {
		Iterator<RescorerProvider> rescorerProviders = rescorers.iterator();

		int maxWindowSize = 0;
		float overallQueryWeight = 1f;
		float overallRescorerWeight = 1f;
		int heroProductsCount = 0;
		if (parameters.heroProductSets != null && parameters.heroProductSets.length > 0) {
			heroProductsCount = Arrays.stream(parameters.heroProductSets).mapToInt(set -> set.ids.length).sum();
		}

		while (rescorerProviders.hasNext()) {
			RescorerProvider rescorerProvider = rescorerProviders.next();
			try {
				Optional<RescorerBuilder<?>> providedRescorer = rescorerProvider.get(parameters.userQuery, parameters.customParams);
				if (providedRescorer.isPresent()) {
					RescorerBuilder<?> rescorer = providedRescorer.get();

					Integer windowSize = rescorer.windowSize();
					if (windowSize > maxWindowSize) {
						maxWindowSize = windowSize;
					}

					// no need to add rescorers, that should not have an impact
					// anyways (hero products should still be boosted to top)
					if (windowSize > heroProductsCount) {
						searchSourceBuilder.addRescorer(rescorer);

						if (rescorer instanceof QueryRescorerBuilder) {
							overallQueryWeight *= ((QueryRescorerBuilder) rescorer).getQueryWeight();
							overallRescorerWeight *= ((QueryRescorerBuilder) rescorer).getRescoreQueryWeight();
						}
					}

				}
			}
			catch (Exception e) {
				log.error("RescorerProvider {} caused exception when creating rescorer based on userQuery {} and customParams {}!"
						+ "Will remove it until next configuration reload!",
						rescorerProvider.getClass().getCanonicalName(), parameters.userQuery, parameters.customParams, e);
				rescorerProviders.remove();
			}
		}

		// if there are rescorers, that lower the query-score effect, add
		// another rescorer that revereses this effect
		if (heroProductsCount > 0 && overallQueryWeight < overallRescorerWeight) {
			float heroRescoreWeight = overallRescorerWeight - overallQueryWeight;
			int heroWindowSize = maxWindowSize;
			HeroProductHandler.getHeroQuery(parameters)
					.ifPresent(heroQuery -> searchSourceBuilder.addRescorer(
							new QueryRescorerBuilder(heroQuery)
									.windowSize(heroWindowSize)
									.setQueryWeight(1)
									.setRescoreQueryWeight(heroRescoreWeight)));
		}
	}

	public SearchResponse executeSearchRequest(SearchSourceBuilder searchSourceBuilder) throws IOException {
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
				Set<String> heroIds;
				if (parameters.heroProductSets != null) {
					heroIds = HeroProductHandler.extractSlices(searchResponse, parameters, searchResult, variantPickingStrategy);
				}
				else {
					heroIds = Collections.emptySet();
				}

				if (parameters.includeMainResult) {
					SearchResultSlice searchResultSlice = toSearchResult(searchResponse, parameters, heroIds);
					if (parameters.isWithFacets()) {
						searchResultSlice.facets = facetApplier.getFacets(searchResponse.getAggregations(), searchResultSlice.matchCount, filterContext, linkBuilder);
					}
					searchResultSlice.label = "main";
					searchResult.slices.add(searchResultSlice);
				}
			}
		});
		searchResult.sortOptions = sortingHandler.buildSortOptions(linkBuilder);
		searchResult.meta = new HashMap<>();

		return searchResult;
	}

	private void setFetchSources(SearchSourceBuilder searchSourceBuilder, List<SortBuilder<?>> variantSortings, boolean fetchSources) {
		if (fetchSources) {
			List<String> includeFields = new ArrayList<>();
			includeFields.add(FieldConstants.RESULT_DATA + ".*");
			if (variantSortings.size() > 0) {
				// necessary for ResultMapper::addSortFieldPrefix
				includeFields.add(FieldConstants.SORT_DATA + ".*");
			}

			searchSourceBuilder.fetchSource(includeFields.toArray(new String[includeFields.size()]), null);
		}
		else {
			searchSourceBuilder.fetchSource(FetchSourceContext.DO_NOT_FETCH_SOURCE);
		}
	}

	private QueryBuilder buildFinalQuery(MasterVariantQuery searchQuery, Optional<QueryBuilder> heroProductsQuery,
			FilterContext filterContext, List<SortBuilder<?>> variantSortings) {
		QueryBuilder masterLevelQuery = searchQuery.getMasterLevelQuery(); // ESQueryUtils.mergeQueries(,

		FilterFunctionBuilder[] masterScoringFunctions = scoringCreator.getScoringFunctions(false);
		if (masterScoringFunctions.length > 0) {
			masterLevelQuery = QueryBuilders.functionScoreQuery(masterLevelQuery, masterScoringFunctions)
					.boostMode(scoringCreator.getBoostMode())
					.scoreMode(scoringCreator.getScoreMode());
		}

		QueryBuilder variantFilterQuery = filterContext.getJoinedBasicFilters().getVariantLevelQuery();
		QueryBuilder variantPostFilters = filterContext.getVariantPostFilters();

		// build query that picks the best matching variants
		QueryBuilder variantsMatchQuery = null;
		boolean variantsOnlyFiltered = variantFilterQuery != null;
		if (searchQuery.getVariantLevelQuery() != null) {
			variantsMatchQuery = searchQuery.getVariantLevelQuery();
			variantsOnlyFiltered = false;
		}
		if (variantFilterQuery != null) {
			variantsMatchQuery = variantsMatchQuery == null ? variantFilterQuery : ESQueryUtils.mapToBoolQueryBuilder(variantsMatchQuery).filter(variantFilterQuery);
		}
		if (variantPostFilters != null) {
			variantsMatchQuery = variantsMatchQuery == null ? variantPostFilters : ESQueryUtils.mapToBoolQueryBuilder(variantsMatchQuery).filter(variantPostFilters);
			variantsOnlyFiltered = false;
		}

		FilterFunctionBuilder[] variantScoringFunctions = variantSortings.isEmpty() ? scoringCreator.getScoringFunctions(true) : new FilterFunctionBuilder[0];
		if (variantScoringFunctions.length > 0) {
			if (variantsMatchQuery == null) variantsMatchQuery = QueryBuilders.matchAllQuery();
			variantsMatchQuery = QueryBuilders.functionScoreQuery(variantsMatchQuery, variantScoringFunctions);
			variantsOnlyFiltered = false;
		}

		// variant inner hits are always retrieved in a should clause,
		// because they may contain optional matchers and post filters
		// only exception: if the variants are only filtered
		boolean isRetrieveVariantInnerHits = false;
		if (variantsMatchQuery != null && !variantsOnlyFiltered) {
			NestedQueryBuilder variantQuery = QueryBuilders.nestedQuery(FieldConstants.VARIANTS, variantsMatchQuery, ScoreMode.Avg)
					.innerHit(getVariantInnerHits(variantSortings));
			masterLevelQuery = ESQueryUtils.mapToBoolQueryBuilder(masterLevelQuery).should(variantQuery);
			isRetrieveVariantInnerHits = true;
		}
		
		// add hero products without the impact of the "natural query"
		if (heroProductsQuery.isPresent()) {
			masterLevelQuery = QueryBuilders.disMaxQuery()
					.add(heroProductsQuery.get())
					.add(masterLevelQuery)
					.tieBreaker(0f);
		}

		// add filters on top of main-query + hero-products
		QueryBuilder masterFilterQuery = filterContext.getJoinedBasicFilters().getMasterLevelQuery();
		if (masterFilterQuery != null) {
			masterLevelQuery = ESQueryUtils.mapToBoolQueryBuilder(masterLevelQuery).filter(masterFilterQuery);
		}

		// if there are hard variant filters, add them as must clause
		if (variantFilterQuery != null) {
			NestedQueryBuilder variantQuery = QueryBuilders.nestedQuery(FieldConstants.VARIANTS, variantFilterQuery, ScoreMode.None);
			if (variantsOnlyFiltered && !isRetrieveVariantInnerHits) {
				variantQuery.innerHit(getVariantInnerHits(variantSortings));
				isRetrieveVariantInnerHits = true;
			}
			masterLevelQuery = ESQueryUtils.mapToBoolQueryBuilder(masterLevelQuery).filter(variantQuery);
		}

		if (variantPickingStrategy.isAllVariantHitCountRequired() && isRetrieveVariantInnerHits) {
			masterLevelQuery = ESQueryUtils.mapToBoolQueryBuilder(masterLevelQuery).should(getAllVariantInnerHits());
		}

		return masterLevelQuery;
	}

	private InnerHitBuilder getVariantInnerHits(List<SortBuilder<?>> variantSortings) {
		InnerHitBuilder variantInnerHits = new InnerHitBuilder()
				.setSize(2)
				.setFetchSourceContext(new FetchSourceContext(true, new String[] { VARIANTS + "." + RESULT_DATA + ".*" }, null));
		if (!variantSortings.isEmpty()) {
			variantInnerHits.setSorts(variantSortings);
		}
		return variantInnerHits;
	}

	private NestedQueryBuilder getAllVariantInnerHits() {
		return QueryBuilders.nestedQuery(FieldConstants.VARIANTS, QueryBuilders.matchAllQuery(), ScoreMode.None)
				.innerHit(new InnerHitBuilder().setSize(0).setName("_all"));
	}

	private SearchResultSlice toSearchResult(SearchResponse search, InternalSearchParams parameters, Set<String> heroIds) {
		SearchHits searchHits = search.getHits();
		SearchResultSlice srSlice = new SearchResultSlice();
		// XXX think about building parameters according to the actual performed
		// search (e.g. with relaxed query or with implicit set filters)
		srSlice.resultLink = SearchQueryBuilder.toLink(parameters).toString();
		srSlice.matchCount = searchHits.getTotalHits().value;

		Map<String, SortOrder> sortedFields = sortingHandler.getSortedNumericFields(parameters);

		boolean preferVariantHit = VariantPickingStrategy.pickAlways.equals(variantPickingStrategy)
				|| (preferredVariantAttributes.size() > 0
						&& parameters.getFilters().stream().anyMatch(f -> preferredVariantAttributes.contains(f.getField().getName())));

		ArrayList<ResultHit> resultHits = new ArrayList<>();
		for (int i = 0; i < searchHits.getHits().length; i++) {
			SearchHit hit = searchHits.getHits()[i];
			if (!heroIds.contains(hit.getId())) {
				ResultHit resultHit = ResultMapper.mapSearchHit(hit, sortedFields, preferVariantHit ? VariantPickingStrategy.pickAlways : variantPickingStrategy);
				resultHits.add(resultHit);
			}
		}
		srSlice.hits = resultHits;
		srSlice.nextOffset = parameters.offset + searchHits.getHits().length;
		return srSlice;
	}

}
