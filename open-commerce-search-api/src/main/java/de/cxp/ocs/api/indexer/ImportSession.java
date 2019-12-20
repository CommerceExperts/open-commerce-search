package de.cxp.ocs.api.indexer;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ImportSession {

	final public String finalIndexName;

	final public String temporaryIndexName;
}
