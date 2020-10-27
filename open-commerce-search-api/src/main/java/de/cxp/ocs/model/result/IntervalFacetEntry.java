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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((lowerBound == null) ? 0 : lowerBound.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		result = prime * result + ((upperBound == null) ? 0 : upperBound.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!super.equals(obj)) return false;
		if (getClass() != obj.getClass()) return false;
		IntervalFacetEntry other = (IntervalFacetEntry) obj;
		if (lowerBound == null) {
			if (other.lowerBound != null) return false;
		}
		else if (!lowerBound.equals(other.lowerBound)) return false;
		if (type == null) {
			if (other.type != null) return false;
		}
		else if (!type.equals(other.type)) return false;
		if (upperBound == null) {
			if (other.upperBound != null) return false;
		}
		else if (!upperBound.equals(other.upperBound)) return false;
		return true;
	}

}
