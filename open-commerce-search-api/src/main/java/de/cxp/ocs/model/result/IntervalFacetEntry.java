package de.cxp.ocs.model.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * Facet entry that describes a numerical interval.
 * If only the lower value or only the upper value is set,
 * this means it's an open ended interval, e.g. '&lt; 100' for upper bound only.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(
		allOf = { FacetEntry.class },
		description = "Facet entry that describes a numerical interval. "
				+ "If only the lower value or only the upper value is set, "
				+ "this means it's an open ended interval, e.g. '< 100' for upper bound only.")
public class IntervalFacetEntry extends FacetEntry {

	public final String type = "interval";

	private Number lowerBound;

	private Number upperBound;

	public IntervalFacetEntry(String label, Number lowerBound, Number upperBound, long docCount, String link, boolean isSelected) {
		super(label, null, docCount, link, isSelected);
		this.lowerBound = lowerBound;
		this.upperBound = upperBound;
	}

	/**
	 * 
	 * @param lowerBound
	 *        lower bound of interval represented by that facet entry. Can be
	 *        null if upper bound exists.
	 * @param upperBound
	 *        upper bound of interval represented by that facet entry. Can be
	 *        null if lower bound exists.
	 * @param docCount
	 *        the amount of documents covered by that interval
	 * @param link
	 *        the link to toggle the filter state of the related result
	 * @param isSelected
	 *        true if the related result is currently filtered by that interval
	 */
	public IntervalFacetEntry(Number lowerBound, Number upperBound, long docCount, String link, boolean isSelected) {
		this(getLabel(lowerBound, upperBound), lowerBound, upperBound, docCount, link, isSelected);
	}

	/**
	 * simple label that considers nullable lower or upper bound value.
	 * 
	 * @param from
	 *        lower bound
	 * @param to
	 *        upper bound
	 * @return
	 */
	private static String getLabel(Number from, Number to) {
		if (from == null && to == null) throw new IllegalArgumentException("Eiter lower bound or upper bound must be defined! Both are null however.");
		if (from == null) {
			return "< " + to;
		}
		if (to == null) {
			return "> " + from;
		}
		return from + "-" + to;
	}

}
