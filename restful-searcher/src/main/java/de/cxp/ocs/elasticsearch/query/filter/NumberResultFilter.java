package de.cxp.ocs.elasticsearch.query.filter;

import lombok.Data;

@Data
public class NumberResultFilter implements InternalResultFilter {

	private final String field;

	private final Number lowerBound;

	private final Number upperBound;

	public NumberResultFilter(String field, Number lowerBound, Number upperBound) {
		this.field = field;
		this.lowerBound = lowerBound;
		this.upperBound = upperBound;
		if (lowerBound == null && upperBound == null) {
			throw new IllegalArgumentException("number result filter without lower and upper bound not allowed!");
		}
		if (upperBound != null && lowerBound != null && lowerBound.doubleValue() > upperBound.doubleValue()) {
			throw new IllegalArgumentException("lower bound can't be greater than upper bound!");
		}
	}
}