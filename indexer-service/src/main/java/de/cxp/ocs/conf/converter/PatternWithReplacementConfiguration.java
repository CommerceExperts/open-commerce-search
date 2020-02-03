package de.cxp.ocs.conf.converter;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import de.cxp.ocs.preprocessor.impl.ReplacePatternInValuesDataProcessor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * {@link ConfigureableField} implementations that holds all information needed
 * by the {@link ReplacePatternInValuesDataProcessor}. The configuration allows:
 * 
 * <pre>
 * configuration:
 *    ReplacePatternInValuesDataProcessor:
 *      someFieldName: ".*\\d+.*"
 *      someFieldName_replacement: "foo"
 *      someFieldName_destination: "someDestinationField"
 * </pre>
 * 
 * @see ReplacePatternInValuesDataProcessor ReplacePatternInValuesDataProcessor
 *      further explanation.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PatternWithReplacementConfiguration extends PatternConfiguration {

	public static final String FIELD_REPLACEMENT_SUFFIX = "_replacement";

	private final String replacement;

	/**
	 * Creats a new instance.
	 * 
	 * @param fieldName
	 *        the field name.
	 * @param destinationFieldName
	 *        the destination name.
	 * @param pattern
	 *        the {@link Pattern} to use.
	 * @param replacement
	 *        the replacement value used when the pattern matches.
	 */
	public PatternWithReplacementConfiguration(String fieldName, String destinationFieldName, Pattern pattern,
			final String replacement) {
		super(fieldName, destinationFieldName, pattern);
		this.replacement = replacement;
	}

	/**
	 * Gets the replacement or an empty string if no replacement is configured.
	 * 
	 * @return the replacement or an empty string.
	 */
	public String getReplacement() {
		return replacement != null ? replacement : StringUtils.EMPTY;
	}
}
