package de.cxp.ocs.spi.indexer;

import java.util.Optional;

import de.cxp.ocs.config.DataProcessorConfiguration;
import de.cxp.ocs.config.FieldConfiguration;
import de.cxp.ocs.config.IndexSettings;

/**
 * Provider for index specific settings. They will be requested for every index
 * job.
 */
public interface IndexerConfigurationProvider {

	/**
	 * @param indexName
	 *        index name
	 * @return
	 *         the settings for the requested index
	 */
	IndexSettings getIndexSettings(String indexName);

	/**
	 * Required configuration about which data fields should be indexed in which
	 * way.
	 * 
	 * @param indexName
	 *        index name
	 * @return
	 *         the field configuration for the requested index
	 */
	FieldConfiguration getFieldConfiguration(String indexName);

	/**
	 * Optional configuration for data processors that should modify the records
	 * before indexation.
	 * 
	 * @param indexName
	 *        index name
	 * @return
	 *         optional data processor configuration
	 */
	Optional<DataProcessorConfiguration> getDataProcessorConfiguration(String indexName);

	/**
	 * Gives access to the default configuration provider.
	 * 
	 * @param defaultIndexerConfigurationProvider
	 *        default configuration provider
	 */
	void setDefaultProvider(IndexerConfigurationProvider defaultIndexerConfigurationProvider);

}
