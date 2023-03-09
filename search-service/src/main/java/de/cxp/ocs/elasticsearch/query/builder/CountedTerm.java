package de.cxp.ocs.elasticsearch.query.builder;

import de.cxp.ocs.elasticsearch.model.term.QueryStringTerm;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Delegate;

@RequiredArgsConstructor
@AllArgsConstructor
public class CountedTerm implements QueryStringTerm {

	@Delegate
	private final QueryStringTerm decorated;

	@Getter
	@Setter
	private int termFrequency = -1;

}
