package de.cxp.ocs.preprocessor.impl;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfigAccess;
import de.cxp.ocs.indexer.DocumentPreProcessor;
import de.cxp.ocs.model.index.Category;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.preprocessor.util.CategorySearchData;
import de.cxp.ocs.util.OnceInAWhileRunner;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Extracts the category levels of a single category path field into separate
 * level and a leaf field if the *lvl and *leaf fields exists in the field
 * configuration. Example:
 * 
 * <pre>
 * source:
 * category: [A/B/C/D, Z/X/Y]
 * becomes:
 * category_lvl_0: A,Z
 * category_lvl_1: B,X
 * category_lvl_2: C,Y
 * category_lvl_3: D
 * category_leaf: D,Y
 * </pre>
 * 
 * Will be auto configured if the following
 * configuration entry is present:
 * 
 * <pre>
 *  data-processor-configuration: 
 *   processors:
 *     - ExtractCategoryLevelDataProcessor
 * </pre>
 * 
 * @author hjk
 * @see CategorySearchData
 *
 */
@Slf4j
@NoArgsConstructor
public class ExtractCategoryLevelDataProcessor implements DocumentPreProcessor {

	private static final String	SLASH	= "/";
	private Optional<Field>		categoryField;

	@Override
	public void initialize(FieldConfigAccess fieldConfig, Map<String, String> preProcessorConfig) {
		categoryField = fieldConfig.getPrimaryCategoryField();
	}

	@Override
	public boolean process(Document document, boolean visible) {
		if (isExtract(document)) {
			addCategoryValuesToSource(document);
		}
		return visible;
	}

	private boolean isExtract(Document doc) {
		return categoryField.isPresent() || (doc.getCategories() != null && doc.getCategories().size() > 0);
	}

	/**
	 * Extract category values from all possible sources.
	 * 
	 * @param document
	 */
	private void addCategoryValuesToSource(final Document document) {
		CategorySearchData categorySearchData = new CategorySearchData();
		String categoryFieldName = "category";

		if (categoryField.isPresent()) {
			categoryFieldName = categoryField.get().getName();
			Object categoryPathValue = document.getData().get(categoryFieldName);
			if (categoryPathValue != null) {
				categorySearchData.add(extractCategoryLevels(document, categoryPathValue));
			}

			for (String sourceField : categoryField.get().getSourceNames()) {
				categoryPathValue = document.getData().get(sourceField);
				if (categoryPathValue != null) {
					categorySearchData.add(extractCategoryLevels(document, categoryPathValue));
				}
			}
		}

		if (document.getCategories() != null) {
			categorySearchData.add(extractCategoryLevels(document, document.getCategories()));
		}

		categorySearchData.toSourceItem(document.getData(), categoryFieldName);
	}

	@SuppressWarnings("unchecked")
	private Collection<String[]> extractCategoryLevels(Document document, Object categoryPathValue) {
		if (categoryPathValue == null) return Collections.emptyList();

		if (categoryPathValue instanceof Collection<?>) {
			if (((Collection<?>) categoryPathValue).isEmpty()) return Collections.emptyList();

			Object object0 = ((Collection<?>) categoryPathValue).iterator().next();
			if (object0 instanceof String) {
				return splitAllStringPaths((Collection<String>) categoryPathValue);
			}
			else if (object0 instanceof Category[]) {
				return extractAllCategoryPathNames((Collection<Category[]>) categoryPathValue);
			}
			else {
				OnceInAWhileRunner.runAgainAfter(() -> log.warn(
						"Expected category path to be a collection of String or Category[], instead got '{}' for document with id '{}'",
						categoryPathValue.getClass().getName(), document.getId()),
						this.getClass().getSimpleName(), ChronoUnit.SECONDS, 60);
			}
		}
		else if (categoryPathValue instanceof String) {
			return Collections.singletonList(((String) categoryPathValue).split(SLASH));
		}
		else if (categoryPathValue instanceof Category[]) {
			return Collections.singletonList(extractCategoryPathNames((Category[]) categoryPathValue));
		}
		else {
			OnceInAWhileRunner.runAgainAfter(() -> log.warn(
					"Expected category path to be a String or Category[], instead got '{}' for document with id '{}'",
					categoryPathValue.getClass().getName(), document.getId()),
					this.getClass().getSimpleName(), ChronoUnit.SECONDS, 60);
		}

		return Collections.emptyList();
	}

	private List<String[]> splitAllStringPaths(final Collection<String> paths) {
		List<String[]> splitPaths = new ArrayList<>();
		for (String path : paths) {
			String[] pathLvls = path.split(SLASH);
			splitPaths.add(pathLvls);
		}
		return splitPaths;
	}

	private Collection<String[]> extractAllCategoryPathNames(Collection<Category[]> paths) {
		List<String[]> splitPaths = new ArrayList<>();
		for (Category[] path : paths) {
			splitPaths.add(extractCategoryPathNames(path));
		}
		return splitPaths;
	}

	private String[] extractCategoryPathNames(Category[] path) {
		String[] pathLvls = new String[path.length];
		for (int i = 0; i < path.length; i++) {
			pathLvls[i] = path[i].getName();
		}
		return pathLvls;
	}

}
