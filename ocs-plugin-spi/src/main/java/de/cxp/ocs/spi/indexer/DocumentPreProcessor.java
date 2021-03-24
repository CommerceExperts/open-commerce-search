package de.cxp.ocs.spi.indexer;

import java.util.Map;

import de.cxp.ocs.config.FieldConfigAccess;
import de.cxp.ocs.model.index.Document;

/**
 * {@link DocumentPreProcessor} implementations can be used to alter product
 * data before they get transformed into
 * {@link de.cxp.ocs.indexer.model.IndexableItem}s.
 * Several implementations can be configured to run one after another, where
 * each processor get's the manipulated record value of the former processor.
 * 
 * @author hjk, rb
 */
public interface DocumentPreProcessor {

	/**
	 * DataPreProcessor MUST have a no-args constructor. To configure it
	 * afterwards, this method will be used.
	 * 
	 * @param fieldConfig
	 * @param a
	 *        custom string-to-string map that can be configured per
	 *        DocumentPreProcessor.
	 */
	void initialize(FieldConfigAccess fieldConfig, Map<String, String> preProcessorConfig);

	/**
	 * Called for each source document.
	 * 
	 * @param sourceDocument
	 * @param visible
	 *        weather or not the record is currently marked for indexing.
	 * @return <code>true</code> if the record should be indexed,
	 *         <code>false</code> otherwise.
	 */
	boolean process(final Document sourceDocument, boolean visible);

}
