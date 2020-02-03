package de.cxp.ocs.preprocessor.impl;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import de.cxp.ocs.conf.IndexConfiguration;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.preprocessor.DataPreProcessor;
import de.danielnaber.jwordsplitter.AbstractWordSplitter;
import de.danielnaber.jwordsplitter.GermanWordSplitter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Summarize and normalize fields depending on configuration
 * 
 * @author gabriel.bauer
 */
@Slf4j
@NoArgsConstructor
public class WordSplitterDataProcessor implements DataPreProcessor {

	private static final String SIMPLE_TOKENIZER_REGEX = ",|;|!|\\?|/|\\||\\.|\\\\|\\(|\\)|:|\\s+";

	private String fieldName;

	private List<String> fields;

	private AbstractWordSplitter splitter = null;

	public void configure(IndexConfiguration properties) {
		this.fields = new LinkedList<>();
		properties.getDataProcessorConfiguration().getConfiguration()
				.getOrDefault(this.getClass().getSimpleName(), Collections.emptyMap())
				.forEach((key, value) -> {
					if ("fieldName".equals(key)) {
						this.fieldName = value.toString();
					}
					if (key.startsWith("fields.")) {
						this.fields.add(value);
					}

				});
		try {
			this.splitter = new GermanWordSplitter(true);
		}
		catch (IOException e) {
			log.error("Could not create JWordSplitter: {}", e.getMessage());
		}
	}

	@Override
	public boolean process(Document sourceDocument, boolean visible) {
		final LinkedHashSet<String> words = new LinkedHashSet<>();

		try {
			sourceDocument.getData().forEach((k, v) -> {
				if (this.fields.contains(k))
					Arrays.stream(v.toString().split(SIMPLE_TOKENIZER_REGEX)).forEach(cw -> {
						if (!cw.isEmpty()) {
							splitter.splitWord(cw.toString()).forEach(words::add);
						}
					});
			});
		}
		catch (Exception e) {
			log.error("Something went wrong mit the JWordSplitter: {}", e.getMessage());
		}

		final StringBuilder builder = new StringBuilder();
		words.forEach(w -> builder.append(w).append(" "));

		sourceDocument.set(fieldName, builder.toString());

		return true;
	}
}
