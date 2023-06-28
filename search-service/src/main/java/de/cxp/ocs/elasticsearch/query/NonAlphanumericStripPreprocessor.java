package de.cxp.ocs.elasticsearch.query;

import static de.cxp.ocs.elasticsearch.query.analyzer.NonAlphanumericWordSplitAnalyzer.BIND_CHARS;

import org.apache.commons.lang3.StringUtils;

import de.cxp.ocs.spi.search.UserQueryPreprocessor;

public class NonAlphanumericStripPreprocessor implements UserQueryPreprocessor {

	@Override
	public String preProcess(String userQuery) {
		String[] split = userQuery.toLowerCase().split("[^\\p{L}\\p{N}" + BIND_CHARS + "]+");
		StringBuilder joinedWords = new StringBuilder(userQuery.length());
		for (String word : split) {
			word = StringUtils.strip(word, BIND_CHARS);
			if (word.isEmpty()) continue;
			if (joinedWords.length() > 0) {
				joinedWords.append(' ');
			}
			joinedWords.append(word);
		}
		return joinedWords.toString();
	}

}
