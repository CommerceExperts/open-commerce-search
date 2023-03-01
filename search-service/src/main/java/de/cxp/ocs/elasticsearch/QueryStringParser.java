package de.cxp.ocs.elasticsearch;

import static de.cxp.ocs.util.SearchParamsParser.parseFilters;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.lucene.search.BooleanClause.Occur;

import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.elasticsearch.query.model.QueryFilterTerm;
import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;
import de.cxp.ocs.spi.search.UserQueryAnalyzer;
import de.cxp.ocs.spi.search.UserQueryPreprocessor;
import de.cxp.ocs.util.InternalSearchParams;
import de.cxp.ocs.util.SearchParamsParser;
import de.cxp.ocs.util.SearchQueryBuilder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class QueryStringParser {

	private final List<UserQueryPreprocessor>	userQueryPreprocessors;
	private final UserQueryAnalyzer				userQueryAnalyzer;
	@NonNull
	private final FieldConfigIndex				fieldIndex;

	private final Locale locale;

	public QueryStringParser(UserQueryAnalyzer userQueryAnalyzer, FieldConfigIndex fieldIndex, Locale l) {
		this(Collections.emptyList(), userQueryAnalyzer, fieldIndex, l);
	}

	public List<QueryStringTerm> preprocessQuery(InternalSearchParams parameters, Map<String, Object> searchMetaData) {
		List<QueryStringTerm> searchWords;
		if (!parameters.includeMainResult) {
			searchWords = Collections.emptyList();
			searchMetaData.put("includeMainResult", "false");
		}
		else if (parameters.userQuery != null && !parameters.userQuery.isEmpty()) {
			String preprocessedQuery = parameters.userQuery;
			for (UserQueryPreprocessor preprocessor : userQueryPreprocessors) {
				preprocessedQuery = preprocessor.preProcess(preprocessedQuery);
			}
			searchMetaData.put("preprocessedQuery", preprocessedQuery);

			searchWords = userQueryAnalyzer.analyze(preprocessedQuery);
			searchWords = handleFiltersOnFields(parameters, searchWords);
			searchMetaData.put("analyzedQuery", searchWords);
			searchMetaData.put("analyzerFilters", parameters.querqyFilters);
		}
		else {
			searchWords = Collections.emptyList();
			searchMetaData.put("noQuery", true);
		}
		return searchWords;
	}

	/**
	 * For simple word filters the returned searchWords are used
	 * For any special Querqy style filtering they are put into the parameters object
	 * 
	 * @param parameters
	 * @param searchWords
	 * @return
	 */
	private List<QueryStringTerm> handleFiltersOnFields(InternalSearchParams parameters, List<QueryStringTerm> searchWords) {
		// Pull all QueryFilterTerm items into a list of its own
		List<QueryStringTerm> remainingSearchWords = new ArrayList<>();

		Map<String, String> filtersAsMap = searchWords.stream()
				.filter(searchWord -> searchWord instanceof QueryFilterTerm || !remainingSearchWords.add(searchWord))
				// Generate the filters and add them
				.map(term -> (QueryFilterTerm) term)
				.collect(Collectors.toMap(QueryFilterTerm::getField, qf -> toParameterStyle(qf), (word1, word2) -> word1 + SearchQueryBuilder.VALUE_DELIMITER + word2));

		parameters.querqyFilters = parseFilters(filtersAsMap, fieldIndex, locale);

		return remainingSearchWords;
	}

	private String toParameterStyle(QueryFilterTerm queryFilter) {
		if (Occur.MUST_NOT.equals(queryFilter.getOccur())) {
			return SearchParamsParser.NEGATE_FILTER_PREFIX + queryFilter.getWord();
		}
		else {
			return queryFilter.getWord();
		}
	}

}
