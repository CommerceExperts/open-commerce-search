package de.cxp.ocs.api.indexer;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import lombok.AllArgsConstructor;
import lombok.Data;

@Schema(accessMode = AccessMode.READ_ONLY)
@Data
@AllArgsConstructor
public class ImportSession {

	@Schema(required = true)
	final public String finalIndexName;

	@Schema(required = true)
	final public String temporaryIndexName;
}
