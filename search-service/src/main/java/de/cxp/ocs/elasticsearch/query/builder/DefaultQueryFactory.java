package de.cxp.ocs.elasticsearch.query.builder;

import static de.cxp.ocs.util.ESQueryUtils.validateSearchFields;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.MultiMatchQueryBuilder.Type;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryStringQueryBuilder;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfigAccess;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.config.QueryBuildingSetting;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.query.MasterVariantQuery;
import de.cxp.ocs.elasticsearch.query.StandardQueryFactory;
import de.cxp.ocs.spi.search.ESQueryFactory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * <p>
 * A predefined broad query that tries to get good results without loosing too
 * much precision.
 * </p>
 * <p>
 * No {@link QueryBuildingSetting}s are supported. Everything is predefined with
 * this query.
 * </p>
 * <p>
 * It should only be used with the main fields and their '.standard' subfield,
 * because it uses the standard analyzer.
 * </p>
 */
@RequiredArgsConstructor
public class DefaultQueryFactory implements ESQueryFactory {

	@Getter
	private String					name	= "defaultQuery";
	private StandardQueryFactory	mainQueryFactory;
	private VariantQueryFactory		variantQueryFactory;

	@Override
	public void initialize(String name, Map<QueryBuildingSetting, String> settings, Map<String, Float> fieldWeights, FieldConfigAccess fieldConfig) {
		if (name != null) this.name = name;

		Map<QueryBuildingSetting, String> extendedSettings = new HashMap<>(settings);
		extendedSettings.putIfAbsent(QueryBuildingSetting.analyzer, "standard");
		extendedSettings.putIfAbsent(QueryBuildingSetting.fuzziness, Fuzziness.AUTO.asString());
		extendedSettings.putIfAbsent(QueryBuildingSetting.minShouldMatch, "2<80%");
		extendedSettings.putIfAbsent(QueryBuildingSetting.tieBreaker, "0.8");
		extendedSettings.putIfAbsent(QueryBuildingSetting.multimatch_type, Type.CROSS_FIELDS.name());

		fieldWeights = !fieldWeights.isEmpty() ? fieldWeights : Collections.singletonMap(FieldConstants.SEARCH_DATA + ".*", 1f);

		mainQueryFactory = new StandardQueryFactory(extendedSettings, validateSearchFields(fieldWeights, fieldConfig, Field::isMasterLevel));
		variantQueryFactory = new VariantQueryFactory(validateSearchFields(fieldWeights, fieldConfig, Field::isVariantLevel));
	}

	@Override
	public MasterVariantQuery<QueryBuilder> createQuery(ExtendedQuery parsedQuery) {
		QueryStringQueryBuilder mainQuery = mainQueryFactory.create(parsedQuery);
		QueryBuilder variantQuery = variantQueryFactory.createMatchAnyTermQuery(parsedQuery);
		return new MasterVariantQuery<>(mainQuery, variantQuery, mainQuery.fuzziness().asDistance() > 0, false);
	}

	@Override
	public boolean allowParallelSpellcheckExecution() {
		return true;
	}
}
