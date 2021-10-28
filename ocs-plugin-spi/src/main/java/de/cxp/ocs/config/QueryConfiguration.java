package de.cxp.ocs.config;

import java.util.HashMap;
import java.util.Map;

import de.cxp.ocs.spi.search.ESQueryFactory;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Configuration that describes how a single query is constructed and under
 * which conditions it is used.
 */
@Getter // write setters with java-doc!
@NoArgsConstructor
public class QueryConfiguration {

	private String name;

	private QueryCondition condition = new QueryCondition();

	private String strategy = "DefaultQueryFactory";

	private Map<String, Float> weightedFields = new HashMap<>();

	private Map<QueryBuildingSetting, String> settings = new HashMap<>();

	/**
	 * Describes the condition under that the particular query is constructed.
	 */
	@Getter // write setters with java-doc!
	public static class QueryCondition {

		private int		minTermCount	= 1;
		private int		maxTermCount	= Integer.MAX_VALUE;
		private String	matchingRegex	= null;

		/**
		 * Set minimum of terms for a query factory to be used.
		 * 
		 * @param minTermCount
		 *        min term count (value &gt; 0)
		 * @return the changed query condition
		 */
		public QueryCondition setMinTermCount(int minTermCount) {
			this.minTermCount = minTermCount;
			return this;
		}

		/**
		 * Set inclusive maximum of terms for a query factory to be used.
		 * 
		 * @param maxTermCount
		 *        max term count (value &gt; 0)
		 * @return the changed query condition
		 */
		public QueryCondition setMaxTermCount(int maxTermCount) {
			this.maxTermCount = maxTermCount;
			return this;
		}

		/**
		 * Set a regular expression that should match for the whole search
		 * query.
		 * 
		 * @param matchingRegex
		 *        regular expression
		 * @return the changed query condition
		 */
		public QueryCondition setMatchingRegex(String matchingRegex) {
			this.matchingRegex = matchingRegex;
			return this;
		}
	}

	/**
	 * Should be a unique name of that query (e.g. "artNrSearch",
	 * "relaxedLevel1" etc) - it will be used in the result to assign the record
	 * matches to their matching query. It can also be used to reference to
	 * other queries as "fallback query" for some query builders.
	 * 
	 * @param name
	 *        unique config name
	 * @return self
	 */
	public QueryConfiguration setName(String name) {
		this.name = name;
		return this;
	}

	/**
	 * Specify the search-term conditions to build the Elasticsearch query based
	 * on this configuration.
	 * 
	 * @param condition
	 *        set condition for that query config
	 * @return self
	 */
	public QueryConfiguration setCondition(QueryCondition condition) {
		this.condition = condition;
		return this;
	}

	/**
	 * <p>
	 * Simple or canonical class name of the {@link ESQueryFactory} that is used
	 * to build that query.
	 * The suffix 'Factory' is optional.
	 * </p>
	 * Defaults to 'DefaultQueryFactory'.
	 * These ones are available:
	 * <ul>
	 * <li>DefaultQueryFactory</li>
	 * <li>ConfigurableQueryFactory</li>
	 * <li>NgramQueryFactory</li>
	 * <li>PredictionQueryFactory</li>
	 * </ul>
	 * 
	 * @param strategy
	 *        strategy name
	 * @return self
	 */
	public QueryConfiguration setStrategy(String strategy) {
		this.strategy = strategy;
		return this;
	}

	/**
	 * Defines the fields to be searched and their according weight.
	 * <p>
	 * The field name may contain a wildcard at the end to match all fields with
	 * a certain prefix. Keep in mind, that this will also match all subfields
	 * with different analyzers.
	 * For example 'title*' will search in 'title', 'title.standard',
	 * 'title.shingle', and 'title.ngram' with the same weight.
	 * </p>
	 * 
	 * @param weightedFields
	 *        field names with weight &gt; 0
	 * @return self
	 */
	public QueryConfiguration setWeightedFields(Map<String, Float> weightedFields) {
		this.weightedFields = weightedFields;
		return this;
	}

	/**
	 * Sets the {@link QueryBuildingSetting}s for that query.
	 * Check the according QueryFactory to see which settings it supports.
	 * 
	 * @param settings
	 *        strategy specific settings
	 * @return self
	 */
	public QueryConfiguration setSettings(Map<QueryBuildingSetting, String> settings) {
		this.settings = settings;
		return this;
	}
}
