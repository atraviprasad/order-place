package com.orderplace.catalog.mapper;

import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Converts product attributes between a Map and the JSON text stored in the jsonb column. */
public final class AttributesJson {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<LinkedHashMap<String, Object>> TYPE = new TypeReference<>() {};

    private AttributesJson() {}

    public static String toJson(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) return "{}";
        return MAPPER.writeValueAsString(attributes);
    }

    public static Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        return MAPPER.readValue(json, TYPE);
    }
}
