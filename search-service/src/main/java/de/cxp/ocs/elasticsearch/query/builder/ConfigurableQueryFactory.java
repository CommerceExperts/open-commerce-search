package de.cxp.ocs.elasticsearch.query.builder;

import static de.cxp.ocs.config.QueryBuildingSetting.acceptNoResult;
import static de.cxp.ocs.config.QueryBuildingSetting.allowParallelSpellcheck;
import static de.cxp.ocs.util.ESQueryUtils.validateSearchFields;

import java.util.Map;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryStringQueryBuilder;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfigAccess;
import de.cxp.ocs.config.QueryBuildingSetting;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.query.MasterVariantQuery;
import de.cxp.ocs.elasticsearch.query.StandardQueryFactory;
import de.cxp.ocs.spi.search.ESQueryFactory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * <p>
 * Factory that exposes the flexibility of Elasticsearch query-string-query to
 * OCS using a configuration. <a href=
 * "https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-query-string-query.html">See
 * the query-string-query documentation for details.</a>
 * </p>
 * Supported {@link QueryBuildingSetting}s:
 * <ul>
 * <li>fuzziness</li>
 * <li>operator</li>
 * <li>analyzer</li>
 * <li>minShouldMatch</li>
 * <li>tieBreaker</li>
 * <li>multimatch_type</li>
 * <li>acceptNoResult: if set to true, no results will be accepted and no
 * further search is done</li>
 * <li>isQueryWithShingles: build term shingles for multi-term queries</li>
 * <li>allowParallelSpellcheck: run parallel spell-check with this query. If
 * terms could be corrected and 0 results are found, this query is built again
 * with the corrected terms.</li>
 * </ul>
 */
@RequiredArgsConstructor
public class ConfigurableQueryFactory implements ESQueryFactory {

	private Map<QueryBuildingSetting, String> querySettings;

	@Getter
	private String name;

	private StandardQueryFactory	mainQueryFactory;
	private VariantQueryFactory		variantQueryFactory;

	@Override
	public void initialize(String name, Map<QueryBuildingSetting, String> settings, Map<String, Float> fieldWeights, FieldConfigAccess fieldConfig) {
		if (name != null) this.name = name;
		querySettings = settings;
		mainQueryFactory = new StandardQueryFactory(settings, validateSearchFields(fieldWeights, fieldConfig, Field::isMasterLevel));
		variantQueryFactory = new VariantQueryFactory(validateSearchFields(fieldWeights, fieldConfig, Field::isVariantLevel));
	}

	@Override
	public MasterVariantQuery<QueryBuilder> createQuery(ExtendedQuery parsedQuery) {
		QueryStringQueryBuilder esQuery = mainQueryFactory.create(parsedQuery);

		return new MasterVariantQuery<>(esQuery,
				variantQueryFactory.createMatchAnyTermQuery(parsedQuery),
				esQuery.fuzziness().asDistance() > 0,
				Boolean.parseBoolean(querySettings.getOrDefault(acceptNoResult, "false")));
	}

	@Override
	public boolean allowParallelSpellcheckExecution() {
		return Boolean.parseBoolean(querySettings.getOrDefault(allowParallelSpellcheck, "true"));
	}

}
