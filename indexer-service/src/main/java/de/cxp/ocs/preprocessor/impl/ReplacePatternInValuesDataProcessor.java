package de.cxp.ocs.preprocessor.impl;

import static de.cxp.ocs.conf.converter.PatternConfiguration.FIELD_REPLACEMENT_DESTINATION;
import static de.cxp.ocs.conf.converter.PatternWithReplacementConfiguration.FIELD_REPLACEMENT_SUFFIX;

import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import de.cxp.ocs.conf.converter.PatternWithReplacementConfiguration;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.preprocessor.ConfigureableDataprocessor;
import de.cxp.ocs.util.OnceInAWhileRunner;
import de.cxp.ocs.util.Util;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link ConfigureableDataprocessor} implementation which replaces all
 * occurrences of a regular expression within a fields value. Will be auto
 * configured and can be further
 * configuration like described below:
 * 
 * <pre>
 * data-processor-configuration: 
 *   processors:
 *     - ReplacePatternInValuesDataProcessor
 *  configuration:
 *    ReplacePatternInValuesDataProcessor:
 *      someFieldName: ".*\\d+.*"
 *      someFieldName_replacement: "foo"
 *      someFieldName_destination: "someDestinationField"
 * </pre>
 * 
 * This would replace all word between a number with 'foo' from the field with
 * the name 'someFieldName'. If no *_replacement is provided, the matched
 * pattern will be replaced by {@link StringUtils#EMPTY}. Furthermore if a
 * *_destination is configured, the original value stays untouched and the
 * replaced value will be written into the configured field.
 *
 */
@Slf4j
@NoArgsConstructor
public class ReplacePatternInValuesDataProcessor extends
		ConfigureableDataprocessor<PatternWithReplacementConfiguration> {

	@Override
	protected PatternWithReplacementConfiguration getPatternConfiguration(String key, String value,
			Map<String, String> confMap) {
		if (!key.endsWith(FIELD_REPLACEMENT_SUFFIX) && !key.endsWith(FIELD_REPLACEMENT_DESTINATION)) {
			return new PatternWithReplacementConfiguration(key, confMap.get(key + FIELD_REPLACEMENT_DESTINATION),
					Pattern.compile(value), confMap.get(key + FIELD_REPLACEMENT_SUFFIX));
		}
		return null;
	}

	@Override
	protected BiConsumer<PatternWithReplacementConfiguration, Object> getProcessConsumer(Document doc, boolean visible) {
		return (pc, value) -> {
			if (value instanceof String) {
				String strValue = (String) value;
				String replacedValue = pc.getPattern().matcher(strValue).replaceAll(pc.getReplacement());
				doc.set(pc.getDestinationFieldName(), StringUtils.isEmpty(replacedValue) ? null : replacedValue);
			}
			else if (Util.isStringCollection(value)) {
				@SuppressWarnings("unchecked")
				final Collection<String> valueCollection = (Collection<String>) value;
				final Collection<String> cleandValues = new HashSet<>(valueCollection.size());
				valueCollection.forEach(collectionValue -> {
					String replacedValue = pc.getPattern().matcher(collectionValue).replaceAll(pc
							.getReplacement());
					if (!StringUtils.isEmpty(replacedValue)) {
						cleandValues.add(replacedValue);
					}
				});
				if (!cleandValues.isEmpty()) {
					doc.set(pc.getDestinationFieldName(), cleandValues.toArray(new String[cleandValues.size()]));
				}
			}
			else {
				OnceInAWhileRunner.runAgainAfter(() -> log.warn(
						"Value '{}' could not be replaced for field '{}' as the value is not a String or a Collection of Strings",
						value, pc.getFieldName()), this.getClass().getSimpleName() + pc.getFieldName(),
						ChronoUnit.SECONDS, 60);
			}
		};
	}

}
