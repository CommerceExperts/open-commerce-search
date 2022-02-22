package de.cxp.ocs.elasticsearch.query.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.lucene.search.BooleanClause.Occur;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
@NoArgsConstructor
public class QueryFilterTerm implements QueryStringTerm {

    @NonNull
    private String  field;
    @NonNull
    private String	word;

    private Occur   occur			= Occur.MUST;

    @Override
    public String toQueryString() {

        return occur.toString()
            + field
            + ":"
            + StringEscapeUtils.escapeJava(word);
    }

    @Override
    public String toString() {
        return toQueryString();
    }
}