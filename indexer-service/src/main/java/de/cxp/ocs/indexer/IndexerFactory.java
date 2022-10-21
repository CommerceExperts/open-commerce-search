package de.cxp.ocs.indexer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.stereotype.Component;

import de.cxp.ocs.conf.IndexConfiguration;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.elasticsearch.ElasticsearchIndexer;
import de.cxp.ocs.plugin.ExtensionSupplierRegistry;
import de.cxp.ocs.plugin.PluginManager;
import de.cxp.ocs.preprocessor.impl.AsciiFoldingDataProcessor;
import de.cxp.ocs.preprocessor.impl.ExtractCategoryLevelDataProcessor;
import de.cxp.ocs.preprocessor.impl.FlagFieldDataProcessor;
import de.cxp.ocs.preprocessor.impl.RemoveFieldContentDelimiterProcessor;
import de.cxp.ocs.preprocessor.impl.RemoveValuesDataProcessor;
import de.cxp.ocs.preprocessor.impl.ReplacePatternInValuesDataProcessor;
import de.cxp.ocs.preprocessor.impl.SkipDocumentDataProcessor;
import de.cxp.ocs.preprocessor.impl.SplitValueDataProcessor;
import de.cxp.ocs.preprocessor.impl.WordSplitterDataProcessor;
import de.cxp.ocs.spi.indexer.DocumentPostProcessor;
import de.cxp.ocs.spi.indexer.DocumentPreProcessor;
import fr.pilato.elasticsearch.tools.ElasticsearchBeyonder;
import fr.pilato.elasticsearch.tools.SettingsFinder.Defaults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class IndexerFactory {

	private final RestHighLevelClient elasticsearchClient;

	private boolean templatesInitialized = false;

	private final Map<String, Supplier<? extends DocumentPreProcessor>> docPreProcessorSuppliers;

	private final Map<String, Supplier<? extends DocumentPostProcessor>> indexableItemProcessorSuppliers;

	public IndexerFactory(RestHighLevelClient elasticsearchClient, PluginManager pm) {
		this.elasticsearchClient = elasticsearchClient;

		ExtensionSupplierRegistry<DocumentPreProcessor> docPreProcessorRegistry = new ExtensionSupplierRegistry<DocumentPreProcessor>();
		docPreProcessorRegistry.register(AsciiFoldingDataProcessor.class, AsciiFoldingDataProcessor::new);
		docPreProcessorRegistry.register(ExtractCategoryLevelDataProcessor.class, ExtractCategoryLevelDataProcessor::new);
		docPreProcessorRegistry.register(FlagFieldDataProcessor.class, FlagFieldDataProcessor::new);
		docPreProcessorRegistry.register(RemoveFieldContentDelimiterProcessor.class, RemoveFieldContentDelimiterProcessor::new);
		docPreProcessorRegistry.register(RemoveValuesDataProcessor.class, RemoveValuesDataProcessor::new);
		docPreProcessorRegistry.register(ReplacePatternInValuesDataProcessor.class, ReplacePatternInValuesDataProcessor::new);
		docPreProcessorRegistry.register(SplitValueDataProcessor.class, SplitValueDataProcessor::new);
		docPreProcessorRegistry.register(SkipDocumentDataProcessor.class, SkipDocumentDataProcessor::new);
		docPreProcessorRegistry.register(WordSplitterDataProcessor.class, WordSplitterDataProcessor::new);
		pm.loadAll(DocumentPreProcessor.class).forEach(c -> docPreProcessorRegistry.register(c));
		docPreProcessorSuppliers = docPreProcessorRegistry.getExtensionSuppliers();

		ExtensionSupplierRegistry<DocumentPostProcessor> indexableItemProcessor = new ExtensionSupplierRegistry<DocumentPostProcessor>();
		pm.loadAll(DocumentPostProcessor.class).forEach(c -> indexableItemProcessor.register(c));
		indexableItemProcessorSuppliers = indexableItemProcessor.getExtensionSuppliers();

		// Initialize on startup to avoid blocking multiple index-start requests
		// at the same time (method is internally synchronized)
		initializeTemplates();
	}

	public AbstractIndexer create(IndexConfiguration indexConfiguration) {
		List<DocumentPreProcessor> preProcessors = new ArrayList<>();
		List<DocumentPostProcessor> postProcessors = new ArrayList<>();
		initializeDataProcessors(indexConfiguration, preProcessors, postProcessors);

		// make sure the templates are initialized
		initializeTemplates();

		return new ElasticsearchIndexer(
				indexConfiguration.getIndexSettings(),
				new FieldConfigIndex(indexConfiguration.getFieldConfiguration()),
				elasticsearchClient,
				preProcessors,
				postProcessors);
	}

	private void initializeDataProcessors(IndexConfiguration indexConfiguration, List<DocumentPreProcessor> preProcessors, List<DocumentPostProcessor> postProcessors) {
		FieldConfigIndex fieldConfigIndex = new FieldConfigIndex(indexConfiguration.getFieldConfiguration());
		Map<String, Map<String, String>> dataProcessorsConfig = indexConfiguration.getDataProcessorConfiguration().getConfiguration();
		for (String processorName : indexConfiguration.getDataProcessorConfiguration().getProcessors()) {
			Supplier<? extends DocumentPreProcessor> preProcessorSupplier = docPreProcessorSuppliers.get(processorName);
			boolean processorFound = false;
			if (preProcessorSupplier != null) {
				DocumentPreProcessor processor = preProcessorSupplier.get();
				processor.initialize(fieldConfigIndex, dataProcessorsConfig.getOrDefault(processor.getClass().getCanonicalName(), Collections.emptyMap()));
				preProcessors.add(processor);
				processorFound = true;
				log.info("initialized pre-processor {}", processorName);
			}

			Supplier<? extends DocumentPostProcessor> postProcessorSupplier = indexableItemProcessorSuppliers.get(processorName);
			if (postProcessorSupplier != null) {
				DocumentPostProcessor postProcessor = postProcessorSupplier.get();
				postProcessor.initialize(fieldConfigIndex, dataProcessorsConfig.getOrDefault(postProcessor.getClass().getCanonicalName(), Collections.emptyMap()));
				postProcessors.add(postProcessor);
				processorFound = true;
				log.info("initialized post-processor {}", processorName);
			}

			if (!processorFound) {
				log.error("Processor '{}' not found!", processorName);
			}
		}
	}

	private void initializeTemplates() {
		if (!templatesInitialized) {
			synchronized (this) {
				if (!templatesInitialized) {
					RestClient restClient = elasticsearchClient.getLowLevelClient();

					try {
						ElasticsearchBeyonder.start(restClient, Defaults.ConfigDir, true);
						templatesInitialized = true;
					}
					catch (Exception e) {
						log.error("failed to initialize templates!", e);
					}

					// check for old legacy templates
					if (templatesInitialized) {
						try {
							Request getOldIndexTemplReq = new Request("GET", "_template/*structured_search");
							Response getOldIndexTemplResp = restClient.performRequest(getOldIndexTemplReq);
							if (getOldIndexTemplResp.getStatusLine().getStatusCode() == 200) {
								log.warn("Old deprecated template(s) '*structured_search' found. Consider deleting them or migrate to new composable index template format.");
							}
						}
						catch (Exception e) {
							// all OK: old templates are already deleted/migrated
						}
					}
				}
			}
		}
	}

}
