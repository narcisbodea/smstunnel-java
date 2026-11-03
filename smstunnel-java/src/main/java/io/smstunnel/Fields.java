package io.smstunnel;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Typed reads from a JSON object that never throw: a missing or mistyped field reads as null (or false). */
final class Fields {

    private Fields() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.emptyMap();
    }

    static Map<String, Object> object(Map<String, Object> map, String name) {
        return object(map.get(name));
    }

    /** The object, or null when the field is missing or not an object. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> objectOrNull(Map<String, Object> map, String name) {
        Object value = map.get(name);
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Map<String, Object> map, String name) {
        Object value = map.get(name);
        return value instanceof List ? (List<Object>) value : Collections.emptyList();
    }

    /** A string field; numbers and booleans are given as text, other types as null. */
    static String string(Map<String, Object> map, String name) {
        Object value = map.get(name);
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return null;
    }

    static boolean bool(Map<String, Object> map, String name) {
        return Boolean.TRUE.equals(map.get(name));
    }

    static Long longValue(Map<String, Object> map, String name) {
        Object value = map.get(name);
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    static long longValue(Map<String, Object> map, String name, long fallback) {
        Long value = longValue(map, name);
        return value == null ? fallback : value;
    }

    static Integer intValue(Map<String, Object> map, String name) {
        Object value = map.get(name);
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    /** An ISO 8601 time such as {@code 2026-11-03T19:13:38.656Z}, or null. */
    static Instant instant(Map<String, Object> map, String name) {
        Object value = map.get(name);
        if (!(value instanceof String)) {
            return null;
        }
        try {
            return Instant.parse((String) value);
        } catch (DateTimeParseException notUtc) {
            try {
                return OffsetDateTime.parse((String) value).toInstant();
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    }
}
