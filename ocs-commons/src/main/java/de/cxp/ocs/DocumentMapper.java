package de.cxp.ocs;

import static de.cxp.ocs.config.FieldConstants.NUMBER_FACET_DATA;
import static de.cxp.ocs.config.FieldConstants.PATH_FACET_DATA;
import static de.cxp.ocs.config.FieldConstants.RESULT_DATA;
import static de.cxp.ocs.config.FieldConstants.SCORES;
import static de.cxp.ocs.config.FieldConstants.SEARCH_DATA;
import static de.cxp.ocs.config.FieldConstants.SORT_DATA;
import static de.cxp.ocs.config.FieldConstants.TERM_FACET_DATA;
import static de.cxp.ocs.config.FieldConstants.VARIANTS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.config.FieldUsage;
import de.cxp.ocs.model.index.Attribute;
import de.cxp.ocs.model.index.Category;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;
import lombok.extern.slf4j.Slf4j;

@SuppressWarnings("unchecked")
@Slf4j
public class DocumentMapper {

	public static Document mapToOriginalDocument(String id, Map<String, Object> source, FieldConfigIndex fieldConfig) {
		Document mapped;

		Object variantsData = source.get(VARIANTS);
		if (variantsData != null && variantsData instanceof List && ((List<?>) variantsData).size() > 0) {
			mapped = new Product(id);
			List<?> variantSources = (List<?>) variantsData;
			Document[] variants = new Document[variantSources.size()];
			int i = 0;
			for (Object variantSource : variantSources) {
				variants[i++] = mapToOriginalDocument("null", (Map<String, Object>) variantSource, fieldConfig);
			}
			((Product) mapped).setVariants(variants);
		}
		else {
			mapped = new Document(id);
		}

		Set<String> knownDataFields = new HashSet<>();

		// extract from result-data first, because the values are preserved
		// there in their best state
		extractFieldsFromSubfield(source, mapped, knownDataFields, RESULT_DATA);

		asFacetList(source, PATH_FACET_DATA)
				.ifPresent(facetEntries -> extractValuesFromPathFacetEntries(mapped, fieldConfig, facetEntries, knownDataFields));
		asFacetList(source, NUMBER_FACET_DATA)
				.ifPresent(facetEntries -> extractValuesFromSingleFacetEntry(mapped, fieldConfig, facetEntries, knownDataFields));
		asFacetList(source, TERM_FACET_DATA)
				.ifPresent(facetEntries -> extractValuesFromSingleFacetEntry(mapped, fieldConfig, facetEntries, knownDataFields));

		// check all fields of the source document as well, since there might be
		// some dynamic fields
		extractFieldsFromSubfield(source, mapped, knownDataFields, SEARCH_DATA);
		extractFieldsFromSubfield(source, mapped, knownDataFields, SORT_DATA);
		extractFieldsFromSubfield(source, mapped, knownDataFields, SCORES);
			
		return mapped;
	}

	private static void extractFieldsFromSubfield(Map<String, Object> source, Document mapped, Set<String> knownDataFields, String subField) {
		for (Entry<String, Object> resultValue : asMap(source, subField).entrySet()) {
			String fieldName = resultValue.getKey();
			if (!knownDataFields.contains(fieldName)) {
				knownDataFields.add(fieldName);
				Object value = resultValue.getValue();
				putValue(mapped, fieldName, value);
			}
		}
	}

	private static void putValue(Document mapped, String fieldName, Object value) {
		if (value instanceof Map) {
			Map<String, Object> mapValue = (Map<String, Object>) value;
			if (mapValue.containsKey("value")) {
				fieldName = mapValue.getOrDefault("name", fieldName).toString();
				value = mapValue.get("value");
				String code = Optional.ofNullable(mapValue.get("code")).map(Object::toString).orElse(null);
				mapped.addAttribute(new Attribute(fieldName, code, value.toString()));
			}
			else {
				log.warn("unexpected map value indexed at document={} field={}", mapped.id, fieldName);
			}
		}
		else {
			mapped.data.put(fieldName, value);
		}
	}

	private static void extractValuesFromPathFacetEntries(Document mapped, FieldConfigIndex fieldConfig, List<Map<String, String>> facetEntries, Set<String> knownDataFields) {
		String lastCatPath = "";

		// used to collect the paths grouped by their name
		Map<String, List<Category>> paths = new HashMap<>();
		Optional<Field> primaryCategoryField = fieldConfig.getPrimaryCategoryField();

		for (Map<String, String> facetEntry : facetEntries) {
			String fieldName = facetEntry.get("name");
			Optional<Field> facetField = fieldConfig.getMatchingField(fieldName, FieldUsage.FACET);

			if (facetField.isPresent() && !knownDataFields.contains(fieldName)) {
				String catPath = facetEntry.get("value").toString();
				List<Category> path = paths.computeIfAbsent(fieldName, n -> new ArrayList<>());

				if (lastCatPath.length() > 0 && !catPath.startsWith(lastCatPath)) {
					if (primaryCategoryField.map(catField -> catField.getName().equals(fieldName)).orElse(false)) {
						mapped.addCategory(path.toArray(new Category[path.size()]));
					}
					else {
						joinPathDataField(mapped, fieldName, path);
					}

					// start a new path in case we have several paths for the
					// same data field
					path.clear();
				}

				String[] catPathStrings = StringUtils.split(catPath, '/');
				Category category = new Category(facetEntry.get("id"), catPathStrings[catPathStrings.length - 1]);
				path.add(category);

				lastCatPath = catPath;
			}
		}
		for (Entry<String, List<Category>> pathEntry : paths.entrySet()) {
			String fieldName = pathEntry.getKey();
			List<Category> path = pathEntry.getValue();
			if (path.size() > 0) {
				if (primaryCategoryField.map(catField -> catField.getName().equals(fieldName)).orElse(false)) {
					mapped.addCategory(path.toArray(new Category[path.size()]));
				}
				else {
					joinPathDataField(mapped, fieldName, path);
				}
			}
		}

		// add collected fields afterwards, because on one hand we don't want to
		// collect data from fields we already have, but on the other hand we
		// need to fetch facet values for the same field name several times.
		primaryCategoryField.ifPresent(f -> knownDataFields.add(f.getName()));
		knownDataFields.addAll(mapped.data.keySet());
	}

	private static void joinPathDataField(Document mapped, String fieldName, List<Category> path) {
		Category[] assembledPath = path.toArray(new Category[path.size()]);
		Object previousPathVal = mapped.data.get(fieldName);

		if (previousPathVal != null && previousPathVal instanceof Category[]) {
			ArrayList<Object> collectedPaths = new ArrayList<>();
			collectedPaths.add(mapped.data.get(fieldName));
			collectedPaths.add(assembledPath);
			mapped.data.put(fieldName, collectedPaths);
		}
		else if (previousPathVal != null && previousPathVal instanceof List) {
			((List<Object>) previousPathVal).add(assembledPath);
		}
		else {
			mapped.data.put(fieldName, assembledPath);
		}
	}

	private static void extractValuesFromSingleFacetEntry(Document mapped, FieldConfigIndex fieldConfig, List<Map<String, String>> facetEntries, Set<String> knownDataFields) {
		for (Map<String, String> facetEntry : facetEntries) {
			String name = facetEntry.get("name");
			if (knownDataFields.contains(name)) continue;

			Optional<Field> facetField = fieldConfig.getMatchingField(name, FieldUsage.FACET);
			if (facetField.isPresent()) {
				if (facetEntry.get("id") != null) {
					mapped.addAttribute(new Attribute(name, facetEntry.get("id"), facetEntry.get("value").toString()));
				}
				else {
					mapped.data.putIfAbsent(name, facetEntry.get("value"));
				}
			}
		}

		// add collected fields afterwards, because on one hand we don't want to
		// collect data from fields we already have, but on the other hand we
		// need to fetch facet values for the same field name several times.
		if (mapped.attributes != null) mapped.attributes.forEach(a -> knownDataFields.add(a.getName()));
		knownDataFields.addAll(mapped.data.keySet());
	}

	private static Optional<List<Map<String, String>>> asFacetList(Map<String, Object> source, String sourceDataField) {
		Object sourceData = source.get(sourceDataField);
		if (sourceData != null && sourceData instanceof List && ((List<?>) sourceData).size() > 0) {
			return Optional.of((List<Map<String, String>>) sourceData);
		}
		else {
			return Optional.empty();
		}
	}

	private static Map<String, Object> asMap(Map<String, Object> source, String sourceDataField) {
		Object sourceData = source.get(sourceDataField);
		if (sourceData != null && sourceData instanceof Map) {
			return (Map<String, Object>) sourceData;
		}
		else {
			return Collections.emptyMap();
		}
	}

}
