package de.cxp.ocs.elasticsearch.query.filter;

import static de.cxp.ocs.config.FieldConstants.FILTER_DATA;
import static de.cxp.ocs.config.FieldConstants.NUMBER_FACET_DATA;
import static de.cxp.ocs.config.FieldConstants.TERM_FACET_DATA;

import java.util.Locale;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldType;
import de.cxp.ocs.config.FieldUsage;
import de.cxp.ocs.elasticsearch.model.filter.InternalResultFilter;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * used for exact filtering of one or more values.
 */
@Accessors(chain = true)
@Data
public class TermResultFilter implements InternalResultFilter {

	private final Field field;

	private String[] values;

	private boolean isFilterOnId = false;

	private boolean isNegated = false;


	public TermResultFilter(Field field, String... inputValues) {
		this(Locale.ROOT, field, inputValues);
	}

	public TermResultFilter(Locale lowerCaseLocale, Field field, String... inputValues) {
		this.field = field;
		values = new String[inputValues.length];
		for (int i = 0; i < inputValues.length; i++) {
			values[i] = inputValues[i].toLowerCase(lowerCaseLocale);
		}
	}

	public String getSingleValue() {
		if (values.length > 0) return values[0];
		return null;
	}

	public String getValue(int index) {
		if (values.length > index) return values[index];
		return null;
	}

	@Override
	public boolean isNestedFilter() {
		return TERM_FACET_DATA.equals(getFieldPrefix()) || NUMBER_FACET_DATA.equals(getFieldPrefix());
	}

	@Override
	public String getFieldPrefix() {
		// ID filter only work with facet fields
		if (isFilterOnId && field.hasUsage(FieldUsage.FACET)) {
			return FieldType.NUMBER.equals(field.getType()) ? NUMBER_FACET_DATA : TERM_FACET_DATA;
		}
		// apart from that, always prefer filter_data field
		else if (field.hasUsage(FieldUsage.FILTER)) {
			return FILTER_DATA;
		}
		else {
			return TERM_FACET_DATA;
		}
	}

	@Override
	public void appendFilter(InternalResultFilter other) {
		if (!(other instanceof TermResultFilter)) return;
		if (isFilterOnId != other.isFilterOnId()) return;
		if (!field.getName().equals(field.getName())) return;

		// negated filter can always we replaced with a correct filter
		if (isNegated && !other.isNegated()) {
			isNegated = false;
			this.values = other.getValues();
			this.isFilterOnId = other.isFilterOnId();
		}
		else if (isNegated == other.isNegated()) {
			values = InternalResultFilter.unifiyValues(values, other.getValues());
		}
		// else only the other is negated and can be ignored
	}



}