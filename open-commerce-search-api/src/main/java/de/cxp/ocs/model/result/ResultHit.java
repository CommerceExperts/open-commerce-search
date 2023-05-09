package de.cxp.ocs.model.result;

import java.util.HashMap;
import java.util.Map;

import de.cxp.ocs.model.index.Document;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class ResultHit {

	/**
	 * the index name where this hit is coming from.
	 */
	public String index;

	/**
	 * The found document.
	 */
	public Document document;

	/**
	 * Optional: Which parts of the query matched that document.
	 * Mainly used for debugging / search transparency.
	 */
	public String[] matchedQueries;

	/**
	 * Optional: Arbitrary meta data for debug information or other insights about the result hit,
	 * i.e. the score or variant picking details.
	 */
	public Map<String, Object> metaData;

	public ResultHit withMetaData(String key, Object value) {
		if (metaData == null) {
			metaData = new HashMap<>();
		}
		metaData.put(key, value);
		return this;
	}

}
