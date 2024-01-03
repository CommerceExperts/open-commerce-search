package de.cxp.ocs.elasticsearch.facets.helper;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.function.Function;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.elasticsearch.query.filter.NumberResultFilter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class NumericFacetEntryBuilder {

	public boolean	isFirstEntry;
	public boolean	isLastEntry;

	public Number	lowerBound;
	public Number	upperBound;

	public long	currentDocumentCount	= 0;
	public int	currentVariantCount		= 0;

	public NumericFacetEntryBuilder(NumberResultFilter facetFilter) {
		lowerBound = facetFilter.getLowerBound();
		upperBound = facetFilter.getUpperBound();
	}

	public NumericFacetEntryBuilder(Number lowerBound, Number upperBound) {
		this.lowerBound = lowerBound;
		this.upperBound = upperBound;
	}

	public String[] getFilterValues() {
		if (lowerBound == null && upperBound == null) {
			return null;
		}
		if (lowerBound == null) {
			return new String[] { upperBound.toString() };
		}
		if (upperBound == null) {
			return new String[] { lowerBound.toString() };
		}
		return new String[] { lowerBound.toString(), upperBound.toString() };
	}

	/**
	 * Sophisticated interval label that considers nullable lower or upper bound value or if this interval is the first
	 * or last one.
	 * <p>
	 * The way the label is created is also configurable via meta-data set at the facet config. First the optional
	 * rounding of the values:
	 * <ul>
	 * <li>showInclusiveRanges: (true|false) // if set to true, the upper bounds will be reduced by 0.01 so that they
	 * can be considered as inclusive bounds.</li>
	 * <li>round: (down|true|up) // if set, the numbers will be rounded accordingly: "true" for natural rounding, "down"
	 * for always floor rounding and "up" for always ceil rounding.
	 * Using 'showInclusiveRanges:true' and 'round:floor' will practically round down the upper bounds to the next lower
	 * integral number</li>
	 * <li>decimals: 0-2 // if set to 0, the numbers will be rounded to a whole number. Defaults to 2</li>
	 * </ul>
	 * </p>
	 * <p>
	 * Also there is some config to define the binding strings around the values inside the label.
	 * <ul>
	 * <li>noLowerBoundPrefix: string that should be used as prefix for the first interval when practically no lower
	 * bound value is defined</li>
	 * <li>noLowerBoundSuffix: same as noLowerBoundPrefix but used as suffix</li>
	 * <li>intervalSeparator: the line separator between lower and upper bound for intermediate intervals</li>
	 * <li>noUpperBoundPrefix: string that should be used as prefix for the last interval when there is no upper bound
	 * value defined</li>
	 * <li>noUpperBoundSuffix: same as noUpperBoundPrefix but as suffix</li>
	 * <li>unit: a string that is appended to every value</li>
	 * <li>unitAsPrefix: set to 'true' to use the unit as a prefix instead, e.g. for £25</li>
	 * </ul>
	 * </p>
	 * 
	 * @param from
	 *        lower bound
	 * @param to
	 *        upper bound
	 * @return
	 */
	public String getLabel(FacetConfig facetConfig) {
		Number _lowerBound = isFirstEntry ? null : lowerBound;
		Number _upperBound = isLastEntry ? null : upperBound;

		if (_upperBound != null && Boolean.parseBoolean(facetConfig.getMetaData().getOrDefault("showInclusiveRanges", "false").toString())) {
			_upperBound = _upperBound.doubleValue() - 0.01;
		}

		int decimals = Integer.parseInt(facetConfig.getMetaData().getOrDefault("decimals", "2").toString());
		if (decimals < 0) decimals = 0;
		else if (decimals > 2) decimals = 2;
		NumberFormat format = NUM_FORMATS[decimals];

		var rounding = facetConfig.getMetaData().get("round");
		Function<Float, Number> roundingFn;
		if ("down".equals(rounding)) roundingFn = Math::floor;
		else if ("up".equals(rounding)) roundingFn = Math::ceil;
		else if ("true".equals(rounding)) roundingFn = Math::round;
		else roundingFn = val -> val;

		String noLowerBoundPrefix = facetConfig.getMetaData().getOrDefault("noLowerBoundPrefix", "< ").toString();
		String noLowerBoundSuffix = facetConfig.getMetaData().getOrDefault("noLowerBoundSuffix", "").toString();
		String intervalSeparator = facetConfig.getMetaData().getOrDefault("intervalSeparator", " - ").toString();
		String noUpperBoundPrefix = facetConfig.getMetaData().getOrDefault("noUpperBoundPrefix", "> ").toString();
		String noUpperBoundSuffix = facetConfig.getMetaData().getOrDefault("noUpperBoundSuffix", "").toString();

		boolean unitAsPrefix = Boolean.parseBoolean(facetConfig.getMetaData().getOrDefault("unitAsPrefix", "false").toString());
		String unit = facetConfig.getMetaData().getOrDefault("unit", "").toString();

		StringBuilder label = new StringBuilder();
		if (_lowerBound != null) {
			if (_upperBound == null) label.append(noUpperBoundPrefix);
			if (unitAsPrefix) label.append(unit);
			label.append(format.format(roundingFn.apply(_lowerBound.floatValue())));
			if (!unitAsPrefix) label.append(unit);
			if (_upperBound == null) label.append(noUpperBoundSuffix);
		}

		if (_upperBound != null) {
			if (_lowerBound == null) label.append(noLowerBoundPrefix);
			else label.append(intervalSeparator);
			if (unitAsPrefix) label.append(unit);
			label.append(format.format(roundingFn.apply(_upperBound.floatValue())));
			if (!unitAsPrefix) label.append(unit);
			if (_lowerBound == null) label.append(noLowerBoundSuffix);
		}

		return label.toString();
	}

	private final static NumberFormat[] NUM_FORMATS = new NumberFormat[] {
			NumberFormat.getIntegerInstance(),
			new DecimalFormat("0.#"),
			new DecimalFormat("0.##")
	};
}
