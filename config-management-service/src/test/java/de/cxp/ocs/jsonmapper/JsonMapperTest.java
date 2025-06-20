package de.cxp.ocs.jsonmapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.cxp.ocs.configmanagement.util.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonMapperTest {

    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);
        mapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        jsonMapper = new JsonMapper(mapper);
    }

    @Test
    void testReadJsonToMap() {
        String json = "{\"key\":\"value\"}";
        Map<String, Object> result = jsonMapper.readJson(json);
        assertEquals("value", result.get("key"));
    }

    @Test
    void testWriteJsonFromMap() {
        Map<String, Object> map = Map.of("foo", "bar");
        String json = jsonMapper.writeJson(map);
        assertTrue(json.contains("\"foo\":\"bar\""));
    }

    @Test
    void testReadJsonToClass() {
        String json = "{\"name\":\"TestName\"}";
        Sample result = jsonMapper.readJson(json, Sample.class);
        assertEquals("TestName", result.name);
    }

    @Test
    void testReadJsonToTypeReference() {
        String json = "{\"x\":\"y\"}";
        Map<String, String> result = jsonMapper.readJson(json, new TypeReference<>() {
        });
        assertEquals("y", result.get("x"));
    }

    @Test
    void testWriteJsonFromObject() {
        Sample sample = new Sample("ExampleName");
        String json = jsonMapper.writeJson(sample);
        assertTrue(json.contains("\"name\":\"ExampleName\""));
    }

    @Test
    void testReadJsonInvalidThrows() {
        String invalidJson = "{unclosed";
        assertThrows(JsonMapper.JsonMappingException.class, () -> {
            jsonMapper.readJson(invalidJson);
        });
    }

    @Test
    void testWriteJsonInvalidThrows() {
        Object invalidObject = new Object() {
            // ObjectMapper can't serialize this anonymous object with circular reference
            private final Object self = this;
        };

        assertThrows(JsonMapper.JsonMappingException.class, () -> {
            jsonMapper.writeJson(invalidObject);
        });
    }

    static class Sample {
        public String name;

        public Sample() {
        }

        public Sample(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Sample)) return false;
            return this.name.equals(((Sample) obj).name);
        }
    }

    @Test
    void testNormalizeKeysSimple() {
        Map<String, Object> input = Map.of(
                "some-key", "value",
                "another-key", "test"
        );

        Map<String, Object> result = jsonMapper.normalizeKeys(input);
        assertEquals("value", result.get("someKey"));
        assertEquals("test", result.get("anotherKey"));
    }

    @Test
    void testNormalizeKeysNested() {
        Map<String, Object> nested = Map.of("inner-key", "val");
        Map<String, Object> input = Map.of("outer-key", nested);

        Map<String, Object> result = jsonMapper.normalizeKeys(input);
        assertTrue(result.containsKey("outerKey"));
        Object outerValue = result.get("outerKey");
        assertTrue(outerValue instanceof Map);
        assertEquals("val", ((Map<?, ?>) outerValue).get("innerKey"));
    }

    @Test
    void testNormalizeKeysListOfMaps() {
        Map<String, Object> map1 = Map.of("some-key", "a");
        Map<String, Object> map2 = Map.of("another-key", "b");
        Map<String, Object> input = Map.of("list-key", List.of(map1, map2));

        Map<String, Object> result = jsonMapper.normalizeKeys(input);
        List<?> normalizedList = (List<?>) result.get("listKey");

        assertEquals("a", ((Map<?, ?>) normalizedList.get(0)).get("someKey"));
        assertEquals("b", ((Map<?, ?>) normalizedList.get(1)).get("anotherKey"));
    }

    @Test
    void testNormalizeKeysIndexedMapConvertedToList() {
        Map<String, Object> indexedMap = new LinkedHashMap<>();
        indexedMap.put("0", "first");
        indexedMap.put("1", "second");

        Map<String, Object> input = Map.of("array-map", indexedMap);
        Map<String, Object> result = jsonMapper.normalizeKeys(input);

        Object value = result.get("arrayMap");
        assertTrue(value instanceof List<?>);
        List<?> list = (List<?>) value;
        assertEquals("first", list.get(0));
        assertEquals("second", list.get(1));
    }
}