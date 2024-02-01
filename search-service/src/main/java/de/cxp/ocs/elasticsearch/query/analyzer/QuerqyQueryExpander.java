package de.cxp.ocs.elasticsearch.query.analyzer;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import de.cxp.ocs.elasticsearch.model.query.*;
import de.cxp.ocs.elasticsearch.model.term.*;
import de.cxp.ocs.elasticsearch.model.util.EscapeUtil;
import de.cxp.ocs.elasticsearch.model.util.QueryStringUtil;
import de.cxp.ocs.elasticsearch.query.analyzer.querqy.TransformingWhitespaceQuerqyParser;
import de.cxp.ocs.elasticsearch.query.analyzer.querqy.TransformingWhitespaceQuerqyParser.TransformationFlags;
import de.cxp.ocs.elasticsearch.query.analyzer.querqy.TransformingWhitespaceQuerqyParserFactory;
import de.cxp.ocs.spi.search.ConfigurableExtension;
import de.cxp.ocs.spi.search.UserQueryAnalyzer;
import de.cxp.ocs.util.StringUtils;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import querqy.model.*;
import querqy.model.Clause.Occur;
import querqy.parser.QuerqyParser;
import querqy.parser.WhiteSpaceQuerqyParser;
import querqy.rewrite.RewriteChain;
import querqy.rewrite.RewriterFactory;
import querqy.rewrite.commonrules.QuerqyParserFactory;
import querqy.rewrite.commonrules.SimpleCommonRulesRewriterFactory;
import querqy.rewrite.commonrules.WhiteSpaceQuerqyParserFactory;
import querqy.rewrite.commonrules.select.ExpressionCriteriaSelectionStrategyFactory;
import querqy.rewrite.experimental.LocalSearchEngineRequestAdapter;

@Slf4j
public class QuerqyQueryExpander implements UserQueryAnalyzer, ConfigurableExtension {

	public final static String	RULES_URL_PROPERTY_NAME				= "common_rules_url";
	public final static String	DO_ASCIIFY_RULES_PROPERTY_NAME		= "do_asciiy_rules";
	public final static String	DO_LOWERCASE_RULES_PROPERTY_NAME	= "do_lowercase_rules";

	private QuerqyParser	parser					= new WhiteSpaceQuerqyParser();
	private RewriteChain	rewriteChain			= null;
	private boolean			loggedMissingRewriter	= false;

	@Override
	public void initialize(Map<String, String> settings) {
		String commonRulesLocation = settings == null ? null : settings.get(RULES_URL_PROPERTY_NAME);
		Boolean isAsciifyRules = Boolean.parseBoolean(settings.get(DO_ASCIIFY_RULES_PROPERTY_NAME));
		Boolean isLowercaseRules = Boolean.parseBoolean(settings.get(DO_LOWERCASE_RULES_PROPERTY_NAME));
		try {
			if (commonRulesLocation == null) {
				log.error("no 'common_rules_url' provided! Won't enrich queries with querqy.");
			}
			else if (commonRulesLocation.startsWith("http")) {
				URL url = new URL(commonRulesLocation);
				BufferedInputStream resourceStream = new BufferedInputStream(url.openStream());
				rewriteChain = initFromStream(resourceStream, isAsciifyRules, isLowercaseRules);
			}
			else {
				InputStream resourceStream;

				File rulesFile = new File(commonRulesLocation);
				if (rulesFile.exists()) {
					resourceStream = new FileInputStream(rulesFile);
				}
				else {
					resourceStream = this.getClass().getClassLoader().getResourceAsStream(commonRulesLocation);
				}

				if (resourceStream != null) {
					rewriteChain = initFromStream(resourceStream, isAsciifyRules, isLowercaseRules);
				}
				else {
					log.error("resource '{}' not found. querqy rewriter not initialized", commonRulesLocation);
				}
			}
		}
		catch (Exception e) {
			log.error("Failed to load common rules from url {}", commonRulesLocation, e);
		}
		if (rewriteChain != null) {
			log.info("Successfully initialized querqy from rules at {}", commonRulesLocation);
		}
	}

	@SuppressWarnings("resource")
	private RewriteChain initFromStream(InputStream resourceStream, boolean asciifyRules, boolean lowercaseRules) throws IOException {
		Reader inputReader = new InputStreamReader(resourceStream);

		Collection<TransformingWhitespaceQuerqyParser.TransformationFlags> transformationFlags = new ArrayList<>(2);
		if (asciifyRules) {
			inputReader = StringUtils.asAsciifyCharFilter(inputReader);
			transformationFlags.add(TransformationFlags.ASCIIFY);
		}
		if (lowercaseRules) {
			inputReader = StringUtils.asLowercaseCharFilter(inputReader);
			transformationFlags.add(TransformationFlags.LOWERCASE);
		}

		QuerqyParserFactory parserFactory = transformationFlags.isEmpty() ? new WhiteSpaceQuerqyParserFactory() : new TransformingWhitespaceQuerqyParserFactory(EnumSet.copyOf(transformationFlags));
		parser = parserFactory.createParser();

		List<RewriterFactory> factories = Collections.singletonList(
				new SimpleCommonRulesRewriterFactory(
						"common_rules",
						inputReader,
						true,
						parserFactory,
						true,
						Collections.emptyMap(),
						new ExpressionCriteriaSelectionStrategyFactory(), false));
		return new RewriteChain(factories);
	}

	@Override
	public ExtendedQuery analyze(String userQuery) {
		// TODO: add extension point for "QueryExpander" and to be like that
		// if a different analyzer is used, it should also be possible to
		// construct an expanded query from a list of QueryStringTerm-s
		ExpandedQuery expandedQuery = new ExpandedQuery(parser.parse(userQuery));
		if (rewriteChain != null) {
			rewriteChain.rewrite(expandedQuery, new LocalSearchEngineRequestAdapter(rewriteChain, Collections.emptyMap()));
		}
		else if (!loggedMissingRewriter) {
			log.info("No rewriter initialized, will just analyze/expand but not enrich query.");
			loggedMissingRewriter = true;
		}
		return extractQueryString(expandedQuery);
	}

	private static ExtendedQuery extractQueryString(ExpandedQuery expandedQuery) {
		AnalyzedQuery searchQuery;
		if (expandedQuery.getUserQuery() instanceof querqy.model.Query) {
			TermFetcher termFetcher = new TermFetcher();
			querqy.model.Query userQuery = (querqy.model.Query) expandedQuery.getUserQuery();
			for (BooleanClause clause : userQuery.getClauses()) {
				clause.accept(termFetcher);
				termFetcher.reset();
			}
			searchQuery = termFetcher.getExtractedWords();
		}
		else if (expandedQuery.getUserQuery() != null) {
			log.error("not expected userquery of type" + expandedQuery.getUserQuery().getClass().getCanonicalName());
			TermFetcher termFetcher = new TermFetcher();
			expandedQuery.getUserQuery().accept(termFetcher);
			searchQuery = termFetcher.getExtractedWords();
		}
		else {
			searchQuery = null;
		}

		List<QueryStringTerm> filters;
		if (expandedQuery.getFilterQueries() != null) {
			Collection<QuerqyQuery<?>> filterQueries = expandedQuery.getFilterQueries();
			FilterFetcher filterFetcher = new FilterFetcher();
			filters = new ArrayList<>();
			for (QuerqyQuery<?> qq : filterQueries) {
				qq.accept(filterFetcher);
				filters.addAll(filterFetcher.extractedWords);
				filterFetcher.extractedWords.clear();
			}
		}
		else {
			filters = Collections.emptyList();
		}

		return new ExtendedQuery(searchQuery, filters);
	}

	@NoArgsConstructor
	static class TermCollector {

		private List<QueryStringTerm> inputTerms = new ArrayList<>();

		private Set<QueryStringTerm> associations = new LinkedHashSet<>();

		public TermCollector(QueryStringTerm inpuTerm) {
			addInputTerm(inpuTerm);
		}

		void addInputTerm(QueryStringTerm term) {
			inputTerms.add(term);
		}

		void addAssociation(QueryStringTerm term) {
			associations.add(term);
		}

		List<QueryStringTerm> build() {
			if (associations.size() > 0) {
				List<QueryStringTerm> inputQueryTerms = inputTerms.stream().map(input -> extractTermInput(input)).collect(Collectors.toList());
				QueryStringTerm inputQuery = inputQueryTerms.size() == 1 ? inputQueryTerms.get(0) : new ConceptTerm(inputTerms);
				return Collections.singletonList(new AssociatedTerm(inputQuery, associations));
			}
			else {
				if (inputTerms.size() == 1) {
					return Collections.singletonList(inputTerms.get(0));
				}
				else {
					List<QueryStringTerm> allTerms = new ArrayList<>(inputTerms.size());
					inputTerms.forEach(input -> allTerms.add(extractTermInput(input)));
					return allTerms;
				}
			}
		}

		private static QueryStringTerm extractTermInput(QueryStringTerm term) {
			if (term instanceof TermCollector) {
				List<QueryStringTerm> buildQuery = ((TermCollector) term).build();
				return buildQuery.size() == 1 ? buildQuery.get(0) : term;
			}
			return term;
		}

		public String toString() {
			List<QueryStringTerm> build = build();
			return build.size() == 1 ? build.get(0).toQueryString() : QueryStringUtil.buildQueryString(build, " ");
		}

		public void replaceInputTerm(String termKey, QueryStringTerm term) {
			for (int i = 0; i < inputTerms.size(); i++) {
				QueryStringTerm prevInputTerm = inputTerms.get(i);
				if (termKey.equals(prevInputTerm.getRawTerm())) {
					inputTerms.set(i, term);
					break;
				}
			}
		}
	}

	static class TermFetcher extends AbstractNodeVisitor<Node> {

		private QueryStringTerm initialTerm;

		Map<String, TermCollector>		termAssociationCollectors	= new LinkedHashMap<>();
		Map<String, List<String>>		inputTermCollectorRelations	= new HashMap<>();
		Map<String, QueryStringTerm>	inputTerms					= new LinkedHashMap<>();

		private Occur occur;

		public AnalyzedQuery getExtractedWords() {
			List<AnalyzedQuery> queryVariants = new ArrayList<>();
			Map<String, TermCollector> joinedTermAssociations = joinTermAssociations();
			extractQueryVariants(joinedTermAssociations, queryVariants);

			if (joinedTermAssociations.isEmpty()) {
				queryVariants.add(toAnalyzedQuery(inputTerms.values()));
			}

			return queryVariants.size() == 1 ? queryVariants.get(0) : new MultiVariantQuery(inputTerms.values(), queryVariants);
		}

		private Map<String, TermCollector> joinTermAssociations() {
			Map<String, TermCollector> joinedCollectors = new LinkedHashMap<>();
			for (TermCollector collector : termAssociationCollectors.values()) {
				String collectorKey = collector.inputTerms.toString();
				TermCollector joinedCollector = joinedCollectors.get(collectorKey);
				if (joinedCollector == null) {
					joinedCollectors.put(collectorKey, collector);
				}
				else {
					collector.associations.forEach(joinedCollector::addAssociation);
				}
			}
			return joinedCollectors;
		}

		private void extractQueryVariants(Map<String, TermCollector> joinedConcepts, List<AnalyzedQuery> queryVariants) {
			for (TermCollector joinedConcept : joinedConcepts.values()) {
				List<QueryStringTerm> queryVariant = buildQueryWithConcept(joinedConcept);
				queryVariants.add(toAnalyzedQuery(queryVariant));
			}
		}

		private List<QueryStringTerm> buildQueryWithConcept(TermCollector joinedConcept) {
			List<QueryStringTerm> queryVariant = new ArrayList<>();
			boolean conceptAdded = false;
			for (QueryStringTerm inputTerm : inputTerms.values()) {
				if (!joinedConcept.inputTerms.contains(inputTerm)) {
					queryVariant.add(inputTerm);
				}
				else if (!conceptAdded) {
					queryVariant.addAll(joinedConcept.build());
					conceptAdded = true;
				}
			}
			return queryVariant;
		}

		private AnalyzedQuery toAnalyzedQuery(Collection<QueryStringTerm> terms) {
			return terms.size() == 1 ? new SingleTermQuery(terms.iterator().next()) : new MultiTermQuery(terms);
		}

		public void reset() {
			initialTerm = null;
		}

		@Override
		public Node visit(Term term) {
			String termString = term.getValue().toString();
			if (EscapeUtil.escapeReservedESCharacters(termString).isBlank()) {
				return null;
			}

			WeightedTerm weightedWord;
			if (term instanceof BoostedTerm) {
				weightedWord = new WeightedTerm(termString, ((BoostedTerm) term).getBoost());
			}
			else {
				weightedWord = new WeightedTerm(termString);
			}

			// XXX might always be SHOULD - if so, this block can be removed cause useless
			if (occur != null) {
				weightedWord.setOccur(org.apache.lucene.search.BooleanClause.Occur.valueOf(occur.name()));

				// guard MUST/NOT terms from analyzer
				if (occur.equals(Occur.MUST) || occur.equals(Occur.MUST_NOT)) {
					weightedWord.setQuoted(true);
				}
				occur = null;
			}

			if (initialTerm == null) {
				initialTerm = weightedWord;
				inputTerms.put(weightedWord.getRawTerm(), weightedWord);
			}
			else {
				String termKey = initialTerm.getRawTerm();
				QueryStringTerm inputTerm = inputTerms.get(termKey);
				AssociatedTerm termAssociation = inputTerm instanceof AssociatedTerm ? (AssociatedTerm) inputTerm : new AssociatedTerm(inputTerm);
				termAssociation.putOrUpdate(weightedWord);
				inputTerms.put(initialTerm.getRawTerm(), termAssociation);

				if (inputTermCollectorRelations.containsKey(termKey)) {
					for (String conceptKey : inputTermCollectorRelations.get(termKey)) {
						TermCollector conceptBuilder = termAssociationCollectors.get(conceptKey);
						conceptBuilder.replaceInputTerm(initialTerm.getRawTerm(), termAssociation);
					}
				}
			}

			return super.visit(term);
		}

		@Override
		public Node visit(BooleanQuery booleanQuery) {
			// the expanded userquery always returns as a boolean query with 1 clause
			// in that case we want to continue visiting the inner nodes.
			// if one of the inner nodes is a boolean, it contains 2 or more (dismax) clauses
			// that can be considered as phrase
			if (booleanQuery.getClauses() != null && booleanQuery.getClauses().size() > 1) {

				WeightedTerm term = extractTerm(booleanQuery);

				// remember concept via the synonym phrase..
				String conceptKey = term.getRawTerm();
				TermCollector termCollector = termAssociationCollectors.computeIfAbsent(conceptKey, (x) -> new TermCollector());
				termCollector.addAssociation(term);

				// ..and link it via the input term, so that term synonyms can be attached to
				if (initialTerm != null) {
					String inputTermKey = initialTerm.getRawTerm();
					var inputTerm = inputTerms.get(inputTermKey);
					termCollector.addInputTerm(inputTerm);
					// memorize relation that is used in case the input term is later updated with an association
					inputTermCollectorRelations.computeIfAbsent(inputTermKey, w -> new ArrayList<>(1)).add(conceptKey);
				}

				// end visitor calls
				return null;
			}
			else {
				// this is an outer boolean query - go on visiting the inner nodes
				return super.visit(booleanQuery);
			}
		}

		private WeightedTerm extractTerm(BooleanQuery booleanQuery) {
			float weight = 1f;
			StringBuilder text = new StringBuilder();
			boolean isPhrase = false;
			for (BooleanClause clause : booleanQuery.getClauses()) {
				Optional<WeightedTerm> weightedWord = extractSingleTerm(clause).map(this::toWeightedWord);
				if (weightedWord.isPresent()) {
					if (text.length() > 0) {
						text.append(' ');
						isPhrase = true;
					}
					text.append(weightedWord.get().getRawTerm());
					weight = weightedWord.get().getWeight();
				}
			}

			// for some time we encouraged rules to multi-term queries to be enclosed in brackets. Remove them here,
			// since they have no value.
			if (text.charAt(0) == '(') {
				text.deleteCharAt(0);
			}
			if (text.charAt(text.length() - 1) == ')') {
				text.deleteCharAt(text.length() - 1);
			}

			WeightedTerm term = new WeightedTerm(text.toString(), weight);
			if (isPhrase) term.setQuoted(true);
			return term;
		}

		private Optional<Term> extractSingleTerm(BooleanClause clause) {
			if (!(clause instanceof DisjunctionMaxQuery)) {
				log.warn("Cannot handle boolean clause of type {}: {}", clause.getClass().getSimpleName(), clause.toString());
				return Optional.empty();
			}

			List<DisjunctionMaxClause> innerClauses = ((DisjunctionMaxQuery) clause).getClauses();
			if (innerClauses == null) return Optional.empty(); // should actually never happen

			if (innerClauses.size() == 1 && innerClauses.get(0) instanceof Term) {
				return Optional.of((Term) innerClauses.get(0));
			}
			else {
				return Optional.empty();
			}
		}

		private WeightedTerm toWeightedWord(Term term) {
			float weight = 1f;
			if (term instanceof BoostedTerm) {
				weight = ((BoostedTerm) term).getBoost();
			}
			return toWeightedWord(term.getValue().toString(), weight);
		}

		private WeightedTerm toWeightedWord(String term, float boost) {
			WeightedTerm weightedWord;
			if (boost != 0f && boost != 1f) {
				weightedWord = new WeightedTerm(term, boost);
			}
			else {
				weightedWord = new WeightedTerm(term);
			}

			// guard MUST and MUST_NOT terms from analyzer
			// if (!occur.equals(Occur.SHOULD)) {
			// weightedWord.setOccur(org.apache.lucene.search.BooleanClause.Occur.valueOf(occur.name()));
			// weightedWord.setQuoted(true);
			// }

			return weightedWord;
		}
	}

	static class FilterFetcher extends AbstractNodeVisitor<Node> {

		List<QueryStringTerm>									extractedWords	= new ArrayList<>();
		private org.apache.lucene.search.BooleanClause.Occur	occur;

		@Override
		public Node visit(DisjunctionMaxQuery disjunctionMaxQuery) {
			this.occur = org.apache.lucene.search.BooleanClause.Occur.valueOf(disjunctionMaxQuery.getOccur().name());
			return super.visit(disjunctionMaxQuery);
		}

		@Override
		public Node visit(Term term) {
			WeightedTerm weightedWord;
			if (term instanceof BoostedTerm) {
				weightedWord = new WeightedTerm(term.getValue().toString(), ((BoostedTerm) term).getBoost());
			}
			else {
				weightedWord = new WeightedTerm(term.toString());
			}
			weightedWord.setOccur(occur);
			weightedWord.setQuoted(true);
			extractedWords.add(weightedWord);
			return null;
		}

		@Override
		public Node visit(RawQuery rawQuery) {
			String rawQueryString = ((StringRawQuery) rawQuery).getQueryString();

			if (rawQueryString.indexOf(':') > 0) {
				// multiple colons => multiple filters

				Matcher filterMatcher = Pattern.compile("-?\\w+(\\.id)?\\:[^:]+(\\s|$)").matcher(rawQueryString);
				while (filterMatcher.find()) {
					QueryFilterTerm queryFilterTerm = getQueryFilterTerm(filterMatcher.group().trim());
					extractedWords.add(queryFilterTerm);
				}
				rawQueryString = filterMatcher.reset().replaceAll("");
			}
			
			if (!rawQueryString.isBlank()){
				extractedWords.add(new RawTerm(rawQueryString.trim()));
			}
			return super.visit(rawQuery);
		}

		private QueryFilterTerm getQueryFilterTerm(String rawQueryString) {

			String[] rawQuerySplit = rawQueryString.split(":");
			// TODO: Make it possible to use " AND " as a conjunction for
			// these values
			String fieldName = rawQuerySplit[0];
			String value = rawQuerySplit[1];

			org.apache.lucene.search.BooleanClause.Occur occur;
			if (fieldName.startsWith(Occur.MUST_NOT.toString())) {
				occur = org.apache.lucene.search.BooleanClause.Occur.MUST_NOT;
				fieldName = fieldName.substring(1);
			}
			else {
				occur = org.apache.lucene.search.BooleanClause.Occur.MUST;
			}

			// quotes are part of the valid Lucene syntax, but since we use the filter values as a whole anyways for a
			// term query, we trim them here
			if (value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
				value = value.substring(1, value.length() - 1);
			}

			QueryFilterTerm queryFilterTerm = new QueryFilterTerm(fieldName, value);
			queryFilterTerm.setOccur(occur);

			return queryFilterTerm;
		}
	}
}
