package de.cxp.ocs.elasticsearch.query.builder;

import java.util.Map;

import de.cxp.ocs.config.FieldConfigAccess;
import de.cxp.ocs.config.QueryBuildingSetting;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.query.MasterVariantQuery;
import de.cxp.ocs.spi.search.ESQueryFactory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * Special QueryFactory used for arranged searches that do not want to have a main-result included.
 */
@RequiredArgsConstructor
public class NoResultQueryFactory implements ESQueryFactory {

	@Setter
	@Getter
	private String name = "_no_match";

	@Override
	public void initialize(String name, Map<QueryBuildingSetting, String> settings, Map<String, Float> fieldWeights, FieldConfigAccess fieldConfig) {
		if (name != null) this.name = name;
	}

	@Override
	public MasterVariantQuery createQuery(ExtendedQuery q) {
		return new MasterVariantQuery(null, null,
				// isWithSpellCorrect=true because we don't want to match anything
				true,
				// accept no results, because this is what we want
				true);
	}

	@Override
	public boolean allowParallelSpellcheckExecution() {
		// not necessary, because we don't want to match anything anyways
		return false;
	}
}
