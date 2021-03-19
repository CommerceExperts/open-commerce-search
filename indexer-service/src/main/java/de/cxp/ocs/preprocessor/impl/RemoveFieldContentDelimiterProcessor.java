package de.cxp.ocs.preprocessor.impl;

import java.util.Map;

import de.cxp.ocs.config.FieldConfiguration;
import de.cxp.ocs.indexer.DocumentPreProcessor;
import de.cxp.ocs.model.index.Document;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class RemoveFieldContentDelimiterProcessor implements DocumentPreProcessor {

	private static final String	FIELD_CONTENT_DELIMITER_MATCH_PATTERN		= "^\\.\\.(.*)\\.\\.$|\\.\\.\\|\\.\\.|\\.\\.\\,\\.\\.";
	private static final String	FIELD_CONTENT_REPLACE_PATTERN_START			= "^\\.\\.";
	private static final String	FIELD_CONTENT_REPLACE_PATTERN_MIDDLE_PIPE	= "\\.\\.\\|\\.\\.";
	private static final String	FIELD_CONTENT_REPLACE_PATTERN_MIDDLE_COMMA	= "\\.\\.\\,\\.\\.";
	private static final String	FIELD_CONTENT_REPLACE_PATTERN_END			= "\\.\\.$";
	private static final String	FIELD_CONTENT_DELIMITER_REPLACEMENT			= "";
	private static final String	FIELD_CONTENT_DELIMITER_REPLACEMENT_PIPE	= "|";
	private static final String	FIELD_CONTENT_DELIMITER_REPLACEMENT_COMMA	= ",";

	@Override
	public void initialize(FieldConfiguration fieldConfig, Map<String, String> confMap) {}
	
	public boolean process(Document document, boolean visible) {
		Map<String, Object> sourceData = document.getData();
		for (String key : sourceData.keySet()) {
			if (sourceData.get(key) instanceof String && ((String) sourceData.get(key)).matches(
					FIELD_CONTENT_DELIMITER_MATCH_PATTERN)) {
				String record = ((String) sourceData.get(key));
				record = record.replaceAll(FIELD_CONTENT_REPLACE_PATTERN_START, FIELD_CONTENT_DELIMITER_REPLACEMENT)
						.replaceAll(FIELD_CONTENT_REPLACE_PATTERN_MIDDLE_PIPE, FIELD_CONTENT_DELIMITER_REPLACEMENT_PIPE)
						.replaceAll(FIELD_CONTENT_REPLACE_PATTERN_MIDDLE_COMMA,
								FIELD_CONTENT_DELIMITER_REPLACEMENT_COMMA)
						.replaceAll(FIELD_CONTENT_REPLACE_PATTERN_END, FIELD_CONTENT_DELIMITER_REPLACEMENT);
				sourceData.put(key, record);
			}
		}
		return visible;
	}

}
