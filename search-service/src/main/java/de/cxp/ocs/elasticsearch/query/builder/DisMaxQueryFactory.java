package de.cxp.ocs.elasticsearch.query.builder;

import de.cxp.ocs.config.FieldConfigAccess;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.config.QueryBuildingSetting;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.model.util.EscapeUtil;
import de.cxp.ocs.elasticsearch.query.TextMatchQuery;
import de.cxp.ocs.spi.search.ESQueryFactory;
import lombok.Getter;
import lombok.Setter;
import org.elasticsearch.index.query.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static de.cxp.ocs.config.QueryBuildingSetting.acceptNoResult;

public class DisMaxQueryFactory implements ESQueryFactory {
    @Getter
    @Setter
    private String name;
    private final Map<QueryBuildingSetting, String> queryBuildingSettings = new HashMap<>();
    private final Map<String, Float> masterFields = new HashMap<>();
    private final Map<String, Float> variantFields = new HashMap<>();

    @Override
    public void initialize(String name, Map<QueryBuildingSetting, String> settings, Map<String, Float> fieldWeights, FieldConfigAccess fieldConfig) {
        this.name = name;
        queryBuildingSettings.putAll(settings);

        for (Map.Entry<String, Float> fieldAndWeight : fieldWeights.entrySet()) {
            String fieldName = fieldAndWeight.getKey().split("\\.")[0].replaceAll("[^a-zA-Z0-9-_]", "");

            fieldConfig.getField(fieldName).ifPresent(fieldConf -> {
                if (fieldConf.isVariantLevel()) {
                    variantFields.put(
                            FieldConstants.VARIANTS + "." + FieldConstants.SEARCH_DATA + "." + fieldName + ".ngram",
                            fieldAndWeight.getValue());
                }
                if (fieldConf.isMasterLevel()) {
                    masterFields.put(
                            FieldConstants.SEARCH_DATA + "." + fieldName + ".ngram",
                            fieldAndWeight.getValue());
                }
            });
        }
    }

    @Override
    public TextMatchQuery<QueryBuilder> createQuery(ExtendedQuery parsedQuery) {
        String searchPhrase = parsedQuery.getSearchQuery().getInputTerms().stream()
                .map(EscapeUtil::escapeReservedESCharacters)
                .collect(Collectors.joining(" "));

        ArrayList<String> masterFieldNames = new ArrayList<>(masterFields.keySet());

        // Using arbitrary fields and assuming at least 2 fields
        MultiMatchQueryBuilder multiMatchQuery = QueryBuilders
                .multiMatchQuery(searchPhrase)
                .field(masterFieldNames.get(0))
                .field(masterFieldNames.get(1));
        MatchPhrasePrefixQueryBuilder matchPhrasePrefixQuery = QueryBuilders
                .matchPhrasePrefixQuery(
                        masterFieldNames.get(0),
                        searchPhrase.substring(searchPhrase.lastIndexOf(" ") + 1));

        DisMaxQueryBuilder mainQuery = QueryBuilders.disMaxQuery()
                .add(multiMatchQuery)
                .add(matchPhrasePrefixQuery);

        MatchAllQueryBuilder variantQuery = QueryBuilders.matchAllQuery();

        return new TextMatchQuery<>(mainQuery, variantQuery, false,
                Boolean.parseBoolean(queryBuildingSettings.getOrDefault(acceptNoResult, "true")));
    }

    @Override
    public boolean allowParallelSpellcheckExecution() {
        return false;
    }

}

