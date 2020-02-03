package de.cxp.ocs.preprocessor.impl;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.cxp.ocs.conf.IndexConfiguration;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldType;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.preprocessor.DataPreProcessor;
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
public class ExtractCategoryLevelDataProcessor implements DataPreProcessor {

	private Optional<Field> categoryField;

	public void configure(final IndexConfiguration properties) {
		categoryField = properties.getFieldConfiguration().getField(FieldType.category);
	}

	@Override
	public boolean process(Document document, boolean visible) {
		if (isExtract(document)) {
			addCategoryValuesToSource(document.getData());
		}
		return visible;
	}

	private boolean isExtract(Document doc) {
		return categoryField.isPresent() && doc.getData().containsKey(categoryField.get().getName());
	}

	private void addCategoryValuesToSource(final Map<String, Object> sourceData) {
		Object categoryPathValue = sourceData.get(categoryField.get().getName());
		if (categoryPathValue instanceof List<?>) {
			@SuppressWarnings("unchecked")
			CategorySearchData categorySearchData = new CategorySearchData((List<String>) categoryPathValue);
			categorySearchData.toSourceItem(sourceData, categoryField.get());
		}
		else {
			OnceInAWhileRunner.runAgainAfter(() -> log.warn(
					"Expected category path to be an list, instead got '{}' for record '{}'", categoryPathValue != null
							? categoryPathValue.getClass().getName()
							: "null", sourceData), this.getClass().getSimpleName(), ChronoUnit.SECONDS,
					60);
		}
	}
}
