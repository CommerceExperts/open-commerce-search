package de.cxp.ocs.indexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.stereotype.Component;

import de.cxp.ocs.conf.IndexConfiguration;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.elasticsearch.ElasticsearchIndexer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Component
public class IndexerFactory {

	private static final String dataPreProcessorImplPackage = "de.cxp.ocs.preprocessor.impl.";

	private final RestHighLevelClient elasticsearchClient;

	public AbstractIndexer create(IndexConfiguration indexConfiguration) {
		List<DocumentPreProcessor> dataProcessors = new ArrayList<>();

		Map<String, Map<String, String>> dataProcessorsConfig = indexConfiguration.getDataProcessorConfiguration().getConfiguration();
		FieldConfigIndex fieldConfigIndex = new FieldConfigIndex(indexConfiguration.getFieldConfiguration());

		for (String processorName : indexConfiguration.getDataProcessorConfiguration().getProcessors()) {
			try {
				Class<?> processorClass = Class.forName(dataPreProcessorImplPackage + processorName);
				if (processorClass != null && DocumentPreProcessor.class.isAssignableFrom(processorClass)) {
					DocumentPreProcessor processor = (DocumentPreProcessor) processorClass.newInstance();
					processor.initialize(fieldConfigIndex, dataProcessorsConfig.get(processorClass.getSimpleName()));
					dataProcessors.add(processor);
				}
			}
			catch (ClassNotFoundException e1) {
				log.error("DataPreProcessor with name {} not found: {}", processorName, e1.getMessage());
			}
			catch (InstantiationException | IllegalAccessException e2) {
				log.error("DataPreProcessor with name {} couldn't be instantiated: {}:{}", processorName,
						e2.getClass().getSimpleName(), e2.getMessage());
			}
		}

		return new ElasticsearchIndexer(fieldConfigIndex, elasticsearchClient, dataProcessors);
	}

}
