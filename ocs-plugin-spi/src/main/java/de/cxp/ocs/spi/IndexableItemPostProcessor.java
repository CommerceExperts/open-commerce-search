package de.cxp.ocs.spi;

import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.indexer.model.IndexableItem;
import de.cxp.ocs.model.index.Document;

// TODO transform all DataPreProcessor to use this interface
public interface IndexableItemPostProcessor {

	void apply(Document originalDocument, IndexableItem record, FieldConfigIndex fieldConfig);
}
