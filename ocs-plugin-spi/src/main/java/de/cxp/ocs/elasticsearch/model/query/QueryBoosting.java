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
    private String rawTerm;

    @NonNull
	private final BoostType type;

	private float weight = 1f;

    public enum BoostType {
        UP, DOWN;
    }

	public QueryBoosting(String term, BoostType boostType, float boostWeight) {
		rawTerm = term;
		type = boostType;
		weight = boostWeight;
	}

	public boolean isUpBoosting() {
		return BoostType.UP.equals(type);
	}

}