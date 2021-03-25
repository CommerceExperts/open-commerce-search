package de.cxp.ocs.preprocessor.impl;

import static de.cxp.ocs.conf.converter.SplitValueConfiguration.*;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;

import de.cxp.ocs.conf.converter.SplitValueConfiguration;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.preprocessor.ConfigureableDataprocessor;
import de.cxp.ocs.util.OnceInAWhileRunner;
import de.cxp.ocs.util.Util;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link de.cxp.ocs.spi.indexer.DocumentPreProcessor} implementation which
 * splits a field value on a regular expression and adds the splitted values
 * into new fields. Will be auto configured and can be further configuration
 * like described below:
 * 
 * <pre>
 *  data-processor-configuration: 
 *   processors:
 *     - SplitValueDataProcessor
 *  configuration:
 *    SplitValueDataProcessor:
 *      FieldName: ColorMapping 
 *      ColorMapping_keepOriginal: true
 *      ColorMapping_regEx: /::/
 *      ColorMapping_idxToDest: 0:Hauptfarbe;1:Nebenfarbe
 *      #Optionally a wildcard index can be supplied if the index count is not predictable when splitting a value.
 *      #In this case only one value is allowed and must be -1. For every splitted value a record entry 
 *      #in form: value_idxOfSplitted value will be added. E.g.:
 *      ColorMapping_idxToDest: -1:Hauptfarbe 
 *      # This would add Hauptfarbe_0, Hauptfarbe_1, Hauptfarbe_3 for on a value foo/::/bar/::/baz
 * </pre>
 * 
 * This would split the value of the ColorMapping field by the regular
 * expression /::/ assuming that it result in two splitted values. The first of
 * the splitted values is going to be added in a new field called Hauptfarbe and
 * the second value into a new field Nebenfarbe. Furthermore the original value
 * of the field ColorMapping is not removed.
 *
 */
@Slf4j
@NoArgsConstructor
public class SplitValueDataProcessor extends ConfigureableDataprocessor<SplitValueConfiguration> {

	@Override
	protected SplitValueConfiguration getPatternConfiguration(String key, String value, Map<String, String> confMap) {
		if (key.startsWith(FIELD_NAME)) {
			// TODO: let the exceptions flow to indicate wrong configuration or
			// catch them and log a warning? Also check for other
			// ConfigureableDataprocessor impls
			boolean keepOrig = Boolean.parseBoolean(confMap.get(value + KEEP_ORIG_CONF));
			String regEx = confMap.get(value + REG_EX_CONF);
			String idxToDest = confMap.get(value + IDX_TO_DEST_FIELD_CONF);

			SplitValueConfiguration configuration = new SplitValueConfiguration(value, keepOrig, regEx);
			splitIdxToDestAndAddToConf(configuration, idxToDest);
			return configuration;
		}
		return null;
	}

	private void splitIdxToDestAndAddToConf(SplitValueConfiguration configuration, String idxToDest) {
		String[] splitByEntry = idxToDest.split(ENTRY_SEPARATOR);
		for (String entry : splitByEntry) {
			String[] itd = entry.split(IDX_TO_DEST_SEPARATOR);
			int idx = Integer.parseInt(itd[0]);
			configuration.addIndexToDestinationFieldName(idx, itd[1]);
		}
	}

	@Override
	protected BiConsumer<SplitValueConfiguration, Object> getProcessConsumer(Document doc, boolean visible) {
		return (svc, value) -> {
			Map<String, Object> sourceData = doc.getData();
			if (value instanceof String) {
				splitAndCopy(sourceData, svc, (String) value, false);
				if (!svc.isKeepOriginal()) {
					sourceData.remove(svc.getFieldName());
				}
			}
			else if (Util.isStringCollection(value)) {
				@SuppressWarnings("unchecked")
				final Collection<String> valueCollection = (Collection<String>) value;
				valueCollection.forEach(v -> splitAndCopy(sourceData, svc, v, true));
				if (!svc.isKeepOriginal()) {
					sourceData.remove(svc.getFieldName());
				}
			}
			else {
				OnceInAWhileRunner.runAgainAfter(() -> log.warn(
						"Value '{}' could not be split for field '{}' as the value is not a String or a Collection of Strings",
						value, svc.getFieldName()), this.getClass().getSimpleName() + svc.getFieldName(),
						ChronoUnit.SECONDS, 60);
			}
		};
	}

	private void splitAndCopy(Map<String, Object> sourceData, SplitValueConfiguration svc, String value,
			boolean isCollection) {
		String[] splittedValues = value.split(svc.getRegEx());
		if (isWildcardConfiguration(svc)) {
			addToSourceByWildcard(sourceData, svc, splittedValues, isCollection);
		}
		else {
			addToSourceByExplicitIdx(sourceData, svc, value, splittedValues, isCollection);
		}
	}

	private boolean isWildcardConfiguration(SplitValueConfiguration svc) {
		return svc.getIndexToDestinationFieldName().containsKey(WILDCARD_IDX);
	}

	@SuppressWarnings("unchecked")
	private void addToSourceByWildcard(Map<String, Object> sourceData, SplitValueConfiguration svc,
			String[] splittedValues, boolean isCollection) {
		int i = 0;
		for (String splitValue : splittedValues) {
			if (isCollection) {
				((Collection<String>) sourceData.computeIfAbsent(computeWildcardFieldName(svc, i),
						v -> new ArrayList<String>())).add(splitValue);
			}
			else {
				sourceData.put(computeWildcardFieldName(svc, i), splitValue);
			}
			i++;
		}
	}

	private String computeWildcardFieldName(SplitValueConfiguration svc, int i) {
		return svc.getIndexToDestinationFieldName().get(WILDCARD_IDX) + "_" + i;
	}

	@SuppressWarnings("unchecked")
	private void addToSourceByExplicitIdx(Map<String, Object> sourceData, SplitValueConfiguration svc, Object value,
			String[] splittedValues, boolean isCollection) {
		if (splittedValues.length == svc.getIndexToDestinationFieldName().size()) {
			for (Map.Entry<Integer, String> idxToDest : svc.getIndexToDestinationFieldName().entrySet()) {
				if (isCollection) {
					((Collection<String>) sourceData.computeIfAbsent(idxToDest.getValue(),
							v -> new ArrayList<String>())).add(splittedValues[idxToDest.getKey()]);
				}
				else {
					sourceData.put(idxToDest.getValue(), splittedValues[idxToDest.getKey()]);
				}
			}
		}
		else {
			OnceInAWhileRunner.runAgainAfter(() -> log.warn(
					"Expected splitted value to have an length of {} but instead got {} for value {}", svc
							.getIndexToDestinationFieldName().size(), splittedValues.length, value), value
									.toString(), ChronoUnit.SECONDS, 60);
		}
	}

}
