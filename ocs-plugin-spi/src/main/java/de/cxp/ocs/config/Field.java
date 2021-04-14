package de.cxp.ocs.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Accessors(chain = true)
public class Field {

	/**
	 * Sets the name of the field used in the Elasticsearch index.
	 */
	@NonNull
	protected String name;

	/**
	 * Sets the type of the field.
	 * Default: String
	 */
	private FieldType type = FieldType.STRING;

	/**
	 * Sets weather the filed is on variant level or not. If not, it
	 * will be treated as on master level.
	 */
	private FieldLevel fieldLevel = FieldLevel.MASTER;

	/**
	 * Checks weather the field is on variant level.
	 *
	 * @return <code>true</code> if the field is on variant level,
	 *         <code>false</code> otherwise.
	 */
	public boolean isVariantLevel() {
		return FieldLevel.VARIANT.equals(fieldLevel) || FieldLevel.BOTH.equals(fieldLevel);
	}

	/**
	 * Checks weather the field is on master level.
	 *
	 * @return <code>true</code> if the field is on master level,
	 *         <code>false</code> otherwise.
	 */
	public boolean isMasterLevel() {
		return FieldLevel.MASTER.equals(fieldLevel) || FieldLevel.BOTH.equals(fieldLevel);
	}
	
	/**
	 * Checks weather the field should be indexed on both - master and variant - level.
	 *
	 * @return <code>true</code> if the field is on both level,
	 *         <code>false</code> otherwise.
	 */
	public boolean isBothLevel() {
		return FieldLevel.BOTH.equals(fieldLevel);
	}
	
	/**
	 * Sets the names of the csv header column which should be used as the
	 * source of the fields. Multiple names are supported to build combi-fields.
	 */
	private List<String> sourceNames = new ArrayList<>();

	/**
	 * Adds the specified name to the source names list.
	 * 
	 * @param name
	 *        the name to add to the list of source names.
	 * @return this instance.
	 */
	public Field addSourceName(final String name) {
		sourceNames.add(name);
		return this;
	}

	/**
	 * Sets the usage of the field.
	 *
	 */
	// Needs to be a List because of Springs yaml annotation processing.
	private List<FieldUsage> usage = new ArrayList<>();

	public Field setUsage(final FieldUsage usage1, final FieldUsage... usages) {
		if (usage1 != null) {
			usage.add(usage1);
		}
		if (usages != null && usages.length > 0) {
			usage.addAll(Arrays.asList(usages));
		}
		return this;
	}

	public Field setUsage(final Collection<FieldUsage> usages) {
		usage = new ArrayList<>(usages);
		return this;
	}

	private String valueDelimiter = null;

	private String searchContentPrefix = null;

}
