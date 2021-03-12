package de.cxp.ocs.elasticsearch.query.builder;

import java.util.List;

import org.elasticsearch.index.query.QueryBuilders;

import de.cxp.ocs.elasticsearch.query.ESQueryBuilder;
import de.cxp.ocs.elasticsearch.query.MasterVariantQuery;
import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * a broad query that tries to get good results without loosing too much
 * precision.
 */
@RequiredArgsConstructor
public class MatchAllQueryBuilder implements ESQueryBuilder {

	@Setter
	@Getter
	private String name = "_match_all";

	@Override
	public MasterVariantQuery buildQuery(List<QueryStringTerm> searchTerms) {
		return new MasterVariantQuery(
				QueryBuilders
				.matchAllQuery()
				.queryName(name == null ? "_match_all" : name),
				QueryBuilders.matchAllQuery(),
				// isWithSpellCorrect=true because we use match anything anyways
				true,
				// accept no results, because if "matchAll" matches nothing, no query will
				true);
	}

	@Override
	public boolean allowParallelSpellcheckExecution() {
		// not necessary, because we have enough results anyways
		return false;
	}
}
