package de.cxp.ocs.conf.converter;

import java.util.regex.Pattern;

import de.cxp.ocs.preprocessor.impl.RemoveValuesDataProcessor;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * {@link ConfigureableField} implementations that holds all information needed
 * by the {@link RemoveValuesDataProcessor}. The configuration allows:
 * 
 * <pre>
 *  configuration:
 *    RemoveValuesDataProcessor:
 *      someFieldName: ".*\\d+.*"
 *      someFieldName_destination: "someDestinationField"
 *      # Optional configuration:
 *      # RegEx used to split the value into chunks, \\s+ if omitted
 *      someFieldName_wordSplitRegEx: "/"
 *      # join character used when combining splitted cleared chunks, default space <code>" "</code>
 *      someFieldName_wordJoinSeparator: "/"
 * </pre>
 * 
 * @see RemoveValuesDataProcessor RemoveValuesDataProcessor
 *      further explanation.
 */
@Data
@RequiredArgsConstructor
public class PatternConfiguration implements ConfigureableField {

	public static final String	FIELD_REPLACEMENT_DESTINATION	= "_destination";
	public static final String	FIELD_WORD_SPLIT_REGEX			= "_wordSplitRegEx";
	public static final String	FIELD_WORD_JOIN_SEPARATOR		= "_wordJoinSeparator";

	private final String	fieldName;
	private final String	destinationFieldName;
	private final Pattern	pattern;

	@NonNull
	private String		wordSplitRegEx		= "\\s+";
	@NonNull
	private Character	wordJoinSeparator	= ' ';

	/**
	 * Gets the destination field name if configured, otherwise the field name
	 * itself is returned.
	 * 
	 * @return the destination field name.
	 */
	public String getDestinationFieldName() {
		return destinationFieldName != null ? destinationFieldName : fieldName;
	}
}
