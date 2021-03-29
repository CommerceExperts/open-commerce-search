package de.cxp.ocs.elasticsearch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsRequest;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.HasAggregations;
import org.elasticsearch.search.aggregations.bucket.terms.IncludeExclude;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.Cardinality;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

import de.cxp.ocs.config.ConnectionConfiguration;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.config.FieldType;
import de.cxp.ocs.config.FieldUsage;
import de.cxp.ocs.smartsuggest.spi.CommonPayloadFields;
import de.cxp.ocs.smartsuggest.spi.SuggestData;
import de.cxp.ocs.smartsuggest.spi.SuggestDataProvider;
import de.cxp.ocs.smartsuggest.spi.SuggestRecord;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ElasticsearchSuggestDataProvider implements SuggestDataProvider {

	private final static String	EMPTY_STRING	= "";
	private static final String	_NESTED			= "_nested";
	private static final String	_FILTER			= "_filter";
	private static final String	_CARDINALITY	= "_cardinality";
	private static final String	_VALUES			= "_values";

	private final SettingsProxy settings = new SettingsProxy();

	private final RestHighLevelClient client;

	public ElasticsearchSuggestDataProvider() {
		ConnectionConfiguration connectionConf = settings.getConnectionConfig();
		RestClientBuilder restClientBuilder = RestClientBuilderFactory.createRestClientBuilder(connectionConf);
		client = new RestHighLevelClient(restClientBuilder);
	}

	@Override
	public boolean hasData(String indexName) {
		Optional<Boolean> indexEnabled = settings.isIndexEnabled(indexName);
		return indexEnabled.orElseGet(() -> indexExists(indexName));
	}

	private boolean indexExists(String indexName) {
		try {
			return client.indices().exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT);
		}
		catch (IOException e) {
			log.warn("index exists request failed for index {} because of {}", indexName, e.getMessage());
			return false;
		}
	}

	@Override
	public long getLastDataModTime(String indexName) throws IOException {
		long lastModTime = -1;
		try {
			GetSettingsResponse settingsResponse = client.indices()
					.getSettings(new GetSettingsRequest().indices(indexName), RequestOptions.DEFAULT);
			String creationDateTs = settingsResponse.getSetting(settingsResponse.getIndexToSettings().keysIt().next(), "index.creation_date");
			if (creationDateTs != null) {
				lastModTime = Long.parseLong(creationDateTs);
			}
		}
		catch (IOException e) {
			log.error("failed to fetch index creation timestamp for index {} because of IOException: {}", indexName, e.getMessage());
		}

		return lastModTime;
	}

	@Override
	public SuggestData loadData(String indexName) throws IOException {
		SuggestData data = new SuggestData();
		data.setModificationTime(getLastDataModTime(indexName));
		// XXX maybe there should be one "SuggestData" per field?
		data.setType("product_data");
		List<SuggestRecord> suggestRecords = new ArrayList<>();
		data.setSuggestRecords(suggestRecords);

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		List<Field> sourceFields = settings.getSourceFields(indexName);
		Optional<BloomFilter<CharSequence>> dedupFilter = initOptionalDedupFilter(indexName, sourceFields);

		StopWatch fetchWatch = new StopWatch();
		for (Field field : sourceFields) {
			fetchWatch.reset();
			fetchWatch.start();
			if (field.getUsage().contains(FieldUsage.Facet)) {
				suggestRecords.addAll(fetchTermsFromFacetAggregation(indexName, field, dedupFilter));
			}
			else if (field.getUsage().contains(FieldUsage.Sort)) {
				suggestRecords.addAll(fetchTermsFromKeywordsField(indexName, FieldConstants.SORT_DATA, field, dedupFilter));
			}
			else if (field.isMasterLevel()) {
				log.warn("field {} at index {} is not indexed in an optimal way to retrieve suggestions."
						+ " Consider indexing as 'sortable' which is usable for aggregations.", field.getName(), indexName);
				suggestRecords.addAll(fetchTermsFromResultData(indexName, field, dedupFilter));
			}
			else {
				log.error("field {} at index {} is not indexed in a usable way to retrieve suggestions."
						+ " No Suggestions retrieved!"
						+ " Consider indexing as 'sortable' which is usable for aggregations.", field.getName(), indexName);
				fetchWatch.stop();
				continue;
			}
			fetchWatch.stop();
			log.info("fetching suggestions from field {} at index {} took {}ms", field.getName(), indexName, stopWatch.getTime());
		}
		stopWatch.stop();
		log.info("loaded {} suggestRecords from index {} in {}ms", suggestRecords.size(), indexName, stopWatch.getTime());
		return data;
	}

	private Optional<BloomFilter<CharSequence>> initOptionalDedupFilter(String indexName, List<Field> sourceFields) throws IOException {
		if (settings.getIsDeduplicationEnabled(indexName))
			return Optional.of(BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), getDocCount(indexName) * sourceFields.size()));
		else {
			return Optional.empty();
		}
	}

	private long getDocCount(String indexName) throws IOException {
		CountResponse count = client.count(new CountRequest(indexName), RequestOptions.DEFAULT);
		return count.getCount();
	}

	private Collection<SuggestRecord> fetchTermsFromFacetAggregation(String indexName, Field field, Optional<BloomFilter<CharSequence>> dedupFilter) throws IOException {
		final String nestedPath = getNestedPath(field);

		Supplier<AggregationBuilder> nestedAggSupplier = () -> AggregationBuilders.nested(_NESTED, nestedPath);
		Supplier<AggregationBuilder> filterAggSupplier = () -> AggregationBuilders.filter(_FILTER, QueryBuilders.termQuery(nestedPath + ".name", field.getName()));

		return fetchTermsFromAggregation(indexName, field, nestedPath + ".value", dedupFilter, nestedAggSupplier, filterAggSupplier);
	}

	private String getNestedPath(Field field) {
		String nestedPath;
		if (FieldType.category.equals(field.getType())) {
			nestedPath = FieldConstants.PATH_FACET_DATA;
		}
		else {
			nestedPath = FieldConstants.TERM_FACET_DATA;
			if (field.isVariantLevel()) {
				nestedPath = FieldConstants.VARIANTS + "." + nestedPath;
			}
		}
		return nestedPath;
	}

	private Collection<SuggestRecord> fetchTermsFromKeywordsField(String indexName, String fieldPrefix, Field field, Optional<BloomFilter<CharSequence>> dedupFilter)
			throws IOException {
		return fetchTermsFromAggregation(indexName, field, fieldPrefix + "." + field.getName(), dedupFilter);
	}

	@SafeVarargs
	private final Collection<SuggestRecord> fetchTermsFromAggregation(String indexName, Field field, String aggFieldName, Optional<BloomFilter<CharSequence>> dedupFilter,
			Supplier<AggregationBuilder>... superAggSupplier) throws IOException {
		AggregationBuilder[] allAggs = new AggregationBuilder[superAggSupplier.length + 1];
		String[] allAggNames = new String[allAggs.length];
		int lastIndex = superAggSupplier.length;

		for (int i = 0; i < superAggSupplier.length; i++) {
			allAggs[i] = superAggSupplier[i].get();
			allAggNames[i] = allAggs[i].getName();
		}

		// guess the amount of terms that can be fetched from that field
		allAggNames[lastIndex] = _CARDINALITY;
		allAggs[lastIndex] = AggregationBuilders.cardinality(_CARDINALITY).field(aggFieldName);

		SearchSourceBuilder cardinalityReq = new SearchSourceBuilder().size(0)
				.aggregation(subordinateAggregations(allAggs));
		SearchResponse cardinalityResp = execSearch(indexName, cardinalityReq);
		long cardinality = ((Cardinality) extractSubAggregation(cardinalityResp.getAggregations(), allAggNames)).getValue();
		log.info("expecting {} values from field {} at index {}", cardinality, field.getName(), indexName);

		List<SuggestRecord> extractedRecords = new ArrayList<>((int) cardinality);
		int maxFetchSize = settings.getMaxFetchSize(indexName);
		int numPartitions = (int) Math.ceil(((double) cardinality / maxFetchSize));

		// rebuild aggregation builders, because they are statefule and
		// cardinality aggregation is already included to them
		for (int i = 0; i < superAggSupplier.length; i++) {
			allAggs[i] = superAggSupplier[i].get();
		}
		allAggNames[lastIndex] = _VALUES;
		allAggs[lastIndex] = AggregationBuilders.terms(_VALUES)
				.field(aggFieldName)
				.size(maxFetchSize);
		AggregationBuilder compoundAggregation = subordinateAggregations(allAggs);

		SearchSourceBuilder valuesAggReq;
		for (int partition = 0; partition < numPartitions; partition++) {

			// only use partitioning when necessary
			if (numPartitions > 1) {
				((TermsAggregationBuilder) allAggs[lastIndex]).includeExclude(new IncludeExclude(partition, numPartitions));
			}

			valuesAggReq = new SearchSourceBuilder().size(0).aggregation(compoundAggregation);
			SearchResponse valuesAggResp = execSearch(indexName, valuesAggReq);
			Terms valuesAggResult = extractSubAggregation(valuesAggResp.getAggregations(), allAggNames);

			valuesAggResult.getBuckets().stream()
					.filter(b -> dedupFilter.map(filter -> filter.put(b.getKeyAsString().toLowerCase())).orElse(true))
					.map(b -> toSuggestRecord(b.getKeyAsString(), (int) b.getDocCount(), field))
					.forEach(extractedRecords::add);
		}
		log.info("loaded {} values from field {} at index {}", extractedRecords.size(), field.getName(), indexName);

		return extractedRecords;
	}

	private Collection<SuggestRecord> fetchTermsFromResultData(String indexName, Field field, Optional<BloomFilter<CharSequence>> dedupFilter) throws IOException {
		int maxFetchSize = settings.getMaxFetchSize(indexName);
		String prefix;
		if (field.getUsage().contains(FieldUsage.Search)) {
			prefix = FieldConstants.SEARCH_DATA;
		}
		else if (field.getUsage().contains(FieldUsage.Result)) {
			prefix = FieldConstants.RESULT_DATA;
		}
		else {
			log.error("Unexpected state: field {} not indexed in any known way", field.getName());
			return Collections.emptyList();
		}

		// https://www.programcreek.com/2013/10/efficient-counter-in-java/
		Map<String, int[]> fetchedStrings = new HashMap<>();

		// initialize search request
		SearchSourceBuilder fetchSource = new SearchSourceBuilder()
				.size(maxFetchSize)
				.query(QueryBuilders.existsQuery(prefix + "." + field.getName()))
				.sort(SortBuilders.fieldSort("_id"))
				.fetchSource(prefix + "." + field.getName(), null);
		SearchResponse searchResponse;
		do {
			searchResponse = execSearch(indexName, fetchSource);
			SearchHit[] hits = searchResponse.getHits().getHits();
			for (SearchHit hit : hits) {
				@SuppressWarnings("unchecked")
				Object fieldValue = ((Map<String, ?>) hit.getSourceAsMap().get(prefix)).get(field.getName());
				if (fieldValue instanceof List<?>) {
					fieldValue = StringUtils.join((List<?>) fieldValue, " ").toString();
				}
				else if (fieldValue instanceof String[]) {
					fieldValue = StringUtils.join((String[]) fieldValue, " ").toString();
				}
				int[] count = fetchedStrings.computeIfAbsent(fieldValue.toString().toLowerCase(), s -> new int[] { 0 });
				count[0]++;
			}
			if (hits.length > maxFetchSize) {
				fetchSource.searchAfter(hits[hits.length - 1].getRawSortValues());
				searchResponse = execSearch(indexName, fetchSource);
			}
			else {
				searchResponse = null;
			}
		}
		while (searchResponse != null);

		log.info("loaded {} values from field {} at index {}", fetchedStrings.size(), field.getName(), indexName);

		// transform into SuggestRecords
		List<SuggestRecord> records = new ArrayList<>(fetchedStrings.size());
		Iterator<Entry<String, int[]>> entryIterator = fetchedStrings.entrySet().iterator();
		while (entryIterator.hasNext()) {
			Entry<String, int[]> entry = entryIterator.next();
			if (dedupFilter.map(filter -> filter.put(entry.getKey())).orElse(true)) {
				records.add(toSuggestRecord(entry.getKey(), entry.getValue()[0], field));
			}
			entryIterator.remove();
		}
		return records;
	}

	private SuggestRecord toSuggestRecord(String label, int termCount, Field sourceField) {
		Map<String, String> payload = CommonPayloadFields.payloadOfTypeAndCount(sourceField.getName(), String.valueOf(termCount));

		String secondaryText = EMPTY_STRING;
		if (FieldType.category.equals(sourceField.getType()) && label.indexOf('/') > -1) {
			int lastPathIndex = StringUtils.lastIndexOf(label, '/');
			secondaryText = label.substring(0, lastPathIndex);
			payload.put("catPath", secondaryText);
			label = label.substring(lastPathIndex + 1);
		}

		return new SuggestRecord(label, secondaryText, payload, Collections.emptySet(), termCount);
	}

	/**
	 * <p>
	 * subordinates all the given aggregationBuilders below each other.
	 * </p>
	 * <p>
	 * Example: If 3 aggregationBuilders are given (a1, a2, a3) then a2 will be
	 * a subaggregation of a1, and a3 will be a subaggregation of a2!
	 * </p>
	 * 
	 * @param firstAgg
	 * @param subAggs
	 * @return
	 */
	private AggregationBuilder subordinateAggregations(AggregationBuilder... subAggs) {
		AggregationBuilder lastAgg = subAggs[0];
		for (int i = 1; i < subAggs.length; i++) {
			lastAgg.subAggregation(subAggs[i]);
			lastAgg = subAggs[i];
		}
		return subAggs[0];
	}

	private <A extends Aggregation> A extractSubAggregation(Aggregations aggregations, String... aggNames) {
		A extractedAgg = null;
		for (String aggName : aggNames) {
			if (extractedAgg == null) {
				extractedAgg = aggregations.get(aggName);
			}
			else {
				extractedAgg = ((HasAggregations) extractedAgg).getAggregations().get(aggName);
			}
		}
		return extractedAgg;
	}

	private SearchResponse execSearch(String indexName, SearchSourceBuilder searchSource) throws IOException {
		SearchRequest searchRequest = new SearchRequest(indexName).source(searchSource);
		return client.search(searchRequest, RequestOptions.DEFAULT);
	}

}
