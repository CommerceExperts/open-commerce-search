package de.cxp.ocs.conf.converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import de.cxp.ocs.preprocessor.impl.FlagFieldDataProcessor;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link ConfigureableField} implementations that holds all information needed
 * by the {@link FlagFieldDataProcessor}. The configuration allows:
 * 
 * <pre>
 *  configuration:
 *    FlagFieldDataProcessor:
 *      group_1_someSourceFieldName: ".*\\d+.*##foo##bar"
 *      group_1_someSourceFieldName_match: 1##0.99##-1
 *      group_1_noMatch: -1
 *      group_1_destination: someDestinationField
 * </pre>
 * 
 * @see FlagFieldDataProcessor FlagFieldDataProcessor
 *      further explanation.
 */
@Slf4j
@Data
public class FlagFieldConfiguration implements Iterable<FlagFieldConfiguration.PatternMatch> {

	public static final String	GROUP_PREFIX	= "group_";
	public static final String	GROUP_SEPARATOR	= "_";

	public static final String SEPARATOR = "##";

	public static final String	TYPE_CONF	= "conf";
	public static final String	TYPE_FIELD	= "field";

	public static final String	FIELD_REGEX				= "_regEx";
	public static final String	FIELD_MATCH				= "_match";
	public static final String	FIELD_NO_MATCH			= "_noMatch";
	public static final String	FIELD_FLAG_DESTINATION	= "_destination";

	private final String groupName;

	private String destinationFieldName;

	private String noMatch = null;

	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private final Map<String, PatternMatch> fieldToPatternMatcher;

	/**
	 * Crates a new instance.
	 * 
	 * @param groupName
	 *        the name of the group the configuration belongs to.
	 * @param typeToConfiguration
	 *        a {@link Map} containing for each type
	 *        {@link FlagFieldConfiguration#TYPE_FIELD} and
	 *        {@link FlagFieldConfiguration#TYPE_CONF} the corresponding field
	 *        and configuration entries.
	 */
	public FlagFieldConfiguration(final String groupName,
			final Map<String, List<Entry<String, String>>> typeToConfiguration) {
		this.groupName = groupName;
		final String groupNameWithSuffix = groupName + GROUP_SEPARATOR;

		if (!isCompleteConf(typeToConfiguration)) {
			throw new IllegalStateException("The configuration of group '" + groupName
					+ "' is missing one of the expected types: field/conf.");
		}
		fieldToPatternMatcher = parsePatternMachterPerField(typeToConfiguration, groupNameWithSuffix);
		parseConfigrationPerField(typeToConfiguration, groupNameWithSuffix);
	}

	private boolean isCompleteConf(final Map<String, List<Entry<String, String>>> typeToConfiguration) {
		return typeToConfiguration.size() == 2 && typeToConfiguration.containsKey(TYPE_FIELD) && typeToConfiguration
				.containsKey(TYPE_CONF);
	}

	private Map<String, PatternMatch> parsePatternMachterPerField(
			final Map<String, List<Entry<String, String>>> typeToConfiguration,
			final String groupNameWithSuffix) {
		List<Entry<String, String>> fieldsToPatternConf = typeToConfiguration.get(TYPE_FIELD);
		Map<String, PatternMatch> fieldToPatternMatcher = new LinkedHashMap<>(fieldsToPatternConf.size());
		fieldsToPatternConf.forEach(entry -> {
			String fieldName = StringUtils.substringAfter(entry.getKey(), groupNameWithSuffix);
			List<Pattern> parsedPattern = parsePattern(entry.getValue());
			fieldToPatternMatcher.put(fieldName, new PatternMatch(fieldName, parsedPattern));
		});
		return fieldToPatternMatcher;
	}

	private List<Pattern> parsePattern(String value) {
		String[] configuredPattern = value.split(SEPARATOR);
		List<Pattern> patternList = new ArrayList<>(configuredPattern.length);
		for (String pattern : configuredPattern) {
			patternList.add(Pattern.compile(pattern));
		}
		return patternList;
	}

	private void parseConfigrationPerField(final Map<String, List<Entry<String, String>>> typeToConfiguration,
			final String groupNameWithSuffix) {
		List<Entry<String, String>> confToPatternConf = typeToConfiguration.get(TYPE_CONF);
		confToPatternConf.forEach(entry -> {
			String fieldNameWithConf = StringUtils.substringAfter(entry.getKey(), groupNameWithSuffix);
			String fieldName = StringUtils.substringBefore(fieldNameWithConf, GROUP_SEPARATOR);
			String matchConf = StringUtils.substring(fieldNameWithConf, fieldName.length(), fieldNameWithConf.length());

			if (!StringUtils.isEmpty(matchConf)) {
				final PatternMatch patternMatch = fieldToPatternMatcher.get(fieldName);
				if (patternMatch != null) {
					patternMatch.getMatch().addAll(getFlagConfiguration(entry.getValue()));
				}
			}
			else {
				if (FIELD_FLAG_DESTINATION.endsWith(fieldName)) {
					this.destinationFieldName = entry.getValue();
				}
				else if (FIELD_NO_MATCH.endsWith(fieldName)) {
					this.noMatch = entry.getValue();
				}
				else {
					log.error("Unknown configuration type '{}', will be ignored", fieldName);
				}
			}
		});
	}

	private List<String> getFlagConfiguration(String matchFlags) {
		if (matchFlags != null) {
			return Arrays.asList(matchFlags.split(SEPARATOR));
		}
		return Collections.emptyList();
	}

	/**
	 * Gets the destination field name if configured, otherwise the field name
	 * itself is returned.
	 * 
	 * @return the destination field name.
	 */
	public String getDestinationFieldName() {
		return destinationFieldName != null ? destinationFieldName : groupName;
	}

	@Getter
	public static class PatternMatch {

		private final String		fieldName;
		private final List<Pattern>	pattern;

		private final List<String> match;

		public PatternMatch(String fieldName, List<Pattern> pattern) {
			this.fieldName = fieldName;
			this.pattern = pattern;
			match = new ArrayList<>(pattern.size());
		}

		public Optional<String> matches(final String value) {
			for (int i = 0; i < pattern.size(); i++) {
				if (pattern.get(i).matcher(value).matches()) {
					String matchVal = i < match.size() ? match.get(i) : null;
					return Optional.ofNullable(matchVal);
				}
			}
			return Optional.empty();
		}
	}

	@Override
	public Iterator<PatternMatch> iterator() {
		return fieldToPatternMatcher.values().iterator();
	}
}
