package de.cxp.ocs.elasticsearch.query;

import static de.cxp.ocs.config.QueryBuildingSetting.analyzer;
import static de.cxp.ocs.config.QueryBuildingSetting.fuzziness;
import static de.cxp.ocs.config.QueryBuildingSetting.isQueryWithShingles;
import static de.cxp.ocs.config.QueryBuildingSetting.minShouldMatch;
import static de.cxp.ocs.config.QueryBuildingSetting.multimatch_type;
import static de.cxp.ocs.config.QueryBuildingSetting.operator;
import static de.cxp.ocs.config.QueryBuildingSetting.phraseSlop;
import static de.cxp.ocs.config.QueryBuildingSetting.quoteAnalyzer;
import static de.cxp.ocs.config.QueryBuildingSetting.tieBreaker;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.MultiMatchQueryBuilder.Type;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;

import de.cxp.ocs.config.*;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.model.term.WeightedTerm;
import de.cxp.ocs.elasticsearch.model.util.EscapeUtil;
import de.cxp.ocs.elasticsearch.model.util.QueryStringUtil;
import de.cxp.ocs.elasticsearch.model.visitor.AbstractTermVisitor;
import de.cxp.ocs.util.Util;
import lombok.NonNull;

public class StandardQueryFactory {

	@NonNull
	private final Map<QueryBuildingSetting, String>	querySettings;
	private final Map<String, Float>				fieldWeights;

	public StandardQueryFactory(Map<QueryBuildingSetting, String> querySettings, Map<String, Float> fieldWeights, FieldConfigAccess fieldConfig) {
		this.querySettings = querySettings;
		this.fieldWeights = validateFields(fieldWeights, fieldConfig);
	}

	private Map<String, Float> validateFields(Map<String, Float> weightedFields, FieldConfigAccess fieldConfig) {
		Map<String, Float> validatedFields = new HashMap<>();
		for (Entry<String, Float> fieldWeight : weightedFields.entrySet()) {
			String fieldName = fieldWeight.getKey();
			if (fieldName.startsWith(FieldConstants.SEARCH_DATA + ".")) {
				fieldName = fieldName.substring(FieldConstants.SEARCH_DATA.length() + 1);
			}

			int subFieldDelimiterIndex = fieldName.indexOf('.');
			String subField = "";
			if (!fieldName.endsWith("*") && subFieldDelimiterIndex > 0) {
				subField = fieldName.substring(subFieldDelimiterIndex);
				fieldName = fieldName.substring(0, subFieldDelimiterIndex);
			}

			if (fieldConfig.getMatchingField(fieldName, FieldUsage.SEARCH).map(Field::isMasterLevel).orElse(false)) {
				validatedFields.put(FieldConstants.SEARCH_DATA + "." + fieldName + subField, fieldWeight.getValue());
			}
		}
		return validatedFields;
	}

	public QueryStringQueryBuilder create(ExtendedQuery parsedQuery) {
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
				.autoGenerateSynonymsPhraseQuery(false)
				.fields(fieldWeights)
				.queryName(queryString);

		// set type
		Type searchType = Type.valueOf(querySettings.getOrDefault(multimatch_type, Type.CROSS_FIELDS.name())
				.toUpperCase());
		esQuery.type(searchType);

		return esQuery;
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

}
