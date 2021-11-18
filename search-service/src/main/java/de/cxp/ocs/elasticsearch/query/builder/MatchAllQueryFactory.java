package de.cxp.ocs.elasticsearch.query.builder;

import java.util.List;
import java.util.Map;

import org.elasticsearch.index.query.QueryBuilders;

import de.cxp.ocs.config.FieldConfigAccess;
import de.cxp.ocs.config.QueryBuildingSetting;
import de.cxp.ocs.elasticsearch.query.MasterVariantQuery;
import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;
import de.cxp.ocs.spi.search.ESQueryFactory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor
public class MatchAllQueryFactory implements ESQueryFactory {

	@Setter
	@Getter
	private String name = "_match_all";

	@Override
	public void initialize(String name, Map<QueryBuildingSetting, String> settings, Map<String, Float> fieldWeights, FieldConfigAccess fieldConfig) {
		if (name != null) this.name = name;
	}

	@Override
	public MasterVariantQuery createQuery(List<QueryStringTerm> searchTerms) {
		return new MasterVariantQuery(
				QueryBuilders
						.matchAllQuery()
						.queryName(name == null ? "_match_all" : name),
				QueryBuilders.matchAllQuery(),
				// isWithSpellCorrect=true because we use match anything anyways
				true,
				// accept no results, because if "matchAll" matches nothing, no
				// query will
				true);
	}

	@Override
	public boolean allowParallelSpellcheckExecution() {
		// not necessary, because we have enough results anyways
		return false;
	}
}
