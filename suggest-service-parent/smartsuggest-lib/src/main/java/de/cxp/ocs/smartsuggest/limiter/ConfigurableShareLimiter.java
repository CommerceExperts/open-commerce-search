package de.cxp.ocs.smartsuggest.limiter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import de.cxp.ocs.smartsuggest.querysuggester.Suggestion;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Limiter where you can configure, which group of suggestions should get which
 * share in the result (e.g. keywords=0.5 (50%), brand=0.3 (30%), category=0.2
 * (20%)).
 * </p>
 * <p>
 * That configuration can be done by the Map given in the constructor or
 * by defining system property or environment variables with the group values
 * prefixed by "SUGGEST_GROUP_SHARE_" keyword, e.g. the group "brand" could be
 * configured with environment variable "SUGGEST_GROUP_SHARE_BRAND=0.2".
 * (It's also possible to combine these configuration possibilities, but why
 * would you do that..).
 * </p>
 * <p>
 * The configured values should sum up to 1.0. If that's not the case, they are
 * normalized accordingly.
 * </p>
 * <p>
 * The order of the returned groups is defined by the order in the
 * configuration. This is why it must be a LinkedHashMap. Groups that are not
 * configured or configured by environment variables, will be appended to that
 * configuration according to their appearance in the results.
 * </p>
 * <p>
 * If one or more group are not configured at all, they either get the same
 * weight as the lowest configured group, or - in case the configured shares sum
 * up to a value lower 1, the remaining share will evenly be distributed over
 * the not-configured groups.
 * </p>
 * <p>
 * To group the results properly, a "groupKey" has to be specified. It is then
 * looked up at the pay-load of the suggestions. If a suggestion has no value
 * for that key, "other" is used. This means you should also consider
 * configuring the value of the "other" group.
 * </p>
 * <p>
 * If a result can't be distributed evenly, the remaining space will be filled
 * up with suggestions according to the group priority order - or the value of
 * the least important group will be truncated.
 * </p>
 */
@Slf4j
public class ConfigurableShareLimiter implements Limiter {

	public final static String	SHARE_KEY_ENV_PREFIX	= "SUGGEST_GROUP_SHARE_";
	public final static String	OTHER_SHARE_KEY			= "other";

	private final String				groupingKey;
	private final Optional<String[]>	groupDeduplicationOrder;

	private final LinkedHashMap<String, Double> origShareConf = new LinkedHashMap<>();
	private final LinkedHashMap<String, Double> normalizedShareConf = new LinkedHashMap<>();

	/**
	 * The share limiter will group the results according to a particular
	 * payload value and uses the configured share values to distribute the
	 * limited space among those grouped suggestions.
	 *
	 * @see ConfigurableShareLimiter
	 * @param groupingKey
	 *        which key to use to get the grouping key from the suggestions
	 *        payload.
	 * @param shareConfiguration
	 *        the share value (between 0 and 1) for each available group.
	 *        The order of the groups matters. See java-doc of
	 *        ConfigurableShareLimiter
	 * @param groupDeduplicationOrder
	 *        If given (even with an empty array), the suggestions will be
	 *        deduplicated. The array defines preferred groups. Suggestions of
	 *        the groups defined first will be preferred over suggestions from
	 *        other groups.
	 */
	public ConfigurableShareLimiter(@NonNull String groupingKey, LinkedHashMap<String, Double> shareConfiguration, Optional<String[]> groupDeduplicationOrder) {
		this.groupingKey = groupingKey;
		this.groupDeduplicationOrder = groupDeduplicationOrder;

		if (shareConfiguration != null && !shareConfiguration.isEmpty()) {
			origShareConf.putAll(shareConfiguration);
			this.normalizedShareConf.putAll(shareConfiguration);
			normalizeShareValues(this.normalizedShareConf);
		}
	}

	@Override
	public List<Suggestion> limit(List<Suggestion> suggestions, int limit) {
		if (suggestions.size() <= limit) {
			return suggestions;
		}

		Map<String, List<Suggestion>> groupedSuggestions = suggestions.stream().collect(Collectors.groupingBy(this::groupKey));
		groupDeduplicationOrder.ifPresent(order -> SuggestDeduplicator.deduplicate(groupedSuggestions, order));

		List<Suggestion> limitedSuggestions;
		if (groupedSuggestions.size() == 1) {
			limitedSuggestions = suggestions.subList(0, limit);
		}
		else {
			limitedSuggestions = new ArrayList<>();
			if (!normalizedShareConf.keySet().containsAll(groupedSuggestions.keySet())) {
				updateShareConfiguration(groupedSuggestions.keySet());
			}

			HashMap<String, Double> resultShares = new HashMap<>(groupedSuggestions.size());
			groupedSuggestions.keySet().forEach(g -> resultShares.put(g, normalizedShareConf.get(g)));
			normalizeShareValues(resultShares);
			

			LinkedList<Suggestion> remainingSuggestions = new LinkedList<>();
			Map<String, int[]> groupInsertIndexes = new HashMap<>(groupedSuggestions.size());
			
			for (String group : normalizedShareConf.keySet()) {
				List<Suggestion> groupSuggestion = groupedSuggestions.get(group);
				if (groupSuggestion != null) {
					int groupLimit = Math.min((int)Math.round(resultShares.get(group) * limit), groupSuggestion.size());
					limitedSuggestions.addAll(groupSuggestion.subList(0, groupLimit));
					
					if (groupLimit < groupSuggestion.size()) {
						remainingSuggestions.addAll(groupSuggestion.subList(groupLimit, groupSuggestion.size()));
						groupInsertIndexes.put(group, new int[] { groupLimit });
					}
				}
			}

			// we're using "Math.round" to get the fairest possible
			// distribution. this may lead to a bigger OR smaller result list.
			// here the final list is adjusted according to priority and
			// availability
			if (limitedSuggestions.size() > limit) {
				limitedSuggestions = limitedSuggestions.subList(0, limit);
			}
			else if (limitedSuggestions.size() < limit) {
				while (limitedSuggestions.size() < limit) {
					Suggestion next = remainingSuggestions.remove(0);
					limitedSuggestions.add(groupInsertIndexes.get(groupKey(next))[0]++, next);
				}
			}
		}

		return limitedSuggestions;
	}

	private void normalizeShareValues(Map<String, Double> shares) {
		if (shares.isEmpty()) {
			return;
		}

		double valuesSum = shares.values().stream().mapToDouble(d -> d).sum();
		if (valuesSum <= 0) {
			if (valuesSum < 0) {
				log.warn("shares configuration has invalid values. Will distribute equaly.");
			}
			valuesSum = shares.size();
		}
		if (valuesSum != 1.0) {
			double recalcFactor = 1.0 / valuesSum;
			for (Entry<String, Double> share : shares.entrySet()) {
				share.setValue(share.getValue() * recalcFactor);
			}
		}
	}

	private synchronized void updateShareConfiguration(Set<String> keys) {
		Set<String> unknownShares = new HashSet<>(keys.size());
		for (String key : keys) {
			if (!origShareConf.containsKey(key)) {
				// try to load this value from environment variable
				String shareEnvVar = System.getenv(SHARE_KEY_ENV_PREFIX + key.toUpperCase());
				if (shareEnvVar == null) {
					shareEnvVar = System.getProperty(SHARE_KEY_ENV_PREFIX + key.toUpperCase());
				}
				double keyShare = 0;
				if (shareEnvVar != null) {
					try {
						keyShare = Double.parseDouble(shareEnvVar);
					}
					catch (Exception e) {
						// ignore
					}
				}

				// if loaded successfuly from env var, then save it to the
				// origShareConf
				if (keyShare > 0) {
					origShareConf.put(key, keyShare);
				}
				else {
					unknownShares.add(key);
				}
			}
		}

		HashMap<String, Double> nextShareConf = new HashMap<>();
		nextShareConf.putAll(origShareConf);

		// if we still have unknown keys, either the remaining share is split
		// onto them or the minimum share is applied to them
		if (!unknownShares.isEmpty()) {
			double min = 1;
			double sum = 0;
			for (Double share : origShareConf.values()) {
				sum += share;
				if (share < min) {
					min = share;
				}
			}
			double sharePerKey;
			if (sum < 1) {
				sharePerKey = (1.0 - sum) / unknownShares.size();
			}
			else {
				sharePerKey = min;
			}
			for (String key : unknownShares) {
				// don't store them into origShareConf, because if another key
				// is added with a defined value, the share of the unknown might
				// change
				nextShareConf.put(key, sharePerKey);
			}
		}

		normalizeShareValues(nextShareConf);
		normalizedShareConf.putAll(nextShareConf);
	}

	private String groupKey(Suggestion suggestion) {
		return suggestion.getPayload() == null
				? OTHER_SHARE_KEY
				: suggestion.getPayload().getOrDefault(groupingKey, OTHER_SHARE_KEY);
	}

}
