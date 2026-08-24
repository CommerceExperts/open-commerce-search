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
		description = "Facet entry that describes the complete range of the facet. "
				+ "If a filter is picked, the selectedMin and selectedMax value are set, otherwise null.")
public class RangeFacetEntry extends FacetEntry {

	public final String type = "range";

	private Number lowerBound;

	private Number upperBound;

	private Number selectedMin;

	private Number selectedMax;

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
	public RangeFacetEntry(Number lowerBound, Number upperBound, long docCount, String link, boolean isSelected) {
		super(getLabel(lowerBound, upperBound), null, docCount, link, isSelected);
		this.lowerBound = lowerBound;
		this.upperBound = upperBound;
	}

	public RangeFacetEntry setLowerBound(Number lowerBound) {
		this.lowerBound = lowerBound;
		this.key = getLabel(lowerBound, upperBound);
		return this;
	}

	public RangeFacetEntry setUpperBound(Number upperBound) {
		this.upperBound = upperBound;
		this.key = getLabel(lowerBound, upperBound);
		return this;
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
		if (from == null || to == null) throw new IllegalArgumentException("Both lower bound and upper bound must be defined!");
		return from + "-" + to;
	}

}
