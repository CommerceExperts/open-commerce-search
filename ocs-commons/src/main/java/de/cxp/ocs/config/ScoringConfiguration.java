package de.cxp.ocs.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * see
 * https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html#function-decay
 */
@Data
@NoArgsConstructor
public class ScoringConfiguration {

	private ScoreMode	scoreMode	= ScoreMode.avg;
	private BoostMode	boostMode	= BoostMode.avg;

	private List<ScoringFunction> scoreFunctions = new ArrayList<>();

	@Accessors(chain = true)
	@Data
	@RequiredArgsConstructor
	@NoArgsConstructor
	public static class ScoringFunction {

		@NonNull
		private String field;

		private ScoreType type = ScoreType.field_value_factor;

		private float weight = 1f;

		private Map<ScoreOption, String> options = new HashMap<>();
	}
}
