package io.searchhub.smartsuggest.querysuggester.lucene;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.time.StopWatch;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Data
public class PerfResult {

	private String			inputQuery;
	private int				resultCount;
	private StopWatch		totalTime;
	private List<StepTime>	steps	= new ArrayList<>();

	@ToString.Exclude
	private long _lastSplit = 0;

	public PerfResult(String inputQuery) {
		this.inputQuery = inputQuery;
		totalTime = new StopWatch();
		totalTime.start();
	}

	@ToString
	@Getter
	@AllArgsConstructor
	public static class StepTime {

		String	step;
		long	timeMs;
		int		results;
	}

	public void addStep(String step, int results) {
		// get split time since last split
		long splitTime = totalTime.getTime();
		steps.add(new StepTime(step, splitTime - _lastSplit, results));
		_lastSplit = splitTime;
	}

	public void stop() {
		totalTime.stop();
	}
}
