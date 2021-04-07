package de.cxp.ocs.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * see
 * https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html#function-decay
 */
@Data
@NoArgsConstructor
public class ScoringConfiguration {

	private ScoreMode	scoreMode	= ScoreMode.AVG;
	private BoostMode	boostMode	= BoostMode.AVG;

	private List<ScoringFunction> scoreFunctions = new ArrayList<>();

	@Accessors(chain = true)
	@Data
	@NoArgsConstructor
	public static class ScoringFunction {

		private String field;

		private ScoreType type = ScoreType.FIELD_VALUE_FACTOR;

		private float weight = 1f;

		private Map<ScoreOption, String> options = new HashMap<>();
	}
}
