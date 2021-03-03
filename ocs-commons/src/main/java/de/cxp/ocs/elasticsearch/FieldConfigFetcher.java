package de.cxp.ocs.elasticsearch;

import static de.cxp.ocs.config.FieldConstants.NUMBER_FACET_DATA;
import static de.cxp.ocs.config.FieldConstants.PATH_FACET_DATA;
import static de.cxp.ocs.config.FieldConstants.TERM_FACET_DATA;
import static de.cxp.ocs.config.FieldConstants.VARIANTS;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
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
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfiguration;
import de.cxp.ocs.config.FieldLevel;
import de.cxp.ocs.config.FieldType;
import de.cxp.ocs.config.FieldUsage;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FieldConfigFetcher {

	private final RestHighLevelClient restHLClient;

	public FieldConfiguration fetchConfig(String searchIndex) throws IOException {
		FieldConfiguration result = new FieldConfiguration();
		Map<String, Field> resultFields = result.getFields();

		GetMappingsRequest getMappingsRequest = new GetMappingsRequest();
		getMappingsRequest.indices(searchIndex);
		GetMappingsResponse mappingResponse = restHLClient.indices().getMapping(getMappingsRequest, RequestOptions.DEFAULT);
		MappingMetaData mappingsData = mappingResponse.mappings().values().iterator().next();

		@SuppressWarnings("unchecked")
		Map<String, Object> mappings = (Map<String, Object>) mappingsData.getSourceAsMap().get("properties");
		modifyFields(resultFields, getProperties(mappings, "searchData").keySet(), f -> f.setUsage(FieldUsage.Search));
		modifyFields(resultFields, getProperties(mappings, "resultData").keySet(), f -> f.setUsage(FieldUsage.Result));
		modifyFields(resultFields, getProperties(mappings, "sortData").keySet(), f -> f.setUsage(FieldUsage.Sort));
		modifyFields(resultFields, getProperties(mappings, "scores").keySet(), f -> f.setUsage(FieldUsage.Score).setType(FieldType.number));

		Map<String, Object> variantMappings = getProperties(mappings, "variants");
		modifyVariantFields(resultFields, getProperties(variantMappings, "searchData").keySet(), f -> f.setUsage(FieldUsage.Search));
		modifyVariantFields(resultFields, getProperties(variantMappings, "resultData").keySet(), f -> f.setUsage(FieldUsage.Result));
		modifyVariantFields(resultFields, getProperties(variantMappings, "sortData").keySet(), f -> f.setUsage(FieldUsage.Sort));
		modifyVariantFields(resultFields, getProperties(variantMappings, "scores").keySet(), f -> f.setUsage(FieldUsage.Score).setType(FieldType.number));

		// get facet fields
		SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource()
				.size(0)
				.aggregation(AggregationBuilders.nested("_master_term_facets", TERM_FACET_DATA)
						.subAggregation(AggregationBuilders.terms("_names").field(TERM_FACET_DATA + ".name").size(1000)))
				.aggregation(AggregationBuilders.nested("_master_path_facets", PATH_FACET_DATA)
						.subAggregation(AggregationBuilders.terms("_names").field(PATH_FACET_DATA + ".name").size(1000)))
				.aggregation(AggregationBuilders.nested("_master_number_facets", NUMBER_FACET_DATA)
						.subAggregation(AggregationBuilders.terms("_names").field(NUMBER_FACET_DATA + ".name").size(1000)))
				.aggregation(AggregationBuilders.nested("_variant_term_facets", VARIANTS + "." + TERM_FACET_DATA)
						.subAggregation(AggregationBuilders.terms("_names").field(VARIANTS + "." + TERM_FACET_DATA + ".name").size(1000)))
				.aggregation(AggregationBuilders.nested("_variant_number_facets", VARIANTS + "." + NUMBER_FACET_DATA)
						.subAggregation(AggregationBuilders.terms("_names").field(VARIANTS + "." + NUMBER_FACET_DATA + ".name").size(1000)));

		SearchRequest searchRequest = new SearchRequest(searchIndex).source(sourceBuilder);
		SearchResponse searchResponse = restHLClient.search(searchRequest, RequestOptions.DEFAULT);

		// use facet names to set according field information
		modifyFields(resultFields, extractFacetFields(searchResponse, "_master_term_facets"), f -> f.setUsage(FieldUsage.Facet));
		modifyFields(resultFields, extractFacetFields(searchResponse, "_master_path_facets"), f -> f.setUsage(FieldUsage.Facet).setType(FieldType.category));
		modifyFields(resultFields, extractFacetFields(searchResponse, "_master_number_facets"), f -> f.setUsage(FieldUsage.Facet).setType(FieldType.number));
		modifyVariantFields(resultFields, extractFacetFields(searchResponse, "_variant_term_facets"), f -> f.setUsage(FieldUsage.Facet));
		modifyVariantFields(resultFields, extractFacetFields(searchResponse, "_variant_number_facets"), f -> f.setUsage(FieldUsage.Facet).setType(FieldType.number));

		// deduplicate FieldUsages which can't be a Set natively because of some
		// spring config mapper thing...
		result.getFields().values().forEach(f -> {
			if (f.getUsage().size() > 1) {
				f.setUsage(EnumSet.copyOf(f.getUsage()));
			}
		});

		return result;
	}

	private Set<String> extractFacetFields(SearchResponse searchResponse, String nestedFacetName) {
		Terms aggregation = (Terms) ((Nested) searchResponse.getAggregations().get(nestedFacetName)).getAggregations().get("_names");
		return aggregation.getBuckets().stream().map(Bucket::getKeyAsString).collect(Collectors.toSet());
	}

	private void modifyFields(Map<String, Field> resultFields, Set<String> fieldNames, Consumer<Field> fieldModifier) {
		for (String fieldName : fieldNames) {
			fieldModifier.accept(resultFields.computeIfAbsent(fieldName, Field::new));
		}
	}

	private void modifyVariantFields(Map<String, Field> resultFields, Set<String> fieldNames, Consumer<Field> fieldModifier) {
		for (String fieldName : fieldNames) {
			Field field = resultFields.computeIfAbsent(fieldName, n -> new Field(n).setFieldLevel(FieldLevel.variant));
			if (field.isMasterLevel()) {
				field.setFieldLevel(FieldLevel.both);
			}
			fieldModifier.accept(field);
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> getProperties(Map<String, Object> mappings, String superFieldName) {
		Object superField = mappings.get(superFieldName);
		Object props = null;
		if (superField != null && superField instanceof Map)
			props = ((Map<String, Object>) superField).get("properties");
		if (props != null && props instanceof Map)
			return (Map<String, Object>) props;
		else
			return Collections.emptyMap();
	}


}
