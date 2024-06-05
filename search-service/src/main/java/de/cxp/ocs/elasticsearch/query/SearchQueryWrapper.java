package de.cxp.ocs.elasticsearch.query;

import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.QueryBuilder;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class SearchQueryWrapper {

	private final QueryBuilder	queryBuilder;
	private final Fuzziness		fuzziness;
	private final String		queryDescription;

}
