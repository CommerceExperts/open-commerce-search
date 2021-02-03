package de.cxp.ocs.elasticsearch.query.filter;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConstants;
import lombok.Data;

@Data
public class NumberResultFilter implements InternalResultFilter {

	private final Field field;

	private final Number lowerBound;

	private final Number upperBound;

	public NumberResultFilter(Field field, Number lowerBound, Number upperBound) {
		this.field = field;
		this.lowerBound = lowerBound;
		this.upperBound = upperBound;
		if (lowerBound == null && upperBound == null) {
			throw new IllegalArgumentException("number result filter without lower and upper bound not allowed!");
		}
		if (lowerBound == null) lowerBound = upperBound;
		else if (upperBound == null) upperBound = lowerBound;
		else if (lowerBound.doubleValue() > upperBound.doubleValue()) {
			throw new IllegalArgumentException("lower bound can't be greater than upper bound!");
		}
	}

	@Override
	public String[] getValues() {
		if (lowerBound.equals(upperBound)) return new String[] { lowerBound.toString()};
		else return new String[] { lowerBound.toString(), upperBound.toString() };
	}

	@Override
	public String getFieldPrefix() {
		return FieldConstants.NUMBER_FACET_DATA;
	}

	@Override
	public boolean isNestedFilter() {
		return true;
	}
}