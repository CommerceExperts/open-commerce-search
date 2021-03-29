package de.cxp.ocs.preprocessor.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import de.cxp.ocs.config.FieldConfigAccess;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.preprocessor.ConfigureableDataprocessor;
import de.cxp.ocs.spi.indexer.DocumentPreProcessor;
import lombok.NoArgsConstructor;

/**
 * {@link ConfigureableDataprocessor} implementation which will make the indexer
 * skip the indexation of matching documents.
 * 
 * <pre>
 * data-processor-configuration: 
 *   processors:
 *     - SkipDocumentDataProcessor
 *  configuration:
 *    SkipDocumentDataProcessor:
 *      type: "[Cc]ontent"
 * </pre>
 * 
 * This would skip all documents where the field "type" matches "[Cc]ontent".
 */
@NoArgsConstructor
public class SkipDocumentDataProcessor implements DocumentPreProcessor {

	private Map<String, Pattern> filterPatterns = new HashMap<>();

	@Override
	public void initialize(FieldConfigAccess fieldConfig, Map<String, String> confMap) {
		confMap.forEach((field, regex) -> filterPatterns.put(field, Pattern.compile(regex)));
	}

	@Override
	public boolean process(Document doc, boolean visible) {
		for (Entry<String, Pattern> filterPattern : filterPatterns.entrySet()) {
			Object fieldValue = doc.getData().get(filterPattern.getKey());
			if (fieldValue == null) continue;
			if (fieldValue instanceof String) {
				if (filterPattern.getValue().matcher((String) fieldValue).matches()) {
					return false;
				}
			}
			else if (fieldValue instanceof Collection<?>) {
				if (((Collection<?>) fieldValue).stream()
						.filter(v -> v != null)
						.anyMatch(v -> filterPattern.getValue().matcher(v.toString()).matches())) {
					return false;
				}
			}
			else if (fieldValue.getClass().isArray()) {
				for (Object v : (Object[]) fieldValue) {
					if (v == null) continue;
					if (filterPattern.getValue().matcher(v.toString()).matches()) {
						return false;
					}
				}
			}
		}
		return true;
	}

}
