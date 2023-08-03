package de.cxp.ocs.elasticsearch.query.builder;

import static de.cxp.ocs.config.QueryBuildingSetting.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.MultiMatchQueryBuilder.Type;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;

import de.cxp.ocs.config.FieldConfigAccess;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.config.QueryBuildingSetting;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.model.term.WeightedTerm;
import de.cxp.ocs.elasticsearch.model.util.EscapeUtil;
import de.cxp.ocs.elasticsearch.model.util.QueryStringUtil;
import de.cxp.ocs.elasticsearch.model.visitor.AbstractTermVisitor;
import de.cxp.ocs.elasticsearch.query.MasterVariantQuery;
import de.cxp.ocs.spi.search.ESQueryFactory;
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
	public MasterVariantQuery createQuery(ExtendedQuery parsedQuery) {
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
			parsedQuery.getSearchQuery().accept(AbstractTermVisitor.forEachTerm(word -> {
				if (word instanceof WeightedTerm) {
					((WeightedTerm) word).setFuzzy(true);
				}
			}));
		}

		String defaultOperator = querySettings.getOrDefault(operator, "OR");
		String queryString = buildQueryString(parsedQuery);
		QueryStringQueryBuilder esQuery = QueryBuilders.queryStringQuery(queryString)
				.minimumShouldMatch(querySettings.getOrDefault(minShouldMatch, null))
				.analyzer(querySettings.getOrDefault(analyzer, null))
				.quoteAnalyzer(querySettings.getOrDefault(quoteAnalyzer, "whitespace"))
				.fuzziness(fuzziness)
				.defaultOperator(Operator.fromString(defaultOperator))
				.phraseSlop(Util.tryToParseAsNumber(querySettings.getOrDefault(phraseSlop, "0")).orElse(0).intValue())
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
				new VariantQueryFactory().createMatchAnyTermQuery(parsedQuery),
				!"0".equals(fuzzySetting),
				Boolean.parseBoolean(querySettings.getOrDefault(acceptNoResult, "false")));
	}

	private String buildQueryString(ExtendedQuery parsedQuery) {
		StringBuilder queryStringBuilder = new StringBuilder();
		queryStringBuilder
				.append('(')
				.append(parsedQuery.toQueryString())
				.append(')');

		// search for all terms quoted with higher weight,
		// in order to prefer phrase matches generally
		queryStringBuilder
				.append(" OR ")
				.append('"')
				.append(getOriginalTermQuery(parsedQuery.getInputTerms()))
				.append('"')
				.append("^1.5");

		// if we have more than one term and the quoteAnalyzer is different than the analyzer,
		// then add a query variant that searches for the single terms quoted on their own.
		if (parsedQuery.getInputTerms().size() > 1 && !querySettings.getOrDefault(analyzer, "").equals(querySettings.get(quoteAnalyzer))) {
			queryStringBuilder
					.append(" OR ")
					.append('(')
					.append('"')
					.append(getOriginalTermQuery(parsedQuery.getInputTerms(), "\" \""))
					.append('"')
					.append(')')
					.append("^1.1");
		}

		// build shingle variants if enabled
		if (querySettings.getOrDefault(isQueryWithShingles, "false").equalsIgnoreCase("true")) {
			attachQueryTermsAsShingles(parsedQuery.getInputTerms(), queryStringBuilder);
		}

		if (parsedQuery.getFilters().size() > 0) {
			queryStringBuilder
					.append(' ')
					.append(QueryStringUtil.buildQueryString(parsedQuery.getFilters(), " "));
		}

		return queryStringBuilder.toString();
	}

	private String getOriginalTermQuery(List<String> inputTerms) {
		return getOriginalTermQuery(inputTerms, " ");
	}

	private String getOriginalTermQuery(List<String> inputTerms, String delimiter) {
		return inputTerms.stream()
				.map(EscapeUtil::escapeReservedESCharacters)
				.collect(Collectors.joining(delimiter));
	}

	private void attachQueryTermsAsShingles(List<String> inputTerms, StringBuilder queryStringBuilder) {
		for (int i = 0; i < inputTerms.size() - 1; i++) {
			List<String> shingledSearchTerms = new ArrayList<>(inputTerms.size());
			inputTerms.forEach(term -> shingledSearchTerms.add(EscapeUtil.escapeReservedESCharacters(term)));

			String wordA = shingledSearchTerms.remove(i);
			// shingled word has double weight, since it practically matches
			// two input tokens
			WeightedTerm shingleWord = new WeightedTerm(wordA + inputTerms.get(i + 1), 2f);
			shingledSearchTerms.set(i, shingleWord.toQueryString());

			queryStringBuilder.append(" OR ")
					.append('(')
					.append(StringUtils.join(shingledSearchTerms, ' '));

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
