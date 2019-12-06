package de.cxp.ocs.model.result;

import de.cxp.ocs.model.index.Document;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ResultHit {

	public String index;

	public Document document;

	public String[] matchedQueries;
}
