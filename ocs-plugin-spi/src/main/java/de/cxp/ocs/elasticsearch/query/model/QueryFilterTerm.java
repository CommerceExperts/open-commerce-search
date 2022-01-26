package de.cxp.ocs.elasticsearch.query.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.lucene.search.BooleanClause.Occur;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
@NoArgsConstructor
public class QueryFilterTerm implements QueryStringTerm {

    @NonNull
    private String	word;
    @NonNull
    private String  field;

    private Occur   occur			= Occur.MUST;

    public QueryFilterTerm(String field, String word, boolean isOrFilter) {
        this.field = field;
        this.word = word;
    }

    @Override
    public String toQueryString() {

        return occur.toString()
            + field
            + ":"
            + word;
    }

    @Override
    public String toString() {
        return toQueryString();
    }
}