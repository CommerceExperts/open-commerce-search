package de.cxp.ocs.preprocessor.impl;

import static de.cxp.ocs.conf.converter.FlagFieldConfiguration.FIELD_FLAG_DESTINATION;
import static de.cxp.ocs.conf.converter.FlagFieldConfiguration.FIELD_NO_MATCH;
import static de.cxp.ocs.conf.converter.FlagFieldConfiguration.GROUP_SEPARATOR;
import static de.cxp.ocs.conf.converter.FlagFieldConfiguration.TYPE_CONF;
import static de.cxp.ocs.conf.converter.FlagFieldConfiguration.TYPE_FIELD;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import de.cxp.ocs.conf.converter.FlagFieldConfiguration;
import de.cxp.ocs.conf.converter.FlagFieldConfiguration.PatternMatch;
import de.cxp.ocs.config.FieldConfigAccess;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.preprocessor.ConfigureableDataprocessor;
import de.cxp.ocs.spi.indexer.DocumentPreProcessor;
import de.cxp.ocs.util.OnceInAWhileRunner;
import de.cxp.ocs.util.Util;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link ConfigureableDataprocessor} implementation which fills a flag field
 * based on a pattern match in a source field. Will be auto configured and can
 * be further configuration like described below:
 * 
 * <pre>
 * data-processor-configuration: 
 *   processors:
 *     - FlagFieldDataProcessor
 *  configuration:
 *    FlagFieldDataProcessor:
 *      # Multiple fields can be evaluated to flag one destination field.
 *      # Thus you must prefix your configuration with {@link FlagFieldConfiguration#GROUP_PREFIX}
 *      # the label after the prefix can be anything, import is though that they end with 
 *      # {@link FlagFieldConfiguration#GROUP_SEPARATOR} and that they have the same value for a group,
 *      # like the numeric values 1 and 2 below. For each field a match value can be provided by
 *      # appending {@link FlagFieldConfiguration#FIELD_MATCH} after the field name. Rules get evaluated
 *      # in order and as soon as one rule matches, the matching value is written into the flag field
 *      # and no further rules gets evaluated.
 *      group_1_title: \bfür\b\s+.+
 *      group_1_title_match: 0.9
 *      # Multiple regular expression can be provided separated by {@link FlagFieldConfiguration#SEPARATOR}
 *      group_1_category: \bzubeh(ö|oe)r\b##.*zubeh(ö|oe)r\b
 *      # If multiple regular expression are defined, multiple match values need to 
 *      # be provided as well. Every expression needs exactly one value.
 *      group_1_category_match: 1##0.8
 *      group_1_price: ^[\\d{,2}](\\.\\d)
 *      group_1_price_match: 0.3
 *      # One must provide a destination flag field per group by appending 
 *      # {@link FlagFieldConfiguration#FIELD_FLAG_DESTINATION} to the field name. 
 *      group_1_destination: "isAccessoire"
 *      # Optionally a no match value can by supplied which will be written into the destination
 *      # field if no rule within a group matched.
 *      group_1_noMatch: -1
 *      # Multiple groups are all evaluated, regardless if a previous group had a match or not.
 *      group_2_kpi: ".+"
 *      group_2_destination: "kpi"
 *      group_2_noMatch: 0
 * </pre>
 *
 */
@Slf4j
@NoArgsConstructor
public class FlagFieldDataProcessor implements DocumentPreProcessor {

	private List<FlagFieldConfiguration>	flagFieldConf;

	@Override
	public void initialize(FieldConfigAccess fieldConfig, Map<String, String> confMap) {
		if (confMap != null) {
			Map<String, Map<String, List<Entry<String, String>>>> groupToTypeConf = confMap.entrySet().stream()
					.collect(Collectors
							.groupingBy(entry -> {
								String key = entry.getKey();
								int ordinalIndexOfUnderscore = StringUtils.ordinalIndexOf(key, GROUP_SEPARATOR, 2);
								final String groupName = key.substring(0, ordinalIndexOfUnderscore);
								return groupName;
							}, Collectors.groupingBy(entry -> {
								String key = entry.getKey();
								return StringUtils.countMatches(key, GROUP_SEPARATOR) == 2
										&& !key.endsWith(FIELD_FLAG_DESTINATION)
										&& !key.endsWith(FIELD_NO_MATCH)
												? TYPE_FIELD
												: TYPE_CONF;
							})));
			flagFieldConf = new ArrayList<>(groupToTypeConf.size());
			groupToTypeConf.forEach((k, v) -> flagFieldConf.add(new FlagFieldConfiguration(k, v)));
		}
		else {
			log.warn("FlagFieldDataProcessor configuration is missing, processor {} will not work", this.getClass()
					.getSimpleName());
		}
	}

	@Override
	public boolean process(Document document, boolean visible) {
		if (flagFieldConf != null) {
			flagFieldConf.forEach(ffc -> {
				MatchResult matchRes = getMatchResult(document, ffc);
				if (matchRes.value != null) {
					document.set(ffc.getDestinationFieldName(), matchRes.value);
				}
				else if (ffc.getNoMatch() != null) {
					document.set(ffc.getDestinationFieldName(), ffc.getNoMatch());
				}
			});
		}
		return visible;
	}

	private MatchResult getMatchResult(Document document, FlagFieldConfiguration ffc) {
		MatchResult matchRes = new MatchResult();
		Map<String, Object> data = document.getData();
		for (PatternMatch pm : ffc) {
			Object value = data.get(pm.getFieldName());
			if (value instanceof String) {
				String strValue = (String) value;
				pm.matches(strValue).ifPresent(matchVal -> matchRes.value = matchVal);
				if (matchRes.value != null) {
					break;
				}
			}
			else if (Util.isStringCollection(value)) {
				@SuppressWarnings("unchecked")
				final Collection<String> valueCollection = (Collection<String>) value;
				for (String val : valueCollection) {
					pm.matches(val).ifPresent(matchVal -> matchRes.value = matchVal);
					if (matchRes.value != null) {
						break;
					}
				}
				if (matchRes.value != null) {
					break;
				}
			}
			else if (value != null) {
				OnceInAWhileRunner.runAgainAfter(() -> log.warn(
						"Value '{}' could not be matched against a pattern for group '{}' and field '{}' as the value is not a String or a Collection of Strings",
						value, ffc.getGroupName(), pm.getFieldName()), this.getClass().getSimpleName() + pm.getFieldName(),
						ChronoUnit.SECONDS, 60);
			}
		}
		return matchRes;
	}

	private class MatchResult {

		String value;
	}
}
