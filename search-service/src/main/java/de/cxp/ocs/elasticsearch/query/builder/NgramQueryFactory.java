package de.cxp.ocs.elasticsearch.query.builder;

import static de.cxp.ocs.config.QueryBuildingSetting.acceptNoResult;
import static de.cxp.ocs.config.QueryBuildingSetting.minShouldMatch;
import static de.cxp.ocs.config.QueryBuildingSetting.multimatch_type;
import static de.cxp.ocs.config.QueryBuildingSetting.tieBreaker;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder.Type;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import de.cxp.ocs.config.FieldConfigAccess;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.config.QueryBuildingSetting;
import de.cxp.ocs.elasticsearch.model.query.AnalyzedQuery;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.model.util.EscapeUtil;
import de.cxp.ocs.elasticsearch.query.TextMatchQuery;
import de.cxp.ocs.spi.search.ESQueryFactory;
import de.cxp.ocs.util.Util;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * Builds a <a href=
 * "https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-multi-match-query.html">multi-match-query</a>
 * that uses the ngram sub-fields to handle decomposition and fuzziness.
 * </p>
 * 
 * Supported {@link QueryBuildingSetting}s:
 * <ul>
 * <li>tieBreaker</li>
 * <li>multimatch_type</li>
 * <li>minShouldMatch</li>
 * <li>acceptNoResult: if set to true, no results will be accepted and no
 * further search is done</li>
 * <li>allowParallelSpellcheck: run parallel spell-check with this query. If
 * terms could be corrected and 0 results are found, this query is built again
 * with the corrected terms.</li>
 * </ul>
 */
public class NgramQueryFactory implements ESQueryFactory {

	private final Map<QueryBuildingSetting, String>	querySettings	= new HashMap<>();
	private final Map<String, Float>				masterFields	= new HashMap<>();
	private final Map<String, Float>				variantFields	= new HashMap<>();

	@Getter
	@Setter
	private String name;

	@Override
	public void initialize(String name, Map<QueryBuildingSetting, String> settings, Map<String, Float> fieldWeights, FieldConfigAccess fieldConfig) {
		if (name != null) this.name = name;
		querySettings.putAll(settings);

		for (Entry<String, Float> fieldAndWeight : fieldWeights.entrySet()) {
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
		// TODO: do a ngram tokenization on each term separately and search each
		// "ngramed word" separately combined with boolean-should-clauses but
		// each using "best-field" match strategy.
		// minShouldMatch setting is then used twice: per splitted field and for
		// the words itself (the should clauses)
		String searchPhrase = parsedQuery.getSearchQuery().getInputTerms().stream()
				.map(EscapeUtil::escapeReservedESCharacters)
				.collect(Collectors.joining(" "));

		MultiMatchQueryBuilder mainQuery = buildEsQuery(searchPhrase);
		if (masterFields.size() > 0) {
			mainQuery.fields(masterFields);
		}
		String queryName = getLabel(parsedQuery.getSearchQuery());
		mainQuery.queryName(queryName);

		MultiMatchQueryBuilder variantQuery = buildEsQuery(searchPhrase);
		if (variantFields.size() > 0) {
			variantQuery.fields(variantFields);
		}
		variantQuery.queryName("variant-" + queryName);

		// isWithSpellCorrect=true because ngram is kind of a fuzzy matching
		return new TextMatchQuery<>(mainQuery, variantQuery, true,
				Boolean.parseBoolean(querySettings.getOrDefault(acceptNoResult, "true")));
	}

	private String getLabel(AnalyzedQuery analyzedQuery) {
		if (name == null) {
			name = "ngram-" + getMinShouldMatch();
		}
		return name + analyzedQuery.getInputTerms().toString();
	}

	private MultiMatchQueryBuilder buildEsQuery(String ngramPhrase) {
		MultiMatchQueryBuilder mainQuery = QueryBuilders
				.multiMatchQuery(ngramPhrase)
				.analyzer("ngram")
				.fuzziness(Fuzziness.ZERO)
				.minimumShouldMatch(getMinShouldMatch())
				.tieBreaker(Util.tryToParseAsNumber(querySettings.getOrDefault(tieBreaker, "0")).orElse(0).floatValue())
				.type(Type.valueOf(querySettings.getOrDefault(multimatch_type, Type.BEST_FIELDS.name())
						.toUpperCase()));
		return mainQuery;
	}

	private String getMinShouldMatch() {
		return querySettings.getOrDefault(minShouldMatch, "70%");
	}

	@Override
	public boolean allowParallelSpellcheckExecution() {
		return false;
	}
}
