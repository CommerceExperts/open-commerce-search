package de.cxp.ocs.preprocessor.impl;

import static de.cxp.ocs.util.StringUtils.asciify;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import de.cxp.ocs.config.FieldConfigAccess;
import de.cxp.ocs.config.FieldUsage;
import de.cxp.ocs.indexer.DocumentPreProcessor;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.util.OnceInAWhileRunner;
import de.cxp.ocs.util.Util;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Converts alphabetic, numeric, and symbolic Unicode characters which are not
 * in the first 127 ASCII characters (the "Basic Latin" Unicode block) into
 * their ASCII equivalents, if one exists for every searchable field.
 */
@Slf4j
@NoArgsConstructor
public class AsciiFoldingDataProcessor implements DocumentPreProcessor {

	private List<String> searchableFields;

	@Override
	public void initialize(FieldConfigAccess fieldConfig, Map<String, String> preProcessorConfig) {
		searchableFields = new ArrayList(fieldConfig.getFieldsByUsage(FieldUsage.Search).keySet());
	}

	@Override
	public boolean process(Document sourceDocument, boolean visible) {
		Map<String, Object> sourceData = sourceDocument.getData();
		searchableFields.forEach(fieldName -> {
			Object value = sourceData.get(fieldName);
			if (value instanceof String) {
				sourceDocument.set(fieldName, asciify((String) value));
			}
			else if (Util.isStringCollection(value)) {
				@SuppressWarnings("unchecked")
				final Collection<String> valueCollection = (Collection<String>) value;
				final Collection<String> cleandValues = new ArrayList<>(valueCollection.size());
				valueCollection.forEach(collectionValue -> {
					cleandValues.add(asciify(collectionValue));
				});
				sourceData.put(fieldName, cleandValues);
			}
			else {
				OnceInAWhileRunner.runAgainAfter(() -> log.warn(
						"Value '{}' could not be ASCII folded for field '{}' as the value is not a String or a Collection of Strings",
						value, fieldName), this.getClass().getSimpleName() + fieldName, ChronoUnit.SECONDS, 60);
			}
		});
		return visible;
	}
}
