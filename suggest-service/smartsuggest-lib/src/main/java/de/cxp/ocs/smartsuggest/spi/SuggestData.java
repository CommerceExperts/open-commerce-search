package de.cxp.ocs.smartsuggest.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SuggestData {

	/**
	 * type/name of these suggest data. All suggest data with same type will be
	 * indexed together and also be part of the same result. Different suggest
	 * types will be in separated blocks at the result.
	 */
	String type;

	Locale locale;

	/**
	 * aka stopwords =)
	 */
	Set<String> wordsToIgnore;

	List<SuggestRecord> suggestRecords = new ArrayList<>();

}
