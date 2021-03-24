package de.cxp.ocs.spi.indexer;

import java.util.Map;

import de.cxp.ocs.config.FieldConfigAccess;
import de.cxp.ocs.indexer.model.IndexableItem;
import de.cxp.ocs.model.index.Document;

/**
 * Processor that is called after the document was transformed into a
 * {@link IndexableItem}, just right before it will be indexed.
 */
public interface DocumentPostProcessor {

	/**
	 * DocumentPostProcessor MUST have a default constructor. This method will
	 * be used to configure it afterwards.
	 * 
	 * @param fieldConfigIndex
	 * @param settings
	 *        a custom string-to-string map that can be configured per
	 *        DocumentPostProcessor.
	 */
	void initialize(FieldConfigAccess fieldConfigIndex, Map<String, String> settings);

	/**
	 * Called for each converted document.
	 * Changes to the {@link Document} won't be considered anymore. Only changes
	 * to the {@link IndexableItem} are relevant.
	 * 
	 * @param originalDocument
	 * @param record
	 * @param fieldConfig
	 */
	void process(Document originalDocument, IndexableItem record, FieldConfigAccess fieldConfig);

}
