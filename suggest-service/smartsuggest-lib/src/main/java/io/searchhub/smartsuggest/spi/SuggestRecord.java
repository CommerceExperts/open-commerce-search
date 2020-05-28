package io.searchhub.smartsuggest.spi;

import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SuggestRecord {

	/**
	 * This is what will be returned inside the resulting suggestions.
	 * If this object is null, the primary text will be used as payload.
	 * 
	 * This string can also contain json data, which however will be escaped at
	 * a suggest response.
	 * 
	 * example:
	 * 
	 * <pre>
	 * [
	 *  { "payload": "foobar" },
	 *  { "payload": "{\"key\": \"value\"}" }
	 * ]
	 */
	String payload;

	/**
	 * Will be searched in first place. If there are enough suggestions matching
	 * the primary text no suggestions will be retrieved by the secondary text.
	 */
	String primaryText;

	/**
	 * Text (concatenated words) that will be searched in case there are no or
	 * not enough matches by searching the primary text.
	 * 
	 * This text won't be indexed for shingle search or fuzzy search.
	 * 
	 * This text MUST NOT extend 32kb and in case it does, it will be truncated.
	 */
	String secondaryText;

	/**
	 * The internal naming is context. These values that can be attached to a
	 * record and can be used as filter or to do contextual boosting at search
	 * time.
	 */
	Set<String> tags;

	/**
	 * A weight value that ranks that record against other records in the
	 * result.
	 * 
	 * The range of the weights for all records should either be (-10,10) or
	 * [10,MAX], because values between -10 and 10 will be multiplied by 10
	 * internally by Lucene.
	 * 
	 * @see https://issues.apache.org/jira/browse/LUCENE-8343
	 */
	long weight;

}
