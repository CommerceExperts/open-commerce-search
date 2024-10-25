package de.cxp.ocs.model.index;

import de.cxp.ocs.api.indexer.ImportSession;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "composite object that is used to add documents to the index.")
@Data
public class BulkImportData {

	@Schema(
			required = true,
			description = "Import session information that were retrieved by the startImport method.")
	public ImportSession	session;

	@Schema(
			required = true,
			description = "An array of Documents or Products that should be indexed into the given index."
					+ "Products have the speciality to contain other documents as variants.",
			anyOf = { Document.class, Product.class })
	public Document[]		documents;
}
