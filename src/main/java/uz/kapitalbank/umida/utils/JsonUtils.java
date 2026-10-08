package uz.kapitalbank.umida.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.lang.Nullable;

import java.util.Comparator;
import java.util.Objects;

public class JsonUtils {
    private JsonUtils() {}

    public static String prettify(@Nullable String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            JsonNode node = mapper.readTree(json);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            return json;
        }
    }

    /**
     * Whether two JSON documents hold the same data, regardless of formatting and key order. Numbers
     * are compared by value, so {@code 100} equals {@code 100.0}. A document that is not valid JSON
     * is compared as plain text.
     */
    public static boolean sameJson(@Nullable String json1, @Nullable String json2, ObjectMapper mapper) {
        if (Objects.equals(json1, json2)) {
            return true;
        }
        if (json1 == null || json2 == null) {
            return false;
        }
        try {
            return mapper.readTree(json1).equals(NUMBER_AWARE_COMPARATOR, mapper.readTree(json2));
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    private static final Comparator<JsonNode> NUMBER_AWARE_COMPARATOR = (node1, node2) -> {
        if (node1.equals(node2)) {
            return 0;
        }
        if (node1.isNumber() && node2.isNumber()) {
            return node1.decimalValue().compareTo(node2.decimalValue());
        }
        return 1;
    };

    public static <T> T parseConfig(String json, Class<T> clazz, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
