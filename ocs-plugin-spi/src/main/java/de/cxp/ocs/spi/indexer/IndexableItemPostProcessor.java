package de.cxp.ocs.spi.indexer;

import de.cxp.ocs.config.FieldConfigAccess;
import de.cxp.ocs.indexer.model.IndexableItem;
import de.cxp.ocs.model.index.Document;

// TODO transform all DataPreProcessor to use this interface
public interface IndexableItemPostProcessor {

	void apply(Document originalDocument, IndexableItem record, FieldConfigAccess fieldConfig);
}
