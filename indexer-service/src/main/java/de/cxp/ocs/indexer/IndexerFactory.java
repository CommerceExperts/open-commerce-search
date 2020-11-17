package de.cxp.ocs.indexer;

import java.util.ArrayList;
import java.util.List;

import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.stereotype.Component;

import de.cxp.ocs.conf.IndexConfiguration;
import de.cxp.ocs.elasticsearch.ElasticsearchIndexer;
import de.cxp.ocs.preprocessor.DataPreProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Component
public class IndexerFactory {

	private static final String dataPreProcessorImplPackage = "de.cxp.ocs.preprocessor.impl.";

	private final RestHighLevelClient elasticsearchClient;

	public AbstractIndexer create(IndexConfiguration indexConfiguration) {
		List<DataPreProcessor> dataProcessors = new ArrayList<>();

		for (String processorName : indexConfiguration.getDataProcessorConfiguration().getProcessors()) {
			try {
				Class<?> processorClass = Class.forName(dataPreProcessorImplPackage + processorName);
				if (processorClass != null && DataPreProcessor.class.isAssignableFrom(processorClass)) {
					DataPreProcessor processor = (DataPreProcessor) processorClass.newInstance();
					processor.configure(indexConfiguration);
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

		return new ElasticsearchIndexer(indexConfiguration, elasticsearchClient, dataProcessors);
	}

}
