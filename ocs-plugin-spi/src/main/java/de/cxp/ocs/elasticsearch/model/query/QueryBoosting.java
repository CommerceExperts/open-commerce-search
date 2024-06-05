package de.cxp.ocs.elasticsearch.model.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class QueryBoosting {

    private String field;

    @NonNull
    private final String rawTerm;

    @NonNull
	private final BoostType type;

	private final float weight;

    public enum BoostType { UP, DOWN }

	public boolean isUpBoosting() {
		return BoostType.UP.equals(type);
	}

	@Override
	public String toString() {
		return "boost:[" + (field == null ? "" : field + ":") + rawTerm + " " + type + "(" + weight + ")]";
	}

}