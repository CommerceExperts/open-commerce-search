package de.cxp.ocs.elasticsearch;

import static com.google.common.base.Predicates.instanceOf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;

import de.cxp.ocs.config.FieldConfigAccess;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.model.index.Category;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ResultMapper {

	private final FieldConfigAccess fieldConfig;

	@SuppressWarnings("unchecked")
	public Document mapToOriginalDocument(String id, Map<String, Object> source) {
		Document mapped;

		Object variantsData = source.get(FieldConstants.VARIANTS);
		if (variantsData != null && isMapArray(variantsData, instanceOf(String.class), instanceOf(Object.class))) {
			mapped = new Product(id);
			Map<String, Object>[] variantSources = (Map<String, Object>[]) variantsData;
			Document[] variants = new Document[variantSources.length];
			for (int i = 0; i < variantSources.length; i++) {
				variants[i] = mapToOriginalDocument(id + "_" + i, variantSources[i]);
			}
			((Product) mapped).setVariants(variants);
		}
		else {
			mapped = new Document(id);
		}

		// remember the fields we have already mapped
		// because some fields could have been indexed into several collections
		Set<String> mappedFields = new HashSet<>();

		fieldConfig.getPrimaryCategoryField().ifPresent(categoryField -> {
			Optional.ofNullable(source.get(FieldConstants.PATH_FACET_DATA))
					.map(pathData -> isMapArray(pathData, instanceOf(String.class), instanceOf(String.class)) ? (Map<String, String>[]) pathData : null)
					.ifPresent(paths -> {
						List<Category[]> allCatPaths = new ArrayList<>();
						List<Category> catPath = new ArrayList<>();
						String lastCatName = null;
						for (Map<String, String> path : paths) {
							String name = path.get("name");
							if (categoryField.getName().equals(name)) {
								mappedFields.add(categoryField.getName());

								String value = path.get("value");
								String[] pathSplit = StringUtils.split(value, '/');
								String catName = pathSplit[pathSplit.length - 1];
								// WIP... it's getting complicated.. not sure
								// anymore if we really need this
								// TODO: finish..

							}
						}
					});
		});

		// values as possible attributes
		for (String superField : new String[] { FieldConstants.TERM_FACET_DATA, FieldConstants.NUMBER_FACET_DATA }) {

		}

		// normal values

		return mapped;
	}

	private boolean isMapArray(Object data, Predicate<Object> keyPredicate, Predicate<Object> valuePredicate) {
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

	private Optional<Map<String, Object>> getSubFields(Map<String, Object> source, String superFieldName) {
		Object data = source.get(superFieldName);
		if (data != null && data instanceof Map) {
			return Optional.of((Map<String, Object>) data);
		}
		return Optional.empty();
	}

}
