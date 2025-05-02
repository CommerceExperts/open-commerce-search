package de.cxp.ocs.controller;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.elasticsearch.common.inject.Singleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import de.cxp.ocs.conf.IndexConfiguration;
import de.cxp.ocs.indexer.AbstractIndexer;
import de.cxp.ocs.indexer.IndexerFactory;
import de.cxp.ocs.spi.indexer.IndexerConfigurationProvider;

@Component
@Singleton
public class IndexerCache {

	@Autowired
	private IndexerFactory indexerFactory;

	@Autowired
	private IndexerConfigurationProvider configProvider;

	private final LoadingCache<String, AbstractIndexer> actualIndexers = CacheBuilder.newBuilder()
			.expireAfterAccess(15, TimeUnit.MINUTES)
			.build(new CacheLoader<String, AbstractIndexer>() {

				@Override
				public AbstractIndexer load(String indexName) throws Exception {
					IndexConfiguration indexConfig = new IndexConfiguration();
					configProvider.getDataProcessorConfiguration(indexName).ifPresent(indexConfig::setDataProcessorConfiguration);
					indexConfig.setFieldConfiguration(configProvider.getFieldConfiguration(indexName));
					return indexerFactory.create(indexConfig);
				}
			});

	public AbstractIndexer getIndexer(String indexName) throws ExecutionException {
		return actualIndexers.get(indexName);
	}
}
