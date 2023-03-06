package de.cxp.ocs.elasticsearch.query.analyzer;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import de.cxp.ocs.elasticsearch.query.model.*;
import de.cxp.ocs.spi.search.ConfigurableExtension;
import de.cxp.ocs.spi.search.UserQueryAnalyzer;
import de.cxp.ocs.util.ESQueryUtils;
import lombok.extern.slf4j.Slf4j;
import querqy.model.*;
import querqy.model.Clause.Occur;
import querqy.parser.QuerqyParser;
import querqy.parser.WhiteSpaceQuerqyParser;
import querqy.rewrite.RewriteChain;
import querqy.rewrite.RewriterFactory;
import querqy.rewrite.commonrules.SimpleCommonRulesRewriterFactory;
import querqy.rewrite.commonrules.WhiteSpaceQuerqyParserFactory;
import querqy.rewrite.commonrules.select.ExpressionCriteriaSelectionStrategyFactory;
import querqy.rewrite.experimental.LocalSearchEngineRequestAdapter;

@Slf4j
public class QuerqyQueryExpander implements UserQueryAnalyzer, ConfigurableExtension {

	private final QuerqyParser	parser					= new WhiteSpaceQuerqyParser();
	private RewriteChain		rewriteChain			= null;
	private boolean				loggedMissingRewriter	= false;

	@Override
	public void initialize(Map<String, String> settings) {
		String commonRulesLocation = settings == null ? null : settings.get("common_rules_url");
		try {
			if (commonRulesLocation == null) {
				log.error("no 'common_rules_url' provided! Won't enrich queries with querqy.");
			}
			else if (commonRulesLocation.startsWith("http")) {
				URL url = new URL(commonRulesLocation);
				BufferedInputStream resourceStream = new BufferedInputStream(url.openStream());
				rewriteChain = initFromStream(resourceStream);
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
					rewriteChain = initFromStream(resourceStream);
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

	private RewriteChain initFromStream(InputStream resourceStream) throws IOException {
		List<RewriterFactory> factories;
		factories = Collections.singletonList(
				new SimpleCommonRulesRewriterFactory(
						"common_rules",
						new InputStreamReader(resourceStream),
						true,
						new WhiteSpaceQuerqyParserFactory(),
						true,
						Collections.emptyMap(),
						new ExpressionCriteriaSelectionStrategyFactory(), false));
		return new RewriteChain(factories);
	}

	@Override
	public List<QueryStringTerm> analyze(String userQuery) {
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
		return getQueryTerms(expandedQuery);
	}

	private static List<QueryStringTerm> getQueryTerms(ExpandedQuery expandedQuery) {
		List<QueryStringTerm> terms = new ArrayList<>();

		if (expandedQuery.getUserQuery() instanceof querqy.model.Query) {
			TermFetcher termFetcher = new TermFetcher();
			querqy.model.Query userQuery = (querqy.model.Query) expandedQuery.getUserQuery();
			for (BooleanClause clause : userQuery.getClauses()) {
				clause.accept(termFetcher);
				termFetcher.reset();
			}
			terms.addAll(termFetcher.getExtractedWords());
		}
		else if (expandedQuery.getUserQuery() != null) {
			log.error("not expected userquery of type" + expandedQuery.getUserQuery().getClass().getCanonicalName());
		}

		if (expandedQuery.getFilterQueries() != null) {
			Collection<QuerqyQuery<?>> filterQueries = expandedQuery.getFilterQueries();
			FilterFetcher filterFetcher = new FilterFetcher();
			for (QuerqyQuery<?> qq : filterQueries) {
				qq.accept(filterFetcher);
				terms.addAll(filterFetcher.extractedWords);
				filterFetcher.extractedWords.clear();
			}
		}

		return terms;
	}

	/**
	 * a concept is one or more words/terms that describe a single thing.
	 * In languages that don't have compound words, several words are used sometimes to name a thing,
	 * e.g. "flat iron" = "bügeleisen" = "fer à repasser".
	 * 
	 * This builder is made to group those terms into a single "ConceptTerm".
	 * (Class does not exist yet)
	 */
	static class ConceptTermBuilder implements QueryStringTerm {

		private List<QueryStringTerm> inputTerms = new ArrayList<>();

		private Set<WeightedWord> associations = new LinkedHashSet<>();

		void addInputTerm(QueryStringTerm term) {
			inputTerms.add(term);
		}

		void addAssociation(WeightedWord term) {
			associations.add(term);
		}

		// List<QueryStringTerm> build() {
		// List<QueryStringTerm> allTerms = new ArrayList<>(associations.size() + 1);
		//
		// for (QueryStringTerm term : inputTerms) {
		// allTerms.add(extractInputTerm(term));
		// }
		//
		// allTerms.addAll(associations);
		// return allTerms;
		// }
		//

		List<QueryStringTerm> build() {
			if (associations.size() > 0) {
				if (inputTerms.size() == 1) {
					return Collections.singletonList(new WordAssociation(inputTerms.get(0).getWord(), inputTerms.get(0).getOccur(), associations));
				}
				else {
					List<QueryStringTerm> allTerms = new ArrayList<>(associations.size() + 1);
					List<QueryStringTerm> inputQueryTerms = inputTerms.stream().map(input -> extractTermInput(input)).collect(Collectors.toList());
					allTerms.add(new MultiTermsTerm(inputQueryTerms));
					allTerms.addAll(associations);
					return Collections.singletonList(new AlternativeTerm(allTerms));
				}
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
		//
		// private QueryStringTerm extractMultiTermInput() {
		// List<WeightedWord> alternativeWords = new ArrayList<>(inputTerms.size() - 1);
		// List<QueryStringTerm> alternativeTerms = new ArrayList<>(inputTerms.size() - 1);
		//
		// Iterator<QueryStringTerm> inputTermIterator = inputTerms.iterator();
		// QueryStringTerm first = extractTermInput(inputTermIterator.next());
		// while (inputTermIterator.hasNext()) {
		// QueryStringTerm alternative = extractTermInput(inputTermIterator.next());
		//
		// if (alternative instanceof WeightedWord) {
		// alternativeWords.add((WeightedWord) alternative);
		// }
		// alternativeTerms.add(alternative);
		// }
		//
		// // unfortunately Java-Generics don't work here to collect a single list and decide its usage afterwards
		// // if all words are "simple words" ( = WeightedWord), then create WordAssociation
		// if (alternativeWords.size() == alternativeTerms.size()) {
		// return new WordAssociation(first.getWord(), first.getOccur(), alternativeWords);
		// }
		// else {
		// alternativeTerms.add(0, first);
		// return new AlternativeTerm(alternativeTerms);
		// }
		// }

		private static QueryStringTerm extractTermInput(QueryStringTerm term) {
			if (term instanceof ConceptTermBuilder) {
				List<QueryStringTerm> buildQuery = ((ConceptTermBuilder) term).build();
				return buildQuery.size() == 1 ? buildQuery.get(0) : term;
			}
			return term;
		}

		@Override
		public String toQueryString() {
			List<QueryStringTerm> build = build();
			return build.size() == 1 ? build.get(0).toQueryString() : ESQueryUtils.buildQueryString(build, " ");
		}

		@Override
		public String getWord() {
			if (inputTerms.size() == 1) {
				return inputTerms.get(0).getWord();
			}
			else {
				return inputTerms.stream().map(QueryStringTerm::getWord).collect(Collectors.joining(" "));
			}
		}

		@Override
		public org.apache.lucene.search.BooleanClause.Occur getOccur() {
			var SHOULD = org.apache.lucene.search.BooleanClause.Occur.SHOULD;
			return inputTerms.stream().map(QueryStringTerm::getOccur).reduce((o1, o2) -> o1.equals(o2) ? o1 : SHOULD).orElse(SHOULD);
		}
	}

	static class TermFetcher extends AbstractNodeVisitor<Node> {

		private QueryStringTerm initialTerm;
		private boolean			isInputTermInConcept	= false;

		Map<String, ConceptTermBuilder> queryConcepts = new LinkedHashMap<>();

		private Occur occur;

		public void reset() {
			if (isInputTermInConcept) {
				queryConcepts.remove(initialTerm.getWord());
				isInputTermInConcept = false;
			}
			initialTerm = null;
		}

		public List<QueryStringTerm> getExtractedWords() {
			List<QueryStringTerm> extracted = new ArrayList<>();
			queryConcepts.values().forEach(concept -> extracted.addAll(concept.build()));
			return extracted;
		}

		@Override
		public Node visit(Term term) {
			WeightedWord weightedWord;
			if (term instanceof BoostedTerm) {
				weightedWord = new WeightedWord(term.getValue().toString(), ((BoostedTerm) term).getBoost());
			}
			else {
				weightedWord = new WeightedWord(term.toString());
			}
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
				ConceptTermBuilder conceptBuilder = queryConcepts.computeIfAbsent(weightedWord.getWord(), w -> new ConceptTermBuilder());
				conceptBuilder.addInputTerm(weightedWord);
			}
			else {
				ConceptTermBuilder conceptBuilder = queryConcepts.get(initialTerm.getWord());
				conceptBuilder.addAssociation(weightedWord);
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

				float weight = 1f;
				StringBuilder text = new StringBuilder();
				boolean isPhrase = false;
				for (BooleanClause clause : booleanQuery.getClauses()) {
					Optional<WeightedWord> weightedWord = extractSingleTerm(clause).map(this::toWeightedWord);
					if (weightedWord.isPresent()) {
						if (text.length() > 0) {
							text.append(' ');
							isPhrase = true;
						}
						text.append(weightedWord.get().getWord());
						weight = weightedWord.get().getWeight();
					}
				}

				if (text.charAt(0) == '(') {
					text.deleteCharAt(0);
				}
				if (text.charAt(text.length() - 1) == ')') {
					text.deleteCharAt(text.length() - 1);
				}

				WeightedWord word = new WeightedWord(text.toString(), weight);
				if (isPhrase) word.setQuoted(true);

				// remember concept via the synonym phrase..
				ConceptTermBuilder conceptBuilder = queryConcepts.computeIfAbsent(text.toString(), (x) -> new ConceptTermBuilder());
				conceptBuilder.addAssociation(word);

				// ..and link it via the input term, so that term synonyms can be attached to
				if (initialTerm != null) {
					var inputConceptBuilder = queryConcepts.get(initialTerm.getWord());
					conceptBuilder.addInputTerm(inputConceptBuilder);
					isInputTermInConcept = true;
				}

				// end visitor calls
				return null;

			}
			else {
				// this is an outer boolean query - go on visiting the inner nodes
				return super.visit(booleanQuery);
			}
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

		private WeightedWord toWeightedWord(Term term) {
			float weight = 1f;
			if (term instanceof BoostedTerm) {
				weight = ((BoostedTerm) term).getBoost();
			}
			return toWeightedWord(term.getValue().toString(), weight);
		}

		private WeightedWord toWeightedWord(String term, float boost) {
			WeightedWord weightedWord;
			if (boost != 0f && boost != 1f) {
				weightedWord = new WeightedWord(term, boost);
			}
			else {
				weightedWord = new WeightedWord(term);
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

		List<QueryStringTerm> extractedWords = new ArrayList<>();
		private org.apache.lucene.search.BooleanClause.Occur	occur;

		@Override
		public Node visit(DisjunctionMaxQuery disjunctionMaxQuery) {
			this.occur = org.apache.lucene.search.BooleanClause.Occur.valueOf(disjunctionMaxQuery.getOccur().name());
			return super.visit(disjunctionMaxQuery);
		}

		@Override
		public Node visit(Term term) {
			WeightedWord weightedWord;
			if (term instanceof BoostedTerm) {
				weightedWord = new WeightedWord(term.getValue().toString(), ((BoostedTerm) term).getBoost());
			}
			else {
				weightedWord = new WeightedWord(term.toString());
			}
			weightedWord.setOccur(occur);
			extractedWords.add(weightedWord);
			return null;
		}

		@Override
		public Node visit(RawQuery rawQuery) {
			String rawQueryString = ((StringRawQuery) rawQuery).getQueryString();

			if (rawQueryString.indexOf(':') < rawQueryString.lastIndexOf(':')) {
				// multiple colons => multiple filters

				Matcher filterMatcher = Pattern.compile("-?\\w+\\:[^:]+(\\s|$)").matcher(rawQueryString);
				while (filterMatcher.find()) {
					QueryFilterTerm queryFilterTerm = getQueryFilterTerm(filterMatcher.group().trim());
					extractedWords.add(queryFilterTerm);
				}
			}
			else if (rawQueryString.indexOf(':') > 0) {
				QueryFilterTerm queryFilterTerm = getQueryFilterTerm(rawQueryString);
				extractedWords.add(queryFilterTerm);
			}
			else {
				extractedWords.add(new RawQueryString(rawQueryString));
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
