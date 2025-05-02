package de.cxp.ocs;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.rapidoid.http.Req;
import org.rapidoid.http.ReqHandler;

public class InfoReqHandler implements ReqHandler {

	private static final long serialVersionUID = 1L;

	private final Map<String, Object> info = new HashMap<>();

	public InfoReqHandler(String... propertyResources) {
		for (String propertyResource : propertyResources) {
			load(propertyResource);
		}
	}

	@Override
	public Object execute(Req req) {
		return info;
	}

	public void load(String propertyResource) {
		try {
			InputStream gitPropertiesStream = InfoReqHandler.class.getClassLoader().getResourceAsStream(propertyResource);
			if (gitPropertiesStream != null) {
				Properties gitProps = new Properties();
				gitProps.load(gitPropertiesStream);
				gitProps.forEach(this::addProperty);
			}
			else {
				addProperty(propertyResource + ".error", "not found");
			}
		}
		catch (Exception e) {
			addProperty(propertyResource + ".error", e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	private void addProperty(Object name, Object value) {
		if (name == null) return;
		if (value == null) return;
		addProperty(name.toString(), value.toString());
	}

	public InfoReqHandler addProperty(String name, String value) {
		addRecursive(info, name.toString().split("\\."), 0, value);
		return this;
	}

	@SuppressWarnings("unchecked")
	private void addRecursive(Map<String, Object> map, String[] namePath, int i, Object value) {
		if (i + 1 == namePath.length) {
			Object previousValue = map.put(namePath[i], value);
			if (previousValue != null && previousValue instanceof Map) {
				((Map<String, Object>) previousValue).put("_value", value);
				map.put(namePath[i], previousValue);
			}
		}
		else {
			Object nestedMap = map.computeIfAbsent(namePath[i], k -> new HashMap<String, Object>());
			if (!(nestedMap instanceof Map)) {
				Map<String, Object> _nestedMap = new HashMap<>();
				_nestedMap.put("_value", nestedMap);
				map.put(namePath[i], _nestedMap);
				nestedMap = _nestedMap;
			}

			addRecursive((Map<String, Object>) nestedMap, namePath, i + 1, value);
		}
	}

}
