package de.cxp.ocs.util;

import java.util.*;

import org.apache.commons.lang3.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class TraceOptions {

	public enum TraceFlag {
		Request, EsQuery
	}

	private static final Map<String, TraceFlag> lowercaseMapping = new HashMap<>(TraceFlag.values().length);
	static {
		for (TraceFlag f : TraceFlag.values()) {
			lowercaseMapping.put(f.name().toLowerCase(), f);
		}
	}

	public static final TraceOptions OFF = new TraceOptions(Collections.emptySet());

	private final Set<TraceFlag> traceFlags;

	public static TraceOptions parse(String paramValue) {
		if (paramValue == null || paramValue.isBlank()) return OFF;

		String[] split = StringUtils.split(paramValue, ',');
		Set<TraceFlag> traceFlags = EnumSet.noneOf(TraceFlag.class);
		for (String s : split) {
			TraceFlag traceFlag = lowercaseMapping.get(s.trim().toLowerCase());
			if (traceFlag != null) {
				traceFlags.add(traceFlag);
			}
			else {
				log.debug("ignoring unknown trace-flag {}", s);
			}
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
