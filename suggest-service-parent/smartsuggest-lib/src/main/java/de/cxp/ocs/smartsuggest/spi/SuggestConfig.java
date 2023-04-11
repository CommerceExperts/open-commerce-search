package de.cxp.ocs.smartsuggest.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
public class SuggestConfig {

	public Locale locale = Locale.ROOT;

	public boolean alwaysDoFuzzy = Boolean.getBoolean("alwaysDoFuzzy");

	private SortStrategy sortStrategy = SortStrategy.PrimaryAndSecondaryByWeight;

	public boolean useDataSourceMerger = false;

	public String groupKey = null;

	public Optional<String[]> groupDeduplicationOrder = Optional.empty();

	public boolean useRelativeShareLimit = false;

	public int prefetchLimitFactor = 1;

	public int maxSharpenedQueries = 3;

	public List<GroupConfig> groupConfig = new ArrayList<>();

	/**
	 * If suggestions are grouped by a certain value of their payload (e.g.
	 * 'type'), this config is used to define the order and limit of each group.
	 */
	@Data
	@AllArgsConstructor
	public static class GroupConfig {

		public String	groupName;
		public int		limit;

	}

	public static enum SortStrategy {
		/**
		 * Suggestions are ordered by their match-group (sharpened, primary,
		 * secondary, fuzzy1, fuzzy2, etc). Within each group, matches are
		 * ordered according to "best match" (a combination of match-position
		 * and weight).
		 */
		MatchGroupsSeparated,

		/**
		 * Similar to MatchGroupsSeparated, but "primary" and "secondary" group
		 * are considered equal and merged. Within these first match groups,
		 * suggestions are only ordered by weight.
		 */
		PrimaryAndSecondaryByWeight
	}

	public SuggestConfig() {
		// legacy: it was possible to set this setting using a system property
		if (System.getProperty("doReorderSecondaryMatches") != null && !Boolean.getBoolean("doReorderSecondaryMatches")) {
			this.setSortStrategy(SortStrategy.MatchGroupsSeparated);
		}
	}

	/**
	 * Set locale for string transformation and sorting.
	 * 
	 * @param locale
	 *        locale
	 */
	public void setLocale(Locale locale) {
		this.locale = locale;
	}

	/**
	 * By default fuzzy searches are only done, if there are no exact matches
	 * within primary or secondary text. If this flag is set, fuzzy search is
	 * also done if result size is below limit.
	 * 
	 * @param alwaysDoFuzzy
	 *        true to activate
	 */
	public void setAlwaysDoFuzzy(boolean alwaysDoFuzzy) {
		this.alwaysDoFuzzy = alwaysDoFuzzy;
	}

	/**
	 * Defines how matching suggest terms are ordered in the result.
	 * 
	 * @param sortStrategy
	 *        sortStrategy
	 */
	public void setSortOrder(SortStrategy sortStrategy) {
		this.sortStrategy = sortStrategy;
	}

	/**
	 * <p>
	 * In case several data-sources are used, each data-source is requested for
	 * the same amount of suggestions.
	 * Per default all those suggestions are simply appended and truncated to
	 * get the final list of suggestions.
	 * </p>
	 * <p>
	 * With this setting it is possible to specify a key that is available in
	 * the payload of all provided suggestions. The final result list will then
	 * be grouped by this payload-value and truncated according to the provided
	 * group configs.
	 * </p>
	 * 
	 * @param groupKey
	 *        groupKey
	 */
	public void setGroupKey(String groupKey) {
		this.groupKey = groupKey;
	}

	/**
	 * <p>
	 * Add a group config with the specified name and limit to the end of the
	 * ordered list.
	 * </p>
	 * <p>
	 * Use the groupName CommonPayloadFields.PAYLOAD_TYPE_OTHER =
	 * 'other' to specify a default limit value.
	 * </p>
	 * 
	 * @param groupName
	 *        groupName
	 * @param limit
	 *        limit
	 */
	public void addGroupConfig(String groupName, int limit) {
		groupConfig.add(new GroupConfig(groupName, limit));
	}

	/**
	 * <p>
	 * Defines in which order similar suggestions from different "groups" are
	 * preferred. Names that appear first are preferred over names appearing
	 * later.
	 * </p>
	 * <p>
	 * This setting is 'null' per default, which means no
	 * deduplication is done at all. If an empty String[] is set, deduplication
	 * is done randomly.
	 * </p>
	 * <p>
	 * This only works, if the suggest service is configured with a grouping
	 * key.
	 * </p>
	 * 
	 * @param groupDeduplicationOrder
	 *        ordered array, suggest entries from groups at beginning are
	 *        preferred
	 */
	public void setGroupDeduplicationOrder(String[] groupDeduplicationOrder) {
		this.groupDeduplicationOrder = Optional.ofNullable(groupDeduplicationOrder);
	}

	/**
	 * Defines to use the limits of the group-configs as relative share values,
	 * e.g. 20 and 80 as 20% and 80%.
	 * <p>
	 * This only works, if the suggest service is configured with a grouping
	 * key.
	 * </p>
	 * 
	 * @param useRelativeShareLimit
	 *        set to true to activate relative share
	 */
	public void setUseRelativeShareLimit(boolean useRelativeShareLimit) {
		this.useRelativeShareLimit = useRelativeShareLimit;
	}

	/**
	 * Defines in which order the suggestion groups should be returned and how
	 * they should be limited.
	 * <ul>
	 * <li>If 'useRelativeShareLimit' is 'false', these
	 * limits are considered absolute.</li>
	 * <li>If 'useRelativeShareLimit' is 'true', the limits are normalized into
	 * according relative values, e.g. 1, 2 and 2 becomes 20%, 40% and 40%</li>
	 * </ul>
	 * <p>
	 * This only works, if the suggest service is configured with a grouping
	 * key.
	 * </p>
	 * 
	 * @param groupConfig
	 *        full groupConfig
	 */
	public void setGroupConfig(List<GroupConfig> groupConfig) {
		this.groupConfig = groupConfig;
	}

	/**
	 * Defines the limit of returned sharpened queries.
	 * <p>
	 * Sharpened queries are queries that are injected directly (without requesting a Lucene index) from a hash-map if
	 * the input query matches one of the existing entries.
	 * </p>
	 * <p>
	 * This limit only is considered if there are more sharpened queries than defined by that limit.
	 * </p>
	 * 
	 * @param maxSharpenedQueries
	 */
	public void setMaxSharpenedQueries(int maxSharpenedQueries) {
		this.maxSharpenedQueries = maxSharpenedQueries;
	}
}
