package de.cxp.ocs.indexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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

@Component
public class IndexerFactory {

	private final RestHighLevelClient elasticsearchClient;

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
	}

	public AbstractIndexer create(IndexConfiguration indexConfiguration) {
		List<DocumentPreProcessor> preProcessors = new ArrayList<>();
		List<DocumentPostProcessor> postProcessors = new ArrayList<>();

		Map<String, Map<String, String>> dataProcessorsConfig = indexConfiguration.getDataProcessorConfiguration().getConfiguration();
		FieldConfigIndex fieldConfigIndex = new FieldConfigIndex(indexConfiguration.getFieldConfiguration());

		for (String processorName : indexConfiguration.getDataProcessorConfiguration().getProcessors()) {
			Supplier<? extends DocumentPreProcessor> preProcessorSupplier = docPreProcessorSuppliers.get(processorName);
			if (preProcessorSupplier != null) {
				DocumentPreProcessor processor = preProcessorSupplier.get();
				processor.initialize(fieldConfigIndex, dataProcessorsConfig.get(processor.getClass().getCanonicalName()));
				preProcessors.add(processor);
			}

			Supplier<? extends DocumentPostProcessor> postProcessorSupplier = indexableItemProcessorSuppliers.get(processorName);
			if (postProcessorSupplier != null) {
				DocumentPostProcessor postProcessor = postProcessorSupplier.get();
				postProcessor.initialize(fieldConfigIndex, dataProcessorsConfig.get(postProcessor.getClass().getCanonicalName()));
			}
		}

		return new ElasticsearchIndexer(fieldConfigIndex, elasticsearchClient, preProcessors, postProcessors);
	}

}
