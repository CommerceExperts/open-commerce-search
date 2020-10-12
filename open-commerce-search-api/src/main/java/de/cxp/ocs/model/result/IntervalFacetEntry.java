package de.cxp.ocs.model.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Schema(
		allOf = { FacetEntry.class },
		description = "Facet entry that describes a numerical interval. "
				+ "If only the lower value or only the upper value is set, "
				+ "this means it's an open ended interval, e.g. '< 100' for upper bound only.")
public class IntervalFacetEntry extends FacetEntry {

	public final String type = "interval";

	private Number lowerBound;

	private Number upperBound;

	/**
	 * 
	 * @param lowerBound
	 *        Nullable
	 * @param upperBound
	 *        Nullable
	 * @param docCount
	 * @param link
	 */
	public IntervalFacetEntry(Number lowerBound, Number upperBound, long docCount, String link, boolean isSelected) {
		super(getLabel(lowerBound, upperBound), docCount, link, isSelected);
		this.lowerBound = lowerBound;
		this.upperBound = upperBound;
	}

	/**
	 * simple label that considers nullable lower or upper bound value.
	 * 
	 * @param from
	 * @param to
	 * @return
	 */
	private static String getLabel(Number from, Number to) {
		if (from == null && to == null) throw new IllegalArgumentException("Eiter lower bound or upper bound must be defined! Both are null however.");
		if (from == null) {
			return "< " + to.toString();
		}
		if (to == null) {
			return "> " + from.toString();
		}
		return from.toString() + "-" + to.toString();
	}

}
