package de.cxp.ocs.elasticsearch.query.filter;

import static de.cxp.ocs.config.FieldConstants.FILTER_DATA;
import static de.cxp.ocs.config.FieldConstants.NUMBER_FACET_DATA;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldUsage;
import de.cxp.ocs.elasticsearch.model.filter.InternalResultFilter;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@Data
public class NumberResultFilter implements InternalResultFilter {

	private final Field field;

	private Number lowerBound;

	private Number upperBound;

	@Getter
	@Setter
	private boolean isNegated = false;

	public NumberResultFilter(Field field, Number lowerBound, Number upperBound) {
		if (lowerBound == null && upperBound == null) {
			throw new IllegalArgumentException("number result filter without lower and upper bound not allowed!");
		}
		if (lowerBound == null) lowerBound = upperBound;
		else if (upperBound == null) upperBound = lowerBound;
		else if (lowerBound.doubleValue() > upperBound.doubleValue()) {
			throw new IllegalArgumentException("lower bound can't be greater than upper bound!");
		}
		this.field = field;
		this.lowerBound = lowerBound;
		this.upperBound = upperBound;
	}

	@Override
	public String[] getValues() {
		if (lowerBound.equals(upperBound)) return new String[] { lowerBound.toString()};
		else return new String[] { lowerBound.toString(), upperBound.toString() };
	}

	@Override
	public String getFieldPrefix() {
		return field.hasUsage(FieldUsage.FILTER) ? FILTER_DATA : NUMBER_FACET_DATA;
	}

	@Override
	public boolean isNestedFilter() {
		return NUMBER_FACET_DATA.equals(getFieldPrefix());
	}

	@Override
	public boolean isFilterOnId() {
		// no "ID" implementation for numbers available
		return false;
	}

	@Override
	public void appendFilter(InternalResultFilter other) {
		if (!(other instanceof NumberResultFilter)) return;
		if (!field.getName().equals(field.getName())) return;

		NumberResultFilter otherNF = (NumberResultFilter) other;

		// negated filter can always we replaced with a correct filter
		if (isNegated && !other.isNegated()) {
			isNegated = false;
			this.lowerBound = otherNF.getLowerBound();
			this.upperBound = otherNF.getUpperBound();
		}
		else if (isNegated == other.isNegated()) {
			if (lowerBound == null || (otherNF.lowerBound != null && otherNF.lowerBound.floatValue() < lowerBound.floatValue())) lowerBound = otherNF.lowerBound;
			if (upperBound == null || (otherNF.upperBound != null && otherNF.upperBound.floatValue() > upperBound.floatValue())) upperBound = otherNF.upperBound;
		}
		// else only the other is negated and can be ignored
	}

}
