package de.cxp.ocs.smartsuggest.spi;

import java.util.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SuggestData {

	/**
	 * type/name of these suggest data. All suggest data with same type will be
	 * indexed together and also be part of the same result. Different suggest
	 * types will be in separated blocks at the result.
	 */
	String type;

	@Builder.Default
	Locale locale = Locale.ROOT;

	/**
	 * aka stopwords =)
	 */
	@Builder.Default
	Set<String> wordsToIgnore = Collections.emptySet();

	@Builder.Default
	Iterable<SuggestRecord> suggestRecords = new ArrayList<>();

	/**
	 * <p>
	 * Optional: Time when this data was created in epoch millis. Should be the
	 * same as stated by the {@link SuggestDataProvider}.
	 * </p>
	 * <p>
	 * If this timestamp is provided, a sanity check is done inside the update
	 * process, if this data has the same timestamp as stated by the data
	 * provider. If this sanity check won't pass, the data won't be updated.
	 * </p>
	 */
	long modificationTime;

	/**
	 * Queries that are suggested prior to all other queries, as soon as the complete input query is typed. i.e. for the
	 * input "shoes" it may show "sneakers".
	 */
	Map<String, List<String>> sharpenedQueries;

	/**
	 * Queries that are suggested in case the complete input does not yield enough results.
	 */
	Map<String, List<String>> relaxedQueries;
}
