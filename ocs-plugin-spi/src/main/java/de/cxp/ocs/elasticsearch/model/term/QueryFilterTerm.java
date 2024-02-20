package de.cxp.ocs.elasticsearch.model.term;

import org.apache.commons.text.StringEscapeUtils;

import lombok.*;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
@NoArgsConstructor
public class QueryFilterTerm implements QueryStringTerm {

    @NonNull
    private String  field;
    @NonNull
    private String	rawTerm;

    private Occur   occur			= Occur.MUST;

    @Override
    public String toQueryString() {
        return occur.toString()
            + field
            + ":"
            + StringEscapeUtils.escapeJava(rawTerm);
    }

	@Override
	public boolean isEnclosed() {
		return false;
	}

    @Override
    public String toString() {
        return toQueryString();
    }
}