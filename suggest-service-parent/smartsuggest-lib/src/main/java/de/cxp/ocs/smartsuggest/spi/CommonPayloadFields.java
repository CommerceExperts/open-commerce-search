package de.cxp.ocs.smartsuggest.spi;

import java.util.HashMap;
import java.util.Map;

/**
 * Common keys for the payload attached to {@link SuggestRecord}s.
 */
public final class CommonPayloadFields {

	public static final String	PAYLOAD_TYPE_KEY	= "type";
	public static final String	PAYLOAD_COUNT_KEY	= "count";

	private CommonPayloadFields() {}

	public static Map<String, String> payloadOfTypeAndCount(String type, String count) {
		Map<String, String> payload = new HashMap<>(2);
		payload.put(PAYLOAD_TYPE_KEY, type);
		payload.put(PAYLOAD_COUNT_KEY, count);
		return payload;
	}

}
