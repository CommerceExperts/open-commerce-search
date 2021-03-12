package de.cxp.ocs.config;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * config that describes how a single query is constructed
 */
@Data
@NoArgsConstructor
public class QueryConfiguration {

	private final QueryCondition condition = new QueryCondition();

	// TODO: allow custom strategies => type should become String
	private QueryStrategy strategy = QueryStrategy.DefaultQuery;

	private final Map<String, Float> weightedFields = new HashMap<>();

	private Map<QueryBuildingSetting, String> settings = new HashMap<>();

	@Data
	public static class QueryCondition {

		private int		minTermCount	= 1;
		private int		maxTermCount	= Integer.MAX_VALUE;
		private String	matchingRegex	= null;
	}
}
