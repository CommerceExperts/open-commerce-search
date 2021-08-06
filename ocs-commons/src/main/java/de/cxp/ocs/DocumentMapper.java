package de.cxp.ocs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.config.FieldUsage;
import de.cxp.ocs.model.index.Attribute;
import de.cxp.ocs.model.index.Category;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;

@SuppressWarnings("unchecked")
public class DocumentMapper {

	public static Document mapToOriginalDocument(String id, Map<String, Object> source, FieldConfigIndex fieldConfig) {
		Document mapped;

		Object variantsData = source.get(FieldConstants.VARIANTS);
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

		// FIXME: also restore dynamic fields
		for (Field f : fieldConfig.getFields().values()) {
			if (f.getUsage().isEmpty()) continue;

			EnumSet<FieldUsage> usages = EnumSet.copyOf(f.getUsage());
			if (usages.contains(FieldUsage.RESULT)) {
				Optional.ofNullable(asMap(source, FieldConstants.RESULT_DATA).get(f.getName()))
						.ifPresent(value -> mapped.data.compute(f.getName(), (k, v) -> v == null ? value : v + " " + value));
			}
			else if (usages.contains(FieldUsage.SORT)) {
				Optional.ofNullable(asMap(source, FieldConstants.SORT_DATA).get(f.getName()))
						.ifPresent(value -> mapped.data.compute(f.getName(), (k, v) -> v == null ? value : v + " " + value));
			}
			else if (usages.contains(FieldUsage.SCORE)) {
				Optional.ofNullable(asMap(source, FieldConstants.SCORES).get(f.getName()))
						.ifPresent(value -> mapped.data.compute(f.getName(), (k, v) -> v == null ? value : v + " " + value));
			}
			else if (usages.contains(FieldUsage.FACET)) {
				switch (f.getType()) {
					case CATEGORY:
						asFacetList(source, FieldConstants.PATH_FACET_DATA)
								.ifPresent(facetEntries -> extractValueFromPathFacetEntries(mapped, f, facetEntries));
						break;
					case NUMBER:
						asFacetList(source, FieldConstants.NUMBER_FACET_DATA)
								.ifPresent(facetEntries -> extractValueFromSingleFacetEntry(mapped, f, facetEntries));
						break;
					default:
						asFacetList(source, FieldConstants.TERM_FACET_DATA)
								.ifPresent(facetEntries -> extractValueFromSingleFacetEntry(mapped, f, facetEntries));
				}

			}
			else if (usages.contains(FieldUsage.SEARCH)) {
				Optional.ofNullable(asMap(source, FieldConstants.SEARCH_DATA).get(f.getName()))
						.ifPresent(value -> mapped.data.put(f.getName(), value));
			}
		}

		return mapped;
	}

	private static void extractValueFromPathFacetEntries(Document mapped, Field field, List<Map<String, String>> facetEntries) {
		String lastCatPath = "";
		List<Category> path = new ArrayList<>(facetEntries.size());
		for (Map<String, String> facetEntry : facetEntries) {
			if (facetEntry.get("name").equals(field.getName())) {
				String catPath = facetEntry.get("value").toString();

				if (lastCatPath.length() > 0 && !catPath.startsWith(lastCatPath)) {
					// start a new path
					mapped.categories.add(path.toArray(new Category[path.size()]));
					path.clear();
				}

				String[] catPathStrings = StringUtils.split(catPath, '/');
				Category category = new Category(facetEntry.get("id"), catPathStrings[catPathStrings.length - 1]);
				path.add(category);

				lastCatPath = catPath;
			}
		}
		if (path.size() > 0) {
			mapped.categories.add(path.toArray(new Category[path.size()]));
		}
	}

	private static void extractValueFromSingleFacetEntry(Document mapped, Field field, List<Map<String, String>> facetEntries) {
		for (Map<String, String> facetEntry : facetEntries) {
			if (facetEntry.get("name").equals(field.getName())) {
				if (facetEntry.get("id") != null) {
					mapped.addAttribute(new Attribute(field.getName(), facetEntry.get("id"), facetEntry.get("value").toString()));
				}
				else {
					mapped.data.put(field.getName(), facetEntry.get("value"));
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
