package de.cxp.ocs.elasticsearch.model.query;

import de.cxp.ocs.elasticsearch.model.term.QueryStringTerm;
import lombok.*;
import org.apache.commons.text.StringEscapeUtils;

import java.util.List;

@Data
public class QueryBoosting {

    private String field;

    @NonNull
    private String rawTerm;

    @NonNull
    private Boosting boosting;

    public enum BoostType {
        UP, DOWN;
    }

    @Getter
    @ToString
    @RequiredArgsConstructor
    public static class Boosting {
        private final BoostType type;
        private final float weight;
    }
}