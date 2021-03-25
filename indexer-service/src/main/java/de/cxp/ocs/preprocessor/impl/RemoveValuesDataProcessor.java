package de.cxp.ocs.preprocessor.impl;

import static de.cxp.ocs.conf.converter.PatternConfiguration.*;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import de.cxp.ocs.conf.converter.PatternConfiguration;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.preprocessor.ConfigureableDataprocessor;
import de.cxp.ocs.util.OnceInAWhileRunner;
import de.cxp.ocs.util.Util;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link de.cxp.ocs.spi.indexer.DocumentPreProcessor} implementation which
 * removes values from a fields value based on a regular expression. Will be
 * auto configured and can be further configuration like described below:
 * 
 * <pre>
 *  data-processor-configuration: 
 *   processors:
 *     - RemoveValuesDataProcessor
 *  configuration:
 *     RemoveValuesDataProcessor:
 *       someFieldName: ".*\\d+.*"
 *       someFieldName_destination: "someDestinationField"
 *       # Optional configuration:
 *       # RegEx used to split the value into chunks, //s+ if omitted
 *       someFieldName_wordSplitRegEx: "/"
 *       # join character used when combining splitted cleared chunks, default space <code>" "</code>
 *       someFieldName_wordJoinSeparator: "/"
 * </pre>
 * 
 * This would remove all numerical values from the field with the name
 * 'someFieldName' and write it into the field 'someDestinationField'. If no
 * destination is specified, the destination will be the source field.
 * This implementation splits the value into separate tokens
 * and checks the regular expression against each token. If the regular
 * expression matches the token, the token get's removed.
 *
 */
@Slf4j
@NoArgsConstructor
public class RemoveValuesDataProcessor extends ConfigureableDataprocessor<PatternConfiguration> {

	@Override
	protected PatternConfiguration getPatternConfiguration(String key, String value, Map<String, String> confMap) {
		if (!key.endsWith(FIELD_REPLACEMENT_DESTINATION) && !key.endsWith(FIELD_WORD_JOIN_SEPARATOR) && !key.endsWith(
				FIELD_WORD_SPLIT_REGEX)) {
			PatternConfiguration patternConfiguration = new PatternConfiguration(key, confMap.get(key
					+ FIELD_REPLACEMENT_DESTINATION), Pattern.compile(
							value));
			String splitRegEx = confMap.get(key + FIELD_WORD_SPLIT_REGEX);
			if (!StringUtils.isEmpty(splitRegEx)) {
				patternConfiguration.setWordSplitRegEx(splitRegEx);
			}
			String joinSeparator = confMap.get(key + FIELD_WORD_JOIN_SEPARATOR);
			if (!StringUtils.isEmpty(joinSeparator)) {
				patternConfiguration.setWordJoinSeparator(joinSeparator.charAt(0));
			}
			return patternConfiguration;
		}
		return null;
	}

	@Override
	protected BiConsumer<PatternConfiguration, Object> getProcessConsumer(Document sourceData, boolean visible) {
		return (pc, value) -> {
			if (value instanceof String) {
				String strValue = (String) value;
				String[] cleanedTokens = removeTokens(strValue.trim().split(pc.getWordSplitRegEx()), pc.getPattern());
				sourceData.getData().put(pc.getDestinationFieldName(), StringUtils.join(cleanedTokens, pc
						.getWordJoinSeparator()));
			}
			else if (Util.isStringCollection(value)) {
				@SuppressWarnings("unchecked")
				final Collection<String> valueCollection = (Collection<String>) value;
				final Collection<String> cleandValues = new HashSet<>(valueCollection.size());
				valueCollection.forEach(collectionValue -> {
					String[] cleanedTokens = removeTokens(collectionValue.trim().split(pc.getWordSplitRegEx()), pc
							.getPattern());
					if (cleanedTokens.length > 0) {
						cleandValues.add(StringUtils.join(cleanedTokens, pc.getWordJoinSeparator()));
					}
				});
				if (!cleandValues.isEmpty()) {
					sourceData.set(pc.getDestinationFieldName(), cleandValues.toArray(new String[cleandValues.size()]));
				}
			}
			else {
				OnceInAWhileRunner.runAgainAfter(() -> log.warn(
						"Value '{}' could not be removed for field '{}' as the value is not a String or a Collection of Strings",
						value, pc.getFieldName()), this.getClass().getSimpleName() + pc.getFieldName(),
						ChronoUnit.SECONDS, 60);
			}
		};
	}

	private String[] removeTokens(String[] inputTokens, final Pattern pattern) {
		List<String> output = new ArrayList<>();
		for (String token : inputTokens) {
			if (!pattern.matcher(token).matches()) {
				output.add(token);
			}
		}
		return output.toArray(new String[output.size()]);
	}

}
