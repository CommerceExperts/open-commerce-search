package de.cxp.ocs.model.index;

import de.cxp.ocs.api.indexer.ImportSession;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "composite object that is used to add documents to the index.")
@Data
public class BulkImportData {

	@Schema(required = true)
	public ImportSession	session;

	@Schema(required = true)
	public Document[]		documents;
}
