package de.cxp.ocs.elasticsearch;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.lucene.search.BooleanClause.Occur;

import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.elasticsearch.model.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.model.term.QueryFilterTerm;
import de.cxp.ocs.elasticsearch.model.term.QueryStringTerm;
import de.cxp.ocs.spi.search.UserQueryAnalyzer;
import de.cxp.ocs.spi.search.UserQueryPreprocessor;
import de.cxp.ocs.util.InternalSearchParams;
import de.cxp.ocs.util.SearchParamsParser;
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

	public ExtendedQuery preprocessQuery(InternalSearchParams parameters, Map<String, Object> searchMetaData) {
		ExtendedQuery parsedQuery;
		if (!parameters.includeMainResult) {
			parsedQuery = ExtendedQuery.MATCH_ALL;
			searchMetaData.put("includeMainResult", "false");
		}
		else if (parameters.userQuery != null && !parameters.userQuery.isEmpty()) {
			String preprocessedQuery = parameters.userQuery;
			for (UserQueryPreprocessor preprocessor : userQueryPreprocessors) {
				preprocessedQuery = preprocessor.preProcess(preprocessedQuery, searchMetaData);
			}
			searchMetaData.put("preprocessedQuery", preprocessedQuery);

			parsedQuery = userQueryAnalyzer.analyze(preprocessedQuery);
			parsedQuery = handleFiltersOnFields(parameters, parsedQuery);
			searchMetaData.put("analyzedQuery", parsedQuery.getSearchQuery().toQueryString() + " " + parsedQuery.getFilters());
			searchMetaData.put("analyzerFilters", parameters.inducedFilters);
		}
		else {
			parsedQuery = ExtendedQuery.MATCH_ALL;
			searchMetaData.put("noQuery", true);
		}
		return parsedQuery;
	}

	/**
	 * For simple word filters the returned searchWords are used
	 * For any special Querqy style filtering they are put into the parameters object
	 * 
	 * @param parameters
	 * @param parsedQuery
	 * @return
	 */
	private ExtendedQuery handleFiltersOnFields(InternalSearchParams parameters, ExtendedQuery parsedQuery) {
		if (parsedQuery.getFilters().isEmpty()) return parsedQuery;

		// Pull all QueryFilterTerm items into a list of its own
		List<QueryStringTerm> remainingFilters = new ArrayList<>();

		Map<String, InternalResultFilter> filtersAsMap = parsedQuery.getFilters().stream()
				.filter(Objects::nonNull)
				.filter(searchWord -> searchWord instanceof QueryFilterTerm || !remainingFilters.add(searchWord))
				// Generate the filters and add them
				.map(term -> (QueryFilterTerm) term)
				.map(queryFilter -> toInternalResultFilter(queryFilter))
				.filter(Objects::nonNull)
				.collect(Collectors.toMap(iField -> iField.getField().getName(), f -> f, this::combineInternalFilter));

		if (filtersAsMap.isEmpty()) {
			return parsedQuery;
		} else {
			parameters.inducedFilters = new ArrayList<>(filtersAsMap.values());
			return new ExtendedQuery(parsedQuery.getSearchQuery(), remainingFilters);
		}
	}


	private InternalResultFilter toInternalResultFilter(QueryFilterTerm queryFilter) {
		String paramValue = Occur.MUST_NOT.equals(queryFilter.getOccur()) ? SearchParamsParser.NEGATE_FILTER_PREFIX + queryFilter.getRawTerm() : queryFilter.getRawTerm();
		return SearchParamsParser.parseSingleFilter(queryFilter.getField(), paramValue, fieldIndex, locale).orElse(null);
	}

	private InternalResultFilter combineInternalFilter(InternalResultFilter f1, InternalResultFilter f2) {
		if (f1 == null) return f2;
		if (f2 == null) return f1;
		// if one filter is negated but the other is not, only keep the including filter
		if (f1.isNegated() && !f2.isNegated()) return f2;
		if (!f1.isNegated() && f2.isNegated()) return f1;

		Set<String> values = new HashSet<>(f1.getValues().length + f2.getValues().length);
		values.addAll(Arrays.asList(f1.getValues()));
		values.addAll(Arrays.asList(f2.getValues()));

		return SearchParamsParser.toInternalFilter(f1.getField(), values.toArray(new String[0]), locale, f1.isFilterOnId(), f1.isNegated());
	}

}
