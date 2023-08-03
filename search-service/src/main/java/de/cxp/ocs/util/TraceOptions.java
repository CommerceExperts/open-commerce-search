package de.cxp.ocs.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TraceOptions {

	public enum TraceFlag {
		Request, EsQuery
	}

	public static final TraceOptions OFF = new TraceOptions(Collections.emptySet());
	
	private final Set<TraceFlag> traceFlags;
	
	public static TraceOptions parse(String paramValue) {
		String[] split = StringUtils.split(paramValue, ',');
		Set<TraceFlag> traceFlags = new HashSet<>(TraceFlag.values().length);
		for (String s : split) {
			TraceFlag flag = TraceFlag.valueOf(s);
			traceFlags.add(flag);
		}
		return new TraceOptions(traceFlags);
	}

	public boolean isSet(TraceFlag flag) {
		return traceFlags.contains(flag);
	}

	public void onFlag(TraceFlag flag, Runnable runFunction) {
		if (isSet(flag)) runFunction.run();
	}
}
