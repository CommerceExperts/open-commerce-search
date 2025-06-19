package de.cxp.ocs.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class JsonMapper {
    private final ObjectMapper objectMapper;

    /**
     * Converts JSON string to Map<String, Object>
     */
    public Map<String, Object> readJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (IOException e) {
            log.error("Failed to parse JSON: {}", json, e);
            throw new JsonMappingException("Failed to parse JSON to Map", e);
        }
    }

    /**
     * Converts Map<String, Object> to JSON string
     */
    public String writeJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("Failed to write Map to JSON: {}", map, e);
            throw new JsonMappingException("Failed to write Map to JSON", e);
        }
    }

    /**
     * Converts JSON string to specified type
     */
    public <T> T readJson(String json, Class<T> valueType) {
        try {
            return objectMapper.readValue(json, valueType);
        } catch (IOException e) {
            log.error("Failed to parse JSON to {}: {}", valueType.getSimpleName(), json, e);
            throw new JsonMappingException("Failed to parse JSON to " + valueType.getSimpleName(), e);
        }
    }

    /**
     * Converts JSON string to specified type using TypeReference
     */
    public <T> T readJson(String json, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (IOException e) {
            log.error("Failed to parse JSON with TypeReference: {}", json, e);
            throw new JsonMappingException("Failed to parse JSON with TypeReference", e);
        }
    }

    /**
     * Converts object to JSON string
     */
    public String writeJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("Failed to write object to JSON: {}", object, e);
            throw new JsonMappingException("Failed to write object to JSON", e);
        }
    }

    /**
     * Converts value using ObjectMapper.convertValue (for Map to POJO conversion)
     * Example: objectMapper.convertValue(rawConfig, ApplicationSearchProperties.class);
     */
    public <T> T convertValue(Object fromValue, Class<T> toValueType) {
        try {
            return objectMapper.convertValue(fromValue, toValueType);
        } catch (IllegalArgumentException e) {
            log.error("Failed to convert value to {}: {}", toValueType.getSimpleName(), fromValue, e);
            throw new JsonMappingException("Failed to convert value to " + toValueType.getSimpleName(), e);
        }
    }

    /**
     * Converts value using ObjectMapper.convertValue with TypeReference
     */
    public <T> T convertValue(Object fromValue, TypeReference<T> toValueTypeRef) {
        try {
            return objectMapper.convertValue(fromValue, toValueTypeRef);
        } catch (IllegalArgumentException e) {
            log.error("Failed to convert value with TypeReference: {}", fromValue, e);
            throw new JsonMappingException("Failed to convert value with TypeReference", e);
        }
    }

    /**
     * Checks if a string is valid JSON
     */
    public boolean isValidJson(String json) {
        try {
            objectMapper.readTree(json);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    /**
     * Pretty prints JSON string
     */
    public String prettyPrint(String json) {
        try {
            Object jsonObject = objectMapper.readValue(json, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
        } catch (JsonProcessingException e) {
            log.error("Failed to pretty print JSON: {}", json, e);
            throw new JsonMappingException("Failed to pretty print JSON", e);
        }
    }

    /**
     * Pretty prints object as JSON string
     */
    public String prettyPrint(Object object) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("Failed to pretty print object: {}", object, e);
            throw new JsonMappingException("Failed to pretty print object", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> normalizeKeys(Map<String, Object> input) {
        Map<String, Object> normalized = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = toLowerCamelCase(entry.getKey());
            Object value = entry.getValue();

            if (value instanceof Map<?, ?>) {
                Map<String, Object> nestedMap = normalizeKeys((Map<String, Object>) value);
                value = tryConvertIndexedMapToList(nestedMap);
            } else if (value instanceof List<?>) {
                value = ((List<?>) value).stream()
                        .map(item -> {
                            if (item instanceof Map<?, ?>) {
                                return normalizeKeys((Map<String, Object>) item);
                            }
                            return item;
                        })
                        .toList();
            }

            normalized.put(key, value);
        }

        return normalized;
    }

    private Object tryConvertIndexedMapToList(Map<String, Object> map) {
        if (map.keySet().stream().allMatch(k -> k.matches("\\d+"))) {
            return map.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(Integer::parseInt)))
                    .map(Map.Entry::getValue)
                    .toList();
        }
        return map;
    }

    private String toLowerCamelCase(String kebab) {
        if (!kebab.contains("-")) return kebab;
        String[] parts = kebab.split("-");
        StringBuilder camel = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            camel.append(Character.toUpperCase(parts[i].charAt(0)))
                    .append(parts[i].substring(1));
        }
        return camel.toString();
    }

    /**
     * Custom exception for JSON mapping errors
     */
    public static class JsonMappingException extends RuntimeException {
        public JsonMappingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}