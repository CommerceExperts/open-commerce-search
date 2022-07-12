package de.cxp.ocs.elasticsearch.query.builder;

import static de.cxp.ocs.config.QueryBuildingSetting.acceptNoResult;
import static de.cxp.ocs.config.QueryBuildingSetting.allowParallelSpellcheck;
import static de.cxp.ocs.config.QueryBuildingSetting.analyzer;
import static de.cxp.ocs.config.QueryBuildingSetting.fuzziness;
import static de.cxp.ocs.config.QueryBuildingSetting.isQueryWithShingles;
import static de.cxp.ocs.config.QueryBuildingSetting.minShouldMatch;
import static de.cxp.ocs.config.QueryBuildingSetting.multimatch_type;
import static de.cxp.ocs.config.QueryBuildingSetting.operator;
import static de.cxp.ocs.config.QueryBuildingSetting.tieBreaker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import org.apache.lucene.search.BooleanClause.Occur;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.MultiMatchQueryBuilder.Type;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;

import de.cxp.ocs.config.FieldConfigAccess;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.config.QueryBuildingSetting;
import de.cxp.ocs.elasticsearch.query.MasterVariantQuery;
import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;
import de.cxp.ocs.elasticsearch.query.model.WeightedWord;
import de.cxp.ocs.spi.search.ESQueryFactory;
import de.cxp.ocs.util.ESQueryUtils;
import de.cxp.ocs.util.Util;
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

	private Map<QueryBuildingSetting, String>	querySettings;
	private Map<String, Float>					weightedFields;

	@Getter
	private String name;

	@Override
	public void initialize(String name, Map<QueryBuildingSetting, String> settings, Map<String, Float> fieldWeights, FieldConfigAccess fieldConfig) {
		if (name != null) this.name = name;
		querySettings = settings;
		weightedFields = fieldWeights;
	}

	@Override
	public MasterVariantQuery createQuery(List<QueryStringTerm> searchTerms) {
		// set fuzziness
		String fuzzySetting = querySettings.get(fuzziness);
		Fuzziness fuzziness = Fuzziness.AUTO;
		if (fuzzySetting != null) {
			Optional<Number> edits = Util.tryToParseAsNumber(fuzzySetting);
			if (edits.isPresent()) {
				fuzziness = Fuzziness.fromEdits(edits.get().intValue());
			}
			// if fuzziness is set explicitly, append fuzzy operator (~) to each
			// term
			searchTerms.forEach(word -> {
				if (word instanceof WeightedWord) {
					((WeightedWord) word).setFuzzy(true);
				}
			});
		}

		String defaultOperator = querySettings.getOrDefault(operator, "OR");
		String queryString = buildQueryString(searchTerms, defaultOperator);
		QueryStringQueryBuilder esQuery = QueryBuilders.queryStringQuery(queryString)
				.minimumShouldMatch(querySettings.getOrDefault(minShouldMatch, null))
				.analyzer(querySettings.getOrDefault(analyzer, null))
				.quoteAnalyzer("whitespace")
				.fuzziness(fuzziness)
				.defaultOperator(Operator.fromString(defaultOperator))
				.tieBreaker(Util.tryToParseAsNumber(querySettings.getOrDefault(tieBreaker, "0")).orElse(0).floatValue())
				.autoGenerateSynonymsPhraseQuery(false);

		// set type
		Type searchType = Type.valueOf(querySettings.getOrDefault(multimatch_type, Type.CROSS_FIELDS.name())
				.toUpperCase());
		esQuery.type(searchType);

		// ex: best_fields_2<70%(braune damenhose)
		esQuery.queryName(
				(name != null ? name
						: searchType.name().toLowerCase()
								+ (esQuery.minimumShouldMatch() != null ? "_" + esQuery.minimumShouldMatch() : ""))
						+ "(" + queryString + ")");

		for (Entry<String, Float> fieldWeight : weightedFields.entrySet()) {
			esQuery.field(FieldConstants.SEARCH_DATA + "." + fieldWeight.getKey(), fieldWeight.getValue());
		}

		return new MasterVariantQuery(esQuery,
				new VariantQueryFactory().createMatchAnyTermQuery(searchTerms),
				!"0".equals(fuzzySetting),
				Boolean.parseBoolean(querySettings.getOrDefault(acceptNoResult, "false")));
	}

	private String buildQueryString(List<QueryStringTerm> searchTerms, String operator) {
		List<QueryStringTerm> excludeTerms = new ArrayList<>();
		List<QueryStringTerm> includeTerms = new ArrayList<>();
		for (QueryStringTerm term : searchTerms) {
			if (Occur.MUST_NOT.equals(term.getOccur())) {
				excludeTerms.add(term);
			}
			else {
				includeTerms.add(term);
			}
		}

		StringBuilder queryStringBuilder = new StringBuilder();
		if ("OR".equals(operator)) {
			queryStringBuilder
					.append(ESQueryUtils.buildQueryString(includeTerms, " OR "));
		}
		else {
			queryStringBuilder
				.append('(')
					.append(ESQueryUtils.buildQueryString(includeTerms, " "))
				.append(')');
		}

		// search for all terms quoted with higher weight,
		// in order to prefer phrase matches generally
		queryStringBuilder
				.append(" OR ")
				.append('(')
				.append('"')
				.append(ESQueryUtils.buildQueryString(includeTerms, " "))
				.append('"')
				.append(")^1.5");

		// build shingle variants if enabled
		if (querySettings.getOrDefault(isQueryWithShingles, "false").equalsIgnoreCase("true")) {
			attachQueryTermsAsShingles(includeTerms, queryStringBuilder);
		}

		if (excludeTerms.size() > 0) {
			queryStringBuilder
					.append(' ')
					.append(ESQueryUtils.buildQueryString(excludeTerms, " "));
		}

		return queryStringBuilder.toString();
	}

	private void attachQueryTermsAsShingles(List<QueryStringTerm> includeTerms, StringBuilder queryStringBuilder) {
		for (int i = 0; i < includeTerms.size() - 1; i++) {
			List<QueryStringTerm> shingledSearchTerms = new ArrayList<>(includeTerms);

			QueryStringTerm wordA = shingledSearchTerms.remove(i);
			// shingled word has double weight, since it practically matches
			// two input tokens
			WeightedWord shingleWord = new WeightedWord(wordA.getWord() + includeTerms.get(i + 1).getWord(), 2f);
			shingleWord.setFuzzy(wordA instanceof WeightedWord && ((WeightedWord) wordA).isFuzzy());
			shingledSearchTerms.set(i, shingleWord);
			queryStringBuilder.append(" OR ")
					.append('(')
					.append(ESQueryUtils.buildQueryString(shingledSearchTerms, " "));


			// the whole variant with shingles has a lower weight
			// than the original terms
			queryStringBuilder.append(")^0.9");
		}
	}

	@Override
	public boolean allowParallelSpellcheckExecution() {
		return Boolean.parseBoolean(querySettings.getOrDefault(allowParallelSpellcheck, "true"));
	}

}
