package de.cxp.ocs.smartsuggest.querysuggester.lucene;

import de.cxp.ocs.smartsuggest.spi.SuggestRecord;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.WordlistLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.Set;

import static java.util.Collections.emptySet;
import static java.util.Collections.singletonMap;

@Slf4j
class TestSetupUtil {

	public static CharArraySet getWordSet(Locale locale) throws IOException {
		if (locale != null) {
			final String languageCode = locale.getLanguage();
			String stopWordsFileName = null;
			switch (languageCode) {
				case "de":
					stopWordsFileName = "de.txt";
					break;
				default:
					log.info("No stopwords file for locale '{}'", locale);
			}

			if (stopWordsFileName != null) {
				final InputStream stopWordInputStream = LuceneQuerySuggester.class.getResourceAsStream("/stopwords/" + stopWordsFileName);
				final CharArraySet stopWordSet = WordlistLoader.getWordSet(new InputStreamReader(stopWordInputStream));
				return stopWordSet;
			}
		}

		return null;
	}

	static SuggestRecord asSuggestRecord(String bestQuery, Set<String> searchData, int weight) {
		return new SuggestRecord(bestQuery, StringUtils.join(searchData, " "), singletonMap("label", bestQuery), emptySet(), weight);
	}

	static SuggestRecord asSuggestRecord(String searchString, String bestQuery, int weight) {
		return new SuggestRecord(bestQuery, searchString, singletonMap("label", bestQuery), emptySet(), weight);
	}

	static SuggestRecord asSuggestRecord(String searchString, String bestQuery, int weight, Set<String> tags) {
		return new SuggestRecord(bestQuery, searchString, singletonMap("label", bestQuery), tags, weight);
	}
}
