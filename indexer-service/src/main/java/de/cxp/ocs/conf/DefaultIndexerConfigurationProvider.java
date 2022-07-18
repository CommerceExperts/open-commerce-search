package de.cxp.ocs.conf;

import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

import de.cxp.ocs.config.DataProcessorConfiguration;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfiguration;
import de.cxp.ocs.config.IndexSettings;
import de.cxp.ocs.spi.indexer.IndexerConfigurationProvider;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DefaultIndexerConfigurationProvider implements IndexerConfigurationProvider {

	@NonNull
	private final ApplicationProperties properties;

	@Override
	public IndexSettings getIndexSettings(String indexName) {
		return getIndexConf(indexName).getIndexSettings();
	}

	@Override
	public FieldConfiguration getFieldConfiguration(String indexName) {
		FieldConfiguration fieldConfiguration = getIndexConf(indexName).getFieldConfiguration();
		fixFieldConfiguration(fieldConfiguration);
		return fieldConfiguration;
	}

	private void fixFieldConfiguration(FieldConfiguration fieldConf) {
		for (Entry<String, Field> field : fieldConf.getFields().entrySet()) {
			if (field.getValue().getName() == null) {
				field.getValue().setName(field.getKey());
			}
		}
	}

	private IndexConfiguration getIndexConf(String indexName) {
		final IndexConfiguration indexConfig = properties.getIndexConfig().get(indexName);
		if (Objects.nonNull(indexConfig)) {
			return new IndexConfigurationMerger(indexConfig, properties.getDefaultIndexConfig()).getIndexConfig();
		}
		return properties.getDefaultIndexConfig();
	}

	@Override
	public Optional<DataProcessorConfiguration> getDataProcessorConfiguration(String indexName) {
		return Optional.ofNullable(getIndexConf(indexName).getDataProcessorConfiguration());
	}

	@Override
	public void setDefaultProvider(IndexerConfigurationProvider defaultIndexerConfigurationProvider) {
		// nothing to do - this is default!
	}

}
