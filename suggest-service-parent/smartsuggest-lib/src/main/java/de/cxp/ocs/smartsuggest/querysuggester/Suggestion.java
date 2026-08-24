package de.cxp.ocs.smartsuggest.querysuggester;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.util.BytesRef;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@RequiredArgsConstructor
public class Suggestion {

	@Getter
	private final String label;

	@Setter
	@Getter
	Map<String, String> payload;

	@Setter
	@Getter
	long weight = 0L;

	@Setter
	Set<BytesRef>	context;

	@Setter
	Set<String>		tags	= null;

	public Set<String> getTags() {
		// lazy deserialize context into tags set
		if (tags == null) {
			if (context == null) {
				tags = Collections.emptySet();
			} else {
				tags = new HashSet<>(context.size());
				for (BytesRef c : context) {
					tags.add(new String(c.bytes));
				}
			}
		}
		return tags;
	}

	@Override
	public String toString() {
		StringBuilder toString = new StringBuilder(label);
		if (weight != 0L) {
			toString.append(" (").append(weight).append(')');
		}
		if (context != null) {
			toString.append(" tags=[").append(getTags()).append(']');
		}
		if (payload != null) {
			toString.append(" payload=[").append(payload).append(']');
		}
		return toString.toString();
	}
}
