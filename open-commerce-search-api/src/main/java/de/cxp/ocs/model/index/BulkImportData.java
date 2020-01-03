package de.cxp.ocs.model.index;

import de.cxp.ocs.api.indexer.ImportSession;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema
@Data
public class BulkImportData {

	ImportSession	session;
	Document[]		documents;
}
