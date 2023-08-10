package de.cxp.ocs.elasticsearch;

import static de.cxp.ocs.config.FieldConstants.RESULT_DATA;
import static de.cxp.ocs.config.FieldConstants.VARIANTS;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
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

import com.google.common.collect.Iterators;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.SearchPlugins;
import de.cxp.ocs.config.*;
import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.elasticsearch.facets.FacetConfigurationApplyer;
import de.cxp.ocs.elasticsearch.mapper.ResultMapper;
import de.cxp.ocs.elasticsearch.mapper.VariantPickingStrategy;
import de.cxp.ocs.elasticsearch.model.query.AnalyzedQuery;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.model.term.AssociatedTerm;
import de.cxp.ocs.elasticsearch.prodset.HeroProductHandler;
import de.cxp.ocs.elasticsearch.prodset.HeroProductsQuery;
import de.cxp.ocs.elasticsearch.query.FiltersBuilder;
import de.cxp.ocs.elasticsearch.query.MasterVariantQuery;
import de.cxp.ocs.elasticsearch.query.analyzer.WhitespaceAnalyzer;
import de.cxp.ocs.elasticsearch.query.builder.ConditionalQueries;
import de.cxp.ocs.elasticsearch.query.builder.ESQueryFactoryBuilder;
import de.cxp.ocs.elasticsearch.query.builder.EnforcedSpellCorrectionQueryFactory;
import de.cxp.ocs.elasticsearch.query.builder.MatchAllQueryFactory;
import de.cxp.ocs.elasticsearch.query.filter.FilterContext;
import de.cxp.ocs.model.params.ProductSet;
import de.cxp.ocs.model.result.ResultHit;
import de.cxp.ocs.model.result.SearchResult;
import de.cxp.ocs.model.result.SearchResultSlice;
import de.cxp.ocs.spi.search.ESQueryFactory;
import de.cxp.ocs.spi.search.RescorerProvider;
import de.cxp.ocs.spi.search.UserQueryAnalyzer;
import de.cxp.ocs.util.DefaultLinkBuilder;
import de.cxp.ocs.util.ESQueryUtils;
import de.cxp.ocs.util.InternalSearchParams;
import de.cxp.ocs.util.TraceOptions.TraceFlag;
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

	private final QueryStringParser queryParser;

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
		UserQueryAnalyzer userQueryAnalyzer = SearchPlugins.initialize(queryAnalyzerClazz, plugins.getUserQueryAnalyzers(), config.getPluginConfiguration().get(queryAnalyzerClazz))
				.orElseGet(WhitespaceAnalyzer::new);
		queryParser = new QueryStringParser(searchContext.userQueryPreprocessors, userQueryAnalyzer, fieldIndex, config.getLocale());

		sortingHandler = new SortingHandler(fieldIndex, config.getSortConfigs());
		facetApplier = new FacetConfigurationApplyer(searchContext, plugins.getFacetCreators());
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
		return find(parameters, new HashMap<>());
	}

	/**
	 * Compute result based on the given parameters. The searchMetaData are attached to the search-result, so by that
	 * you can pass additional information into the result or use it for debugging in case of an error.
	 * 
	 * @param parameters
	 * @param searchMetaData
	 *        in case an exception occurs, this meta data might already be partially filled with data which might be
	 *        useful for debugging
	 * @return
	 * @throws IOException
	 */
	public SearchResult find(InternalSearchParams parameters, Map<String, Object> searchMetaData) throws IOException {
		Sample findTimerSample = Timer.start(Clock.SYSTEM);

		ExtendedQuery parsedQuery = queryParser.preprocessQuery(parameters, searchMetaData);
		boolean isInvalidUserQuery = parsedQuery.isEmpty() && parameters.getUserQuery() != null && !parameters.getUserQuery().isBlank();

		Iterator<ESQueryFactory> stagedQueryBuildersIterator = initializeStageQueryBuilders(parameters, parsedQuery, isInvalidUserQuery);

		FilterContext filterContext = filtersBuilder.buildFilterContext(parameters.filters, parameters.inducedFilters, parameters.withFacets);
		List<SortBuilder<?>> variantSortings = sortingHandler.getVariantSortings(parameters.sortings);

		SearchSourceBuilder searchSourceBuilder = buildBasicSearchSourceBuilder(parameters, filterContext, variantSortings);

		// staged search: try each query builder until we get a result
		// + try and use spell correction with first query
		SearchResponse searchResponse = stagedSearch(parameters, parsedQuery, filterContext, variantSortings, searchSourceBuilder, stagedQueryBuildersIterator, searchMetaData);

		SearchResult searchResult = buildResult(parameters, filterContext, searchResponse);
		searchResult.getMeta().putAll(searchMetaData);

		findTimerSample.stop(findTimer);

		return searchResult;
	}

	private SearchResponse stagedSearch(InternalSearchParams parameters, ExtendedQuery parsedQuery, FilterContext filterContext, List<SortBuilder<?>> variantSortings, SearchSourceBuilder searchSourceBuilder, Iterator<ESQueryFactory> stagedQueryBuildersIterator,
			Map<String, Object> searchMetaData) throws IOException {
		int i = 0;
		SearchResponse searchResponse = null;
		Map<String, AssociatedTerm> correctedWords = null;
		Sample sqbSample = Timer.start(registry);

		Optional<HeroProductsQuery> heroProductsQuery = HeroProductHandler.getHeroQuery(parameters);
		boolean isResultSufficient = false;
		while ((searchResponse == null || !isResultSufficient) && stagedQueryBuildersIterator.hasNext()) {
			StopWatch sw = new StopWatch();
			sw.start();
			Sample inputWordsSample = Timer.start(registry);
			ESQueryFactory stagedQueryBuilder = stagedQueryBuildersIterator.next();

			MasterVariantQuery searchQuery = stagedQueryBuilder.createQuery(parsedQuery);
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
					&& (!searchQuery.isWithSpellCorrection() || stagedQueryBuildersIterator.hasNext())) {
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

			searchSourceBuilder.query(buildFinalQuery(searchQuery, heroProductsQuery.orElse(null), filterContext, variantSortings));

			if (log.isTraceEnabled()) {
				log.trace(QUERY_MARKER, "{ \"user_query\": \"{}\", \"query\": {} }", parameters.userQuery, searchSourceBuilder.toString().replaceAll("[\n\\s]+", " "));
			}
			if (parameters.trace.isSet(TraceFlag.EsQuery)) {
				searchMetaData.put("elasticsearch_query", searchSourceBuilder.toString());
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
				correctedWords = spellCorrector.extractRelatedWords(searchResponse.getSuggest());
				if (correctedWords.size() > 0) {
					AnalyzedQuery queryWithCorrections = SpellCorrector.toListWithAllTerms(parsedQuery.getSearchQuery(), correctedWords);
					parsedQuery = new ExtendedQuery(queryWithCorrections, parsedQuery.getFilters());
					searchMetaData.put("query_corrected", parsedQuery.getSearchQuery().toQueryString());
				}

				// if the current query builder didn't take corrected words into
				// account, then try again with corrected words
				if (correctedWords.size() > 0 && !searchQuery.isWithSpellCorrection()) {
					searchQuery = stagedQueryBuilder.createQuery(parsedQuery);
					searchSourceBuilder.query(buildFinalQuery(searchQuery, heroProductsQuery.orElse(null), filterContext, variantSortings));
					searchResponse = executeSearchRequest(searchSourceBuilder);
					searchMetaData.put("query_correction", correctedWordsSample);
				}
				correctedWordsSample.stop(correctedWordsTimer);
			}
			searchMetaData.put("query_executed", searchQuery.getMasterLevelQuery().queryName());
			searchMetaData.put("query_stage", Optional.ofNullable(parameters.customParams.get("query_stage")).map(Integer::parseInt).orElse(i));

			if (!isResultSufficient && searchQuery.isAcceptNoResult()) {
				break;
			}

			i++;
		}
		summary.record(i);
		sqbSample.stop(sqbTimer);
		return searchResponse;
	}

	private Iterator<ESQueryFactory> initializeStageQueryBuilders(InternalSearchParams parameters, ExtendedQuery parsedQuery, boolean isInvalidUserQuery) {
		Iterator<ESQueryFactory> stagedQueryBuildersIterator;
		if (parsedQuery.isEmpty()) {
			if (isInvalidUserQuery && parsedQuery.getFilters().isEmpty()) {
				stagedQueryBuildersIterator = Collections.emptyIterator();
			}
			else {
				stagedQueryBuildersIterator = Collections.<ESQueryFactory> singletonList(new MatchAllQueryFactory()).iterator();
			}
		}
		else {
			List<ESQueryFactory> stagedQueryBuilders = queryBuilder.getMatchingFactories(parsedQuery);
			// TODO: add a cache to pick the correct query for known search terms
			int queryStage = Optional.ofNullable(parameters.customParams.get("query_stage")).map(Integer::parseInt).orElse(-1);
			if (queryStage >= 0 && queryStage < stagedQueryBuilders.size()) {
				ESQueryFactory singleQueryStage = stagedQueryBuilders.get(queryStage);

				// if we jump to a stage > 0 it is possible that we hit a stage that used corrected queries via the
				// spell-check request from a previous stage. This is checked here and enforced for that stage as well.
				// FIXME: It can still happen, that the uncorrected queries have enough hits and a different result is
				// returned than before.
				for (int i = 0; i < queryStage; i++) {
					if (stagedQueryBuilders.get(i).allowParallelSpellcheckExecution()) {
						singleQueryStage = new EnforcedSpellCorrectionQueryFactory(singleQueryStage);
						break;
					}
				}

				log.info("Jumping to query stage {} with parallel spellcheck {}", queryStage, singleQueryStage instanceof EnforcedSpellCorrectionQueryFactory ? "enabled" : "disabled");
				stagedQueryBuildersIterator = Iterators.singletonIterator(singleQueryStage);
			}
			else {
				stagedQueryBuildersIterator = stagedQueryBuilders.iterator();
			}
		}
		return stagedQueryBuildersIterator;
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
							new QueryRescorerBuilder(heroQuery.getMainQuery())
									.windowSize(heroWindowSize)
									.setQueryWeight(1)
									.setRescoreQueryWeight(heroRescoreWeight)));
		}
	}

	public SearchResponse executeSearchRequest(SearchSourceBuilder searchSourceBuilder) throws IOException {
		Sample sample = Timer.start(registry);
		SearchResponse searchResponse;
		{
			SearchRequest searchRequest = new SearchRequest(StringUtils.split(config.getIndexName(), ','))
					.searchType(SearchType.QUERY_THEN_FETCH).source(searchSourceBuilder);
			searchResponse = restClient.search(searchRequest, RequestOptions.DEFAULT);
		}
		sample.stop(searchRequestTimer);
		return searchResponse;
	}

	private SearchResult buildResult(InternalSearchParams parameters, FilterContext filterContext, SearchResponse searchResponse) {
		DefaultLinkBuilder linkBuilder = new DefaultLinkBuilder(parameters);
		SearchResult searchResult = new SearchResult();
		searchResult.inputURI = DefaultLinkBuilder.toLink(parameters).toString();
		searchResult.slices = new ArrayList<>(1);
		searchResult.sortOptions = sortingHandler.buildSortOptions(linkBuilder);
		searchResult.meta = new HashMap<>();

		if (searchResponse != null) {
			resultTimer.record(() -> {
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
			});
		}
		else {
			searchResult.slices.add(new SearchResultSlice()
					.setLabel("main")
					.setMatchCount(0)
					.setResultLink(DefaultLinkBuilder.toLink(parameters).toString())
					.setHits(Collections.emptyList())
					.setFacets(Collections.emptyList()));
			searchResult.meta.put("error", "invalid user query");
		}

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

	private QueryBuilder buildFinalQuery(MasterVariantQuery searchQuery, @Nullable
	HeroProductsQuery heroProductsQuery,
			FilterContext filterContext, List<SortBuilder<?>> variantSortings) {
		QueryBuilder masterLevelQuery = searchQuery.getMasterLevelQuery();

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

		if (heroProductsQuery != null) {
			variantsMatchQuery = heroProductsQuery.applyToVariantQuery(variantsMatchQuery);
			if (variantsMatchQuery != null) {
				variantsOnlyFiltered = false;
			}
		}

		FilterFunctionBuilder[] variantScoringFunctions = variantSortings.isEmpty() ? scoringCreator.getScoringFunctions(true) : new FilterFunctionBuilder[0];
		if (variantScoringFunctions.length > 0) {
			if (variantsMatchQuery == null) variantsMatchQuery = QueryBuilders.matchAllQuery();
			variantsMatchQuery = QueryBuilders.functionScoreQuery(variantsMatchQuery, variantScoringFunctions)
					.boostMode(scoringCreator.getBoostMode())
					.scoreMode(scoringCreator.getScoreMode());
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
		masterLevelQuery = heroProductsQuery != null ? heroProductsQuery.applyToMasterQuery(masterLevelQuery) : masterLevelQuery;

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
		else if (VariantPickingStrategy.pickAlways.equals(variantPickingStrategy) && !isRetrieveVariantInnerHits) {
			NestedQueryBuilder variantQuery = QueryBuilders.nestedQuery(FieldConstants.VARIANTS, QueryBuilders.matchAllQuery(), ScoreMode.None)
					.innerHit(getVariantInnerHits(variantSortings));
			masterLevelQuery = ESQueryUtils.mapToBoolQueryBuilder(masterLevelQuery).should(variantQuery);
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
		srSlice.resultLink = DefaultLinkBuilder.toLink(parameters).toString();
		srSlice.matchCount = searchHits.getTotalHits().value - heroIds.size();

		Map<String, SortOrder> sortedFields = sortingHandler.getSortedNumericFields(parameters);

		boolean preferVariantHit = VariantPickingStrategy.pickAlways.equals(variantPickingStrategy)
				|| (preferredVariantAttributes.size() > 0
						&& parameters.getFilters().stream().anyMatch(f -> preferredVariantAttributes.contains(f.getField().getName())));

		ArrayList<ResultHit> resultHits = new ArrayList<>();
		for (int i = 0; i < searchHits.getHits().length; i++) {
			SearchHit hit = searchHits.getHits()[i];
			if (!heroIds.contains(hit.getId())) {
				ResultHit resultHit = ResultMapper.mapSearchHit(hit, sortedFields, preferVariantHit ? VariantPickingStrategy.pickAlways : variantPickingStrategy)
						.withMetaData("score", hit.getScore())
						.withMetaData("sort_values", hit.getSortValues())
						.withMetaData("version", hit.getVersion());
				resultHits.add(resultHit);
			}
		}
		srSlice.hits = resultHits;
		srSlice.nextOffset = parameters.offset + searchHits.getHits().length;
		return srSlice;
	}

}
