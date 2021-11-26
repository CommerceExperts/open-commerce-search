package de.cxp.ocs.elasticsearch;

import static de.cxp.ocs.config.FieldConstants.RESULT_DATA;
import static de.cxp.ocs.config.FieldConstants.VARIANTS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
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
import org.elasticsearch.search.rescore.QueryRescorerBuilder;
import org.elasticsearch.search.rescore.RescorerBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.SearchPlugins;
import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.config.FieldUsage;
import de.cxp.ocs.config.SearchConfiguration;
import de.cxp.ocs.elasticsearch.facets.FacetConfigurationApplyer;
import de.cxp.ocs.elasticsearch.prodset.HeroProductHandler;
import de.cxp.ocs.elasticsearch.query.FiltersBuilder;
import de.cxp.ocs.elasticsearch.query.MasterVariantQuery;
import de.cxp.ocs.elasticsearch.query.analyzer.WhitespaceAnalyzer;
import de.cxp.ocs.elasticsearch.query.builder.ConditionalQueries;
import de.cxp.ocs.elasticsearch.query.builder.ESQueryFactoryBuilder;
import de.cxp.ocs.elasticsearch.query.builder.MatchAllQueryFactory;
import de.cxp.ocs.elasticsearch.query.filter.FilterContext;
import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;
import de.cxp.ocs.elasticsearch.query.model.WordAssociation;
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

	private final Set<String> preferredVariantAttributes;

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

		preferredVariantAttributes = initVariantHandling();
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
				.filter(FacetConfig::isPeferVariantOnFilter)
				.map(FacetConfig::getSourceField)
				.filter(facetField -> fieldIndex.getField(facetField).map(Field::isVariantLevel).orElse(false))
				.collect(Collectors.toSet());
	}

	public SearchResult find(InternalSearchParams parameters) throws IOException {
		Sample findTimerSample = Timer.start(Clock.SYSTEM);
		Iterator<ESQueryFactory> stagedQueryBuilders;
		List<QueryStringTerm> searchWords;
		String preprocessedQuery = null;
		if (parameters.userQuery != null && !parameters.userQuery.isEmpty()) {
			preprocessedQuery = parameters.userQuery;
			for (UserQueryPreprocessor preprocessor : userQueryPreprocessors) {
				preprocessedQuery = preprocessor.preProcess(preprocessedQuery);
			}

			searchWords = userQueryAnalyzer.analyze(preprocessedQuery);
			stagedQueryBuilders = queryBuilder.getMatchingFactories(searchWords);
		} else {
			stagedQueryBuilders = Collections.<ESQueryFactory>singletonList(new MatchAllQueryFactory()).iterator();
			searchWords = Collections.emptyList();
		}

		SearchSourceBuilder searchSourceBuilder = SearchSourceBuilder.searchSource().size(parameters.limit)
				.from(parameters.offset);

		List<SortBuilder<?>> variantSortings = sortingHandler.applySorting(parameters.sortings, searchSourceBuilder);

		if (searchSourceBuilder.sorts() == null || searchSourceBuilder.sorts().isEmpty()) {
			addRescorersFailsafe(parameters, searchSourceBuilder);
		}

		setFetchSources(searchSourceBuilder, variantSortings, parameters.withResultData);

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

		// TODO: add a cache to pick the correct query for known search terms

		// staged search: try each query builder until we get a result
		// + try and use spell correction with first query
		int i = 0;
		SearchResponse searchResponse = null;
		Map<String, WordAssociation> correctedWords = null;
		Sample sqbSample = Timer.start(registry);

		int minHitCount = 1;
		if (parameters.heroProductSets != null) {
			minHitCount = HeroProductHandler.getCorrectedMinHitCount(parameters);
		}
		while ((searchResponse == null || searchResponse.getHits().getTotalHits().value < minHitCount)
				&& stagedQueryBuilders.hasNext()) {
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
			if (searchQuery == null)
				continue;

			if (parameters.heroProductSets != null) {
				HeroProductHandler.extendQuery(searchQuery, parameters);
			}

			if (correctedWords == null && spellCorrector != null
					&& stagedQueryBuilder.allowParallelSpellcheckExecution()
					&& (!searchQuery.isWithSpellCorrection() || stagedQueryBuilders.hasNext())) {
				searchSourceBuilder.suggest(spellCorrector.buildSpellCorrectionQuery(parameters.userQuery));
			} else {
				searchSourceBuilder.suggest(null);
			}

			searchSourceBuilder.query(buildFinalQuery(searchQuery, filterContext.getJoinedBasicFilters(), variantSortings));

			if (log.isTraceEnabled()) {
				log.trace(QUERY_MARKER, "{ \"user_query\": \"{}\", \"query\": {} }", parameters.userQuery, searchSourceBuilder.toString().replaceAll("[\n\\s]+", " "));
			}

			searchResponse = executeSearchRequest(searchSourceBuilder);

			if (log.isDebugEnabled()) {
				log.debug("query nr {} ({}) for user-query '{}' done in {}ms with {} hits", i, stagedQueryBuilder.getName(),
						parameters.userQuery, sw.getTime(), searchResponse.getHits().getTotalHits().value);
			}
			inputWordsSample.stop(inputWordsTimer);

			// if we don't have any hits, but there's a chance to get corrected
			// words, then enrich the search words with the corrected words
			if (searchResponse.getHits().getTotalHits().value < minHitCount && correctedWords == null && spellCorrector != null
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
					if (parameters.heroProductSets != null) {
						HeroProductHandler.extendQuery(searchQuery, parameters);
					}
					searchSourceBuilder.query(buildFinalQuery(searchQuery, filterContext.getJoinedBasicFilters(), variantSortings));
					searchResponse = executeSearchRequest(searchSourceBuilder);
				}
				correctedWordsSample.stop(correctedWordsTimer);
			}

			if (searchResponse.getHits().getTotalHits().value < minHitCount && searchQuery.isAcceptNoResult()) {
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

	private void addRescorersFailsafe(InternalSearchParams parameters, SearchSourceBuilder searchSourceBuilder) {
		Iterator<RescorerProvider> rescorerProviders = rescorers.iterator();

		int maxWindowSize = 0;
		float overallQueryWeight = 1f;
		float overallRescorerWeight = 1f;
		int heroProductsCount = 0;
		if (parameters.heroProductSets.length > 0) {
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
			float heroRescoreWeight = overallQueryWeight - overallRescorerWeight;
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
					heroIds = HeroProductHandler.extractSlices(searchResponse, parameters, searchResult);
				}
				else {
					heroIds = Collections.emptySet();
				}

				SearchResultSlice searchResultSlice = toSearchResult(searchResponse, parameters, heroIds);
				if (parameters.isWithFacets()) {
					searchResultSlice.facets = facetApplier.getFacets(searchResponse.getAggregations(), searchResultSlice.matchCount, filterContext, linkBuilder);
				}
				searchResultSlice.label = "main";
				searchResult.slices.add(searchResultSlice);
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
			// TODO: return search data only if configured
			includeFields.add(FieldConstants.SEARCH_DATA + ".*");
			if (variantSortings.size() > 0) {
				includeFields.add(FieldConstants.SORT_DATA + ".*");
			}

			searchSourceBuilder.fetchSource(includeFields.toArray(new String[includeFields.size()]), null);
		}
		else {
			searchSourceBuilder.fetchSource(FetchSourceContext.DO_NOT_FETCH_SOURCE);
		}
	}


	private QueryBuilder buildFinalQuery(MasterVariantQuery searchQuery, MasterVariantQuery basicFilters,
			List<SortBuilder<?>> variantSortings) {
		QueryBuilder masterLevelQuery = ESQueryUtils.mergeQueries(searchQuery.getMasterLevelQuery(),
				basicFilters.getMasterLevelQuery());

		FilterFunctionBuilder[] masterScoringFunctions = scoringCreator.getScoringFunctions(false);
		if (masterScoringFunctions.length > 0) {
			masterLevelQuery = QueryBuilders.functionScoreQuery(masterLevelQuery, masterScoringFunctions)
					.boostMode(scoringCreator.getBoostMode())
					.scoreMode(scoringCreator.getScoreMode());
		}

		QueryBuilder varFilterQuery = basicFilters.getVariantLevelQuery();
		QueryBuilder variantsMatchQuery;

		// if sorting is available, scoring and boosting not necessary
		if (variantSortings.isEmpty() && searchQuery.getVariantLevelQuery() != null) {
			variantsMatchQuery = QueryBuilders.boolQuery();
			if (varFilterQuery != null) {
				((BoolQueryBuilder) variantsMatchQuery).must(varFilterQuery);
			}
			((BoolQueryBuilder) variantsMatchQuery).should(searchQuery.getVariantLevelQuery().boost(2f));

			FilterFunctionBuilder[] variantScoringFunctions = scoringCreator.getScoringFunctions(true);
			if (variantScoringFunctions.length > 0) {
				variantsMatchQuery = QueryBuilders.functionScoreQuery(variantsMatchQuery, variantScoringFunctions);
			}
		}
		else if (varFilterQuery != null) {
			variantsMatchQuery = varFilterQuery;
		} else {
			variantsMatchQuery = QueryBuilders.matchAllQuery();
		}

		NestedQueryBuilder variantLevelQuery = QueryBuilders
				.nestedQuery(FieldConstants.VARIANTS, variantsMatchQuery, ScoreMode.Avg)
				.innerHit(getVariantInnerHits(variantSortings));

		// if variant query contains hard filters, it should go into a
		// boolean must clause
		if (varFilterQuery != null) {
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
		InnerHitBuilder variantInnerHits = new InnerHitBuilder().setSize(2).setFetchSourceContext(
				new FetchSourceContext(true, new String[] { VARIANTS + "." + RESULT_DATA + ".*" }, null));
		if (!variantSortings.isEmpty()) {
			variantInnerHits.setSorts(variantSortings);
		}
		return variantInnerHits;
	}

	private SearchResultSlice toSearchResult(SearchResponse search, InternalSearchParams parameters, Set<String> heroIds) {
		SearchHits searchHits = search.getHits();
		SearchResultSlice srSlice = new SearchResultSlice();
		// XXX think about building parameters according to the actual performed
		// search (e.g. with relaxed query or with implicit set filters)
		srSlice.resultLink = SearchQueryBuilder.toLink(parameters).toString();
		srSlice.matchCount = searchHits.getTotalHits().value;

		Map<String, SortOrder> sortedFields = sortingHandler.getSortedNumericFields(parameters);

		boolean preferVariantHits = preferredVariantAttributes.size() > 0
				&& ((int) parameters.getFilters().stream()
						.filter(f -> preferredVariantAttributes.contains(f.getField().getName()))
						.count()) == preferredVariantAttributes.size();

		ArrayList<ResultHit> resultHits = new ArrayList<>();
		for (int i = 0; i < searchHits.getHits().length; i++) {
			SearchHit hit = searchHits.getHits()[i];
			if (!heroIds.contains(hit.getId())) {
				ResultHit resultHit = ResultMapper.mapSearchHit(hit, sortedFields, preferVariantHits);
				resultHits.add(resultHit);
			}
		}
		srSlice.hits = resultHits;
		srSlice.nextOffset = parameters.offset + searchHits.getHits().length;
		return srSlice;
	}

}
