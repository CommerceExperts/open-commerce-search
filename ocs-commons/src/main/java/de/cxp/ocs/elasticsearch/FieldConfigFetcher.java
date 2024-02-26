package de.cxp.ocs.elasticsearch;

import static de.cxp.ocs.config.FieldConstants.FILTER_DATA;
import static de.cxp.ocs.config.FieldConstants.NUMBER_FACET_DATA;
import static de.cxp.ocs.config.FieldConstants.PATH_FACET_DATA;
import static de.cxp.ocs.config.FieldConstants.RESULT_DATA;
import static de.cxp.ocs.config.FieldConstants.SCORES;
import static de.cxp.ocs.config.FieldConstants.SEARCH_DATA;
import static de.cxp.ocs.config.FieldConstants.SORT_DATA;
import static de.cxp.ocs.config.FieldConstants.TERM_FACET_DATA;
import static de.cxp.ocs.config.FieldConstants.VARIANTS;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetMappingsRequest;
import org.elasticsearch.client.indices.GetMappingsResponse;
import org.elasticsearch.cluster.metadata.MappingMetadata;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.Cardinality;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import de.cxp.ocs.config.*;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class FieldConfigFetcher {

	private static final String			PROPERTIES_PROPERTY	= "properties";

	private final static Set<String>	stringTypes	= Set.of("text", "keyword", "date");
	private final static Set<String>	numberTypes	= Set.of("float", "long");
	private final static Set<String>	rawTypes	= Set.of("rank_features");

	private final RestHighLevelClient	restHLClient;

	public FieldConfiguration fetchConfig(String searchIndex) throws IOException {
		FieldConfiguration result = new FieldConfiguration();
		Map<String, Field> resultFields = result.getFields();

		GetMappingsRequest getMappingsRequest = new GetMappingsRequest();
		getMappingsRequest.indices(searchIndex);
		GetMappingsResponse mappingResponse = restHLClient.indices().getMapping(getMappingsRequest, RequestOptions.DEFAULT);
		MappingMetadata mappingsData = mappingResponse.mappings().values().iterator().next();

		@SuppressWarnings("unchecked")
		Map<String, Object> mappings = (Map<String, Object>) mappingsData.getSourceAsMap().get(PROPERTIES_PROPERTY);
		modifyFields(resultFields, getPropertyBasedFields(mappings, SEARCH_DATA), f -> f.setUsage(FieldUsage.SEARCH));
		modifyFields(resultFields, getPropertyBasedFields(mappings, RESULT_DATA), f -> f.setUsage(FieldUsage.RESULT));
		modifyFields(resultFields, getPropertyBasedFields(mappings, SORT_DATA), f -> f.setUsage(FieldUsage.SORT));
		modifyFields(resultFields, getPropertyBasedFields(mappings, FILTER_DATA), f -> f.setUsage(FieldUsage.FILTER));
		modifyFields(resultFields, getPropertyBasedFields(mappings, SCORES), f -> f.setUsage(FieldUsage.SCORE));

		Map<String, Object> variantMappings = getProperties(mappings, VARIANTS);
		modifyVariantFields(resultFields, getPropertyBasedFields(variantMappings, SEARCH_DATA), f -> f.setUsage(FieldUsage.SEARCH));
		modifyVariantFields(resultFields, getPropertyBasedFields(variantMappings, RESULT_DATA), f -> f.setUsage(FieldUsage.RESULT));
		modifyVariantFields(resultFields, getPropertyBasedFields(variantMappings, SORT_DATA), f -> f.setUsage(FieldUsage.SORT));
		modifyVariantFields(resultFields, getPropertyBasedFields(variantMappings, FILTER_DATA), f -> f.setUsage(FieldUsage.FILTER));
		modifyVariantFields(resultFields, getPropertyBasedFields(variantMappings, SCORES), f -> f.setUsage(FieldUsage.SCORE));

		// get facet fields
		SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource()
				.size(0)
				.aggregation(AggregationBuilders.nested("_master_term_facets", TERM_FACET_DATA)
						.subAggregation(namesAggregation(TERM_FACET_DATA)
								.subAggregation(valueEstimationAgg(TERM_FACET_DATA))))
				.aggregation(AggregationBuilders.nested("_master_path_facets", PATH_FACET_DATA)
						.subAggregation(namesAggregation(PATH_FACET_DATA)
								.subAggregation(valueEstimationAgg(PATH_FACET_DATA))))
				.aggregation(AggregationBuilders.nested("_master_number_facets", NUMBER_FACET_DATA)
						.subAggregation(namesAggregation(NUMBER_FACET_DATA)))
				// no need to check cardinality for number facet
				.aggregation(AggregationBuilders.nested("_variant_term_facets", VARIANTS + "." + TERM_FACET_DATA)
						.subAggregation(namesAggregation(VARIANTS + "." + TERM_FACET_DATA)
								.subAggregation(valueEstimationAgg(VARIANTS + "." + TERM_FACET_DATA))))
				.aggregation(AggregationBuilders.nested("_variant_number_facets", VARIANTS + "." + NUMBER_FACET_DATA)
						.subAggregation(namesAggregation(VARIANTS + "." + NUMBER_FACET_DATA)));
		// no need to check cardinality for number facet

		SearchRequest searchRequest = new SearchRequest(searchIndex).source(sourceBuilder);
		SearchResponse searchResponse = restHLClient.search(searchRequest, RequestOptions.DEFAULT);

		// use facet names to set according field information
		modifyFields(resultFields, extractFacetFields(searchResponse, "_master_term_facets"), f -> f.setUsage(FieldUsage.FACET));
		modifyFields(resultFields, extractFacetFields(searchResponse, "_master_path_facets"), f -> f.setUsage(FieldUsage.FACET).setType(FieldType.CATEGORY));
		modifyFields(resultFields, extractFacetFields(searchResponse, "_master_number_facets"), f -> f.setUsage(FieldUsage.FACET).setType(FieldType.NUMBER));
		modifyVariantFields(resultFields, extractFacetFields(searchResponse, "_variant_term_facets"), f -> f.setUsage(FieldUsage.FACET));
		modifyVariantFields(resultFields, extractFacetFields(searchResponse, "_variant_number_facets"), f -> f.setUsage(FieldUsage.FACET).setType(FieldType.NUMBER));

		return result;
	}

	private final static String	VALUE_ESTIMATE_AGG_NAME	= "_value_estimate";
	private final static String	FACET_NAME_AGG_NAME		= "_names";

	public TermsAggregationBuilder namesAggregation(String fieldPath) {
		return AggregationBuilders.terms(FACET_NAME_AGG_NAME).field(fieldPath + ".name").size(1000);
	}

	private AggregationBuilder valueEstimationAgg(String fieldPath) {
		return AggregationBuilders.cardinality(VALUE_ESTIMATE_AGG_NAME).field(fieldPath + ".value");
	}

	private Set<FacetFetchData> extractFacetFields(SearchResponse searchResponse, String nestedFacetName) {
		Terms aggregation = (Terms) ((Nested) searchResponse.getAggregations().get(nestedFacetName)).getAggregations()
				.get(FACET_NAME_AGG_NAME);
		return aggregation.getBuckets().stream()
				.map(bucket -> new FacetFetchData(bucket.getKeyAsString(), getValueCardinality(bucket)))
				.collect(Collectors.toSet());
	}

	private long getValueCardinality(Bucket bucket) {
		Aggregations subAggs = bucket.getAggregations();
		if (subAggs != null && subAggs.get(VALUE_ESTIMATE_AGG_NAME) != null) {
			Cardinality valueEstimate = (Cardinality) subAggs.get(VALUE_ESTIMATE_AGG_NAME);
			return valueEstimate.getValue();
		}
		return -1L;
	}

	private void modifyFields(Map<String, Field> resultFields, Set<FacetFetchData> fieldsData,
			Consumer<Field> fieldModifier) {
		for (FacetFetchData fieldData : fieldsData) {
			IndexedField field = (IndexedField) resultFields.computeIfAbsent(fieldData.name, IndexedField::new);
			applyFieldDetails(fieldData, field);
			fieldModifier.accept(field);
		}
	}

	private void modifyVariantFields(Map<String, Field> resultFields, Set<FacetFetchData> fieldsData,
			Consumer<Field> fieldModifier) {
		for (FacetFetchData fieldData : fieldsData) {
			IndexedField field = (IndexedField) resultFields.computeIfAbsent(fieldData.name,
					n -> new IndexedField(n).setFieldLevel(FieldLevel.VARIANT));
			if (field.isMasterLevel()) {
				field.setFieldLevel(FieldLevel.BOTH);
			}
			applyFieldDetails(fieldData, field);

			fieldModifier.accept(field);
		}
	}

	private void applyFieldDetails(FacetFetchData fieldData, IndexedField field) {
		if (fieldData.cardinality > 0) {
			field.setValueCardinality((int) fieldData.cardinality);
		}
		if (fieldData.type != null) {
			if (stringTypes.contains(fieldData.type)) {
				field.setType(FieldType.STRING);
			}
			else if (numberTypes.contains(fieldData.type)) {
				field.setType(FieldType.NUMBER);
			}
			else if (rawTypes.contains(fieldData.type)) {
				field.setType(FieldType.RAW);
			}
			else {
				log.debug("unknown fieldData type {} for field {}", fieldData.type, fieldData.name);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> getProperties(Map<String, Object> mappings, String superFieldName) {
		Object superField = mappings.get(superFieldName);
		Object props = null;
		if (superField != null && superField instanceof Map) {
			props = ((Map<String, Object>) superField).get(PROPERTIES_PROPERTY);
		}
		if (props != null && props instanceof Map) {
			return ((Map<String, Object>) props);
		}
		else {
			return Collections.emptyMap();
		}
	}

	@SuppressWarnings("unchecked")
	private Set<FacetFetchData> getPropertyBasedFields(Map<String, Object> mappings, String superFieldName) {
		Object superField = mappings.get(superFieldName);
		Object props = null;
		if (superField != null && superField instanceof Map) {
			props = ((Map<String, Object>) superField).get(PROPERTIES_PROPERTY);
		}
		if (props != null && props instanceof Map) {
			return ((Map<String, Object>) props).entrySet().stream().map(entry -> {
				if (entry.getValue() instanceof Map && ((Map<String, Object>) entry.getValue()).get("type") != null) {
					return new FacetFetchData(entry.getKey(), ((Map<String, Object>) entry.getValue()).get("type").toString());
				}
				else {
					return new FacetFetchData(entry.getKey());
				}
			}).collect(Collectors.toSet());
		}
		else {
			return Collections.emptySet();
		}
	}

	@AllArgsConstructor
	private class FacetFetchData {

		String	name;
		String	type;
		long	cardinality;

		FacetFetchData(String name) {
			this.name = name;
			cardinality = -1;
		}

		FacetFetchData(String name, long cardinality) {
			this.name = name;
			this.cardinality = cardinality;
		}

		FacetFetchData(String name, String type) {
			this.name = name;
			this.type = type;
			cardinality = -1;
		}
	}

}
