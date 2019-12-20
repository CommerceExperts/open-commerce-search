package de.cxp.ocs.preprocessor;

import de.cxp.ocs.conf.IndexConfiguration;
import de.cxp.ocs.model.index.Document;

/**
 * {@link DataPreProcessor} implementations can be used to alter product data
 * before they get indexed into the search engine. Several implementations can be
 * configured to run one after another, where each processor get's the
 * manipulated record value of the former processor.
 * 
 * @author hjk, rb
 */
public interface DataPreProcessor {

	/**
	 * DataPreProcessor MUST have a no-args constructor. To configure it
	 * afterwards, this method will be used.
	 * 
	 * @param indexConfiguration
	 */
	void configure(IndexConfiguration indexConfiguration);

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
