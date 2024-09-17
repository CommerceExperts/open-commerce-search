package de.cxp.ocs.elasticsearch.query.builder;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfigAccess;
import de.cxp.ocs.config.QueryBuildingSetting;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.model.util.EscapeUtil;
import de.cxp.ocs.elasticsearch.query.TextMatchQuery;
import de.cxp.ocs.spi.search.ESQueryFactory;
import lombok.Getter;
import lombok.Setter;
import org.elasticsearch.index.query.DisMaxQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static de.cxp.ocs.config.QueryBuildingSetting.acceptNoResult;
import static de.cxp.ocs.util.ESQueryUtils.validateSearchFields;

public class DisMaxQueryFactory implements ESQueryFactory {
    private final Map<QueryBuildingSetting, String> queryBuildingSettings = new HashMap<>();
    private final Map<String, Float> masterFields = new HashMap<>();
    private final Map<String, Float> variantFields = new HashMap<>();
    @Getter
    @Setter
    private String name;

    @Override
    public void initialize(String name, Map<QueryBuildingSetting, String> settings, Map<String, Float> fieldWeights, FieldConfigAccess fieldConfig) {
        this.name = name;
        queryBuildingSettings.putAll(settings);
        masterFields.putAll(validateSearchFields(fieldWeights, fieldConfig, Field::isMasterLevel));
        variantFields.putAll(validateSearchFields(fieldWeights, fieldConfig, Field::isVariantLevel));
    }

    @Override
    public TextMatchQuery<QueryBuilder> createQuery(ExtendedQuery parsedQuery) {
        String searchPhrase = parsedQuery.getSearchQuery().getInputTerms().stream()
                .map(EscapeUtil::escapeReservedESCharacters)
                .collect(Collectors.joining(" "));

        DisMaxQueryBuilder mainQuery = createDisMaxQueryBuilder(searchPhrase, masterFields);
        QueryBuilder variantQuery = variantFields.isEmpty() ? QueryBuilders.matchAllQuery() : createDisMaxQueryBuilder(searchPhrase, variantFields);

        return new TextMatchQuery<>(mainQuery, variantQuery, false,
                Boolean.parseBoolean(queryBuildingSettings.getOrDefault(acceptNoResult, "true")));
    }

    private DisMaxQueryBuilder createDisMaxQueryBuilder(String searchPhrase, Map<String, Float> fields) {
        MultiMatchQueryBuilder multiMatchQuery = QueryBuilders
                .multiMatchQuery(searchPhrase)
                .fields(fields);
        MultiMatchQueryBuilder matchPhrasePrefixQuery = QueryBuilders
                .multiMatchQuery(searchPhrase)
                .type(queryBuildingSettings.getOrDefault(QueryBuildingSetting.multimatch_type, String.valueOf(MultiMatchQueryBuilder.Type.PHRASE_PREFIX)))
                .fields(fields);
        return QueryBuilders
                .disMaxQuery()
                .add(multiMatchQuery)
                .add(matchPhrasePrefixQuery)
                .tieBreaker(Float.parseFloat(queryBuildingSettings.getOrDefault(QueryBuildingSetting.tieBreaker, "0.0")));
    }

    @Override
    public boolean allowParallelSpellcheckExecution() {
        return false;
    }

}

