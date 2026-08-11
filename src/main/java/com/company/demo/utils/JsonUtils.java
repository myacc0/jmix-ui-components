package com.company.demo.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.lang.Nullable;

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
