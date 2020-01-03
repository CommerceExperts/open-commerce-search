package de.cxp.ocs.api.indexer;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import lombok.AllArgsConstructor;
import lombok.Data;

@Schema(accessMode = AccessMode.READ_ONLY)
@Data
@AllArgsConstructor
public class ImportSession {

	final public String finalIndexName;

	final public String temporaryIndexName;
}
