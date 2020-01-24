package de.cxp.ocs.model.index;

import de.cxp.ocs.api.indexer.ImportSession;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "composite object that is used to add documents to the index.", requiredProperties = { "session", "documents" })
@Data
public class BulkImportData {

	public ImportSession	session;
	public Document[]		documents;
}
