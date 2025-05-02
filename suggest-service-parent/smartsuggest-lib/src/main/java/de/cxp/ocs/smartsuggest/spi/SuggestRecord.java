package de.cxp.ocs.smartsuggest.spi;

import java.util.Map;
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
	 * <p>
	 * Will be searched in first place. Normally this should be the label that
	 * is shown to the user afterwards.
	 * </p>
	 * <p>
	 * If there are enough suggestions matching the primary text no suggestions
	 * will be retrieved by the secondary text.
	 * </p>
	 */
	String primaryText;

	/**
	 * <p>
	 * Text (concatenated words) that will be searched in case there are no or
	 * not enough matches by searching the primary text.
	 * </p>
	 * <p>
	 * This text MUST NOT extend 32kb and in case it does, it will be truncated.
	 * </p>
	 */
	String secondaryText;

	/**
	 * This is what will be returned inside the resulting suggestions.
	 * Can be null.
	 */
	Map<String, String> payload;

	/**
	 * <p>
	 * The lucene naming for this is 'context'. These values that can be
	 * attached to a record and can be used as filter or to do contextual
	 * boosting at search time.
	 * </p>
	 * <p>
	 * Keep in mind, that tags only work for non-fuzzy suggesters.
	 * So if a filter is used when fetching suggestions, no fuzzy matches will
	 * be made.
	 * </p>
	 * TODO A workaround might be implemented soon.
	 */
	Set<String> tags;

	/**
	 * <p>
	 * A weight value that ranks that record against other records in the
	 * result.
	 * <p>
	 * </p>
	 * The range of the weights for all records should either be (-10,10) or
	 * [10,MAX], because values between -10 and 10 will be multiplied by 10
	 * internally by Lucene.
	 * </p>
	 *
	 * @see <a href="https://issues.apache.org/jira/browse/LUCENE-8343">LUCENE-8343</a>
	 */
	long weight;

}
