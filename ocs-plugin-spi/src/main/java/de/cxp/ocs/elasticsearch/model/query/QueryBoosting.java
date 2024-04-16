package de.cxp.ocs.elasticsearch.model.query;

import lombok.*;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class QueryBoosting {

    private String field;

    @NonNull
    private String rawTerm;

    @NonNull
    private Boosting boosting;

    public enum BoostType {
        UP, DOWN;
    }

    @Data
    @ToString
    @RequiredArgsConstructor
    public static class Boosting {
        private final BoostType type;
        private final float weight;
    }

	public boolean isUpBoosting() {
		return BoostType.UP.equals(boosting.type);
	}

	public float getWeight() {
		return boosting.weight;
	}
}