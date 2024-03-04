package de.cxp.ocs.elasticsearch.query.builder;

import java.util.Map;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import de.cxp.ocs.config.FieldConfigAccess;
import de.cxp.ocs.config.QueryBuildingSetting;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.query.TextMatchQuery;
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
	public TextMatchQuery<QueryBuilder> createQuery(ExtendedQuery parsedQuery) {
		return new TextMatchQuery<>(
				QueryBuilders
						.matchAllQuery()
						.queryName(name == null ? "_match_all" : name),
				null,
				// isWithSpellCorrect=true because we use match anything anyways
				true,
				// accept no results, because if "matchAll" matches nothing, no
				// query will
				true,
				name);
	}

	@Override
	public boolean allowParallelSpellcheckExecution() {
		// not necessary, because we have enough results anyways
		return false;
	}
}
