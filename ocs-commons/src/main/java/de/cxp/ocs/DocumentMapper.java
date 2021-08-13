package de.cxp.ocs;

import static de.cxp.ocs.config.FieldConstants.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.config.FieldUsage;
import de.cxp.ocs.model.index.Attribute;
import de.cxp.ocs.model.index.Category;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;

@SuppressWarnings("unchecked")
public class DocumentMapper {

	public static Document mapToOriginalDocument(String id, Map<String, Object> source, FieldConfigIndex fieldConfig) {
		Document mapped;

		Object variantsData = source.get(VARIANTS);
		if (variantsData != null && variantsData instanceof List && ((List) variantsData).size() > 0) {
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

		asFacetList(source, PATH_FACET_DATA)
				.ifPresent(facetEntries -> extractValuesFromPathFacetEntries(mapped, fieldConfig, facetEntries));
		asFacetList(source, NUMBER_FACET_DATA)
				.ifPresent(facetEntries -> extractValuesFromSingleFacetEntry(mapped, fieldConfig, facetEntries));
		asFacetList(source, TERM_FACET_DATA)
				.ifPresent(facetEntries -> extractValuesFromSingleFacetEntry(mapped, fieldConfig, facetEntries));

		Set<String> knownDataFields = new HashSet<>(mapped.data.keySet());
		if (mapped.attributes != null) {
			mapped.attributes.forEach(a -> knownDataFields.add(a.name));
		}

		// check all fields of the source document as well, since there might be
		// some dynamic fields
		for (String subField : new String[] { RESULT_DATA, SEARCH_DATA, SORT_DATA, SCORES }) {
			for (Entry<String, Object> resultValue : asMap(source, subField).entrySet()) {
				if (!knownDataFields.contains(resultValue.getKey())) {
					knownDataFields.add(resultValue.getKey());
					mapped.data.put(resultValue.getKey(), resultValue.getValue());
				}
			}
		}
			
		return mapped;
	}

	private static void extractValuesFromPathFacetEntries(Document mapped, FieldConfigIndex fieldConfig, List<Map<String, String>> facetEntries) {
		String lastCatPath = "";

		// used to collect the paths grouped by their name
		Map<String, List<Category>> paths = new HashMap<>();
		Optional<Field> primaryCategoryField = fieldConfig.getPrimaryCategoryField();

		for (Map<String, String> facetEntry : facetEntries) {
			Optional<Field> facetField = fieldConfig.getMatchingField(facetEntry.get("name"), FieldUsage.FACET);

			if (facetField.isPresent()) {
				String fieldName = facetField.get().getName();
				String catPath = facetEntry.get("value").toString();
				List<Category> path = paths.computeIfAbsent(fieldName, n -> new ArrayList<>());

				if (lastCatPath.length() > 0 && !catPath.startsWith(lastCatPath)) {
					if (primaryCategoryField.map(catField -> catField.getName().equals(fieldName)).orElse(false)) {
						mapped.addCategory(path.toArray(new Category[path.size()]));
					}
					else {
						joinPathDataField(mapped, fieldName, path);
					}

					// start a new path
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

	private static void extractValuesFromSingleFacetEntry(Document mapped, FieldConfigIndex fieldConfig, List<Map<String, String>> facetEntries) {
		for (Map<String, String> facetEntry : facetEntries) {
			String name = facetEntry.get("name");
			Optional<Field> facetField = fieldConfig.getMatchingField(name, FieldUsage.FACET);
			if (facetField.isPresent()) {
				if (facetEntry.get("id") != null) {
					mapped.addAttribute(new Attribute(name, facetEntry.get("id"), facetEntry.get("value").toString()));
				}
				else {
					mapped.data.putIfAbsent(name, facetEntry.get("value"));
				}
				break;
			}
		}
	}

	private static Optional<List<Map<String, String>>> asFacetList(Map<String, Object> source, String sourceDataField) {
		Object sourceData = source.get(sourceDataField);
		if (sourceData != null && sourceData instanceof List && ((List) sourceData).size() > 0) {
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

	private static boolean isMapArray(Object data, Predicate<Object> keyPredicate, Predicate<Object> valuePredicate) {
		boolean isWantedMap = data.getClass().isArray()
				&& ((Object[]) data).length > 0
				&& ((Object[]) data)[0] instanceof Map
				&& ((Map<?, ?>[]) data)[0].size() > 0;
		if (isWantedMap && keyPredicate != null && valuePredicate != null) {
			Entry<?, ?> mapContent = ((Map<?, ?>[]) data)[0].entrySet().iterator().next();
			isWantedMap &= keyPredicate.test(mapContent.getKey()) && valuePredicate.test(mapContent.getValue());
		}
		return isWantedMap;
	}
}
