package de.cxp.ocs.spi.indexer;

import java.util.Optional;

import de.cxp.ocs.config.DataProcessorConfiguration;
import de.cxp.ocs.config.FieldConfiguration;

public interface IndexerConfigurationProvider {

	/**
	 * Required configuration about which data fields should be indexed in which
	 * way.
	 * 
	 * @param indexName
	 * @return
	 */
	FieldConfiguration getFieldConfiguration(String indexName);

	/**
	 * Optional configuration for data processors that should modify the records
	 * before indexation.
	 * 
	 * @param indexName
	 * @return
	 */
	Optional<DataProcessorConfiguration> getDataProcessorConfiguration(String indexName);


}
