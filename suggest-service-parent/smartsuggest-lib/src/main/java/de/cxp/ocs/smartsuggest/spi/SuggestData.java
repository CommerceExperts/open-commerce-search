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
}
