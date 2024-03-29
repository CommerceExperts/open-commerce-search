package de.cxp.ocs.preprocessor;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import de.cxp.ocs.conf.IndexConfiguration;
import de.cxp.ocs.conf.converter.ConfigureableField;
import de.cxp.ocs.conf.converter.PatternConfiguration;
import de.cxp.ocs.config.FieldConfigAccess;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.spi.indexer.DocumentPreProcessor;
import de.cxp.ocs.util.OnceInAWhileRunner;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Abstract class which handles reading and initializing
 * {@link de.cxp.ocs.spi.indexer.DocumentPreProcessor}
 * implementations which need further configuration.
 * 
 * @author hjk
 * 
 * @param <T>
 *        {@link ConfigureableField} implementation to which the
 *        {@link de.cxp.ocs.spi.indexer.DocumentPreProcessor} gets mapped.
 *
 */
@Slf4j
@NoArgsConstructor
public abstract class ConfigureableDataprocessor<T extends ConfigureableField> implements DocumentPreProcessor {

	protected IndexConfiguration properties;

	protected List<T> patternConf;

	@Override
	public void initialize(FieldConfigAccess fieldConfig, Map<String, String> confMap) {
		if (confMap != null) {
			patternConf = new ArrayList<>(confMap.size());
			confMap.forEach((key, val) -> {
				if (key != null && val != null) {
					T patternConfiguration = getPatternConfiguration(key, val, confMap);
					if (patternConfiguration != null) {
						patternConf.add(patternConfiguration);
					}
				}
				else {
					log.warn(
							"configuration for processor {} invalid. must have a key and a value, but got key={}, value={} ",
							this.getClass().getSimpleName(), key, val);
				}
			});
		}
		else {
			log.warn("configuration is missing for processor {}. It won't work", this.getClass()
					.getSimpleName());
		}
	}

	/**
	 * Returns the {@link ConfigureableField} holding the parsed configuration
	 * for every configured key.
	 * 
	 * @param key
	 *        a key from the data processor configuration for this data
	 *        processor.
	 * @param value
	 *        the value of that key.
	 * @param confMap
	 *        the complete data processor configuration map.
	 * @return the parsed configurable field for the key.
	 */
	protected abstract T getPatternConfiguration(final String key, final String value,
			final Map<String, String> confMap);

	@Override
	public boolean process(Document sourceDocument, boolean visible) {
		if (patternConf != null) {
			BiConsumer<T, Object> biConsumer = getProcessConsumer(sourceDocument, visible);
			patternConf.forEach(pc -> {
				Object value = sourceDocument.getData().get(pc.getFieldName());
				if (biConsumer != null) {
					biConsumer.accept(pc, value);
				}
				else {
					OnceInAWhileRunner.runAgainAfter(() -> log.warn(
							"DataProcessor consumer is null, processing can't be done for class {}", this.getClass()
									.getSimpleName()), this.getClass().getSimpleName(), ChronoUnit.SECONDS, 60);
				}
			});
		}
		return isRecordVisible(sourceDocument, visible);
	}

	/**
	 * Returns a {@link BiConsumer} whose input is the configured
	 * {@link ConfigureableField} with the value of the currently processed
	 * record. The consumer gets called in the
	 * {@link de.cxp.ocs.spi.indexer.DocumentPreProcessor#process(Document, boolean)}
	 * method for each configured key.
	 * 
	 * @param sourceDocument
	 *        the record data
	 * @param visible
	 *        the current visibility of the record
	 * @return a {@link BiConsumer} whose accept method should handle every
	 *         {@link PatternConfiguration} with the corresponding value.
	 */
	protected abstract BiConsumer<T, Object> getProcessConsumer(Document sourceDocument, boolean visible);

	/**
	 * Called at the end of each process(Document, boolean) run after
	 * {@link ConfigureableDataprocessor#getProcessConsumer(Document, boolean)}
	 * is has run for every {@link ConfigureableField}, to determine weather the
	 * record should be visible or not.
	 * 
	 * @param sourceDocument
	 *        the record data
	 * @param visible
	 *        the current visibility of the record
	 * @return <code>true</code> if the record should be indexed,
	 *         <code>false</code> otherwise.
	 */
	protected boolean isRecordVisible(Document sourceDocument, boolean visible) {
		return visible;
	}

}
