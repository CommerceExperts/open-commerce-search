package de.cxp.ocs.conf.converter;

import java.util.HashMap;
import java.util.Map;

import de.cxp.ocs.preprocessor.impl.SplitValueDataProcessor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * {@link ConfigureableField} implementations that holds all information needed
 * by the {@link SplitValueDataProcessor}. The configuration allows:
 * 
 * <pre>
 * 	configuration:
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
 */
@Data
@RequiredArgsConstructor
public class SplitValueConfiguration implements ConfigureableField {

	public static final int		WILDCARD_IDX			= -1;
	public static final String	IDX_TO_DEST_SEPARATOR	= ":";
	public static final String	ENTRY_SEPARATOR			= ";";

	public static final String	FIELD_NAME				= "FieldName";
	public static final String	IDX_TO_DEST_FIELD_CONF	= "_idxToDest";
	public static final String	REG_EX_CONF				= "_regEx";
	public static final String	KEEP_ORIG_CONF			= "_keepOrig";

	private final String	fieldName;
	private final boolean	keepOriginal;
	private final String	regEx;

	private Map<Integer, String> indexToDestinationFieldName = new HashMap<>();

	public SplitValueConfiguration addIndexToDestinationFieldName(final int idx, final String destinationFieldName) {
		indexToDestinationFieldName.put(idx, destinationFieldName);
		return this;
	}
}
