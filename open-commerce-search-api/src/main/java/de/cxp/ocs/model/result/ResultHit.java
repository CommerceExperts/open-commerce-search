package de.cxp.ocs.model.result;

import de.cxp.ocs.model.index.Document;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class ResultHit {

	/**
	 * the index name where this hit is comming from.
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
}
