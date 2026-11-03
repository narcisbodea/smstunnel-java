package io.smstunnel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void readsAnSmsTunnelAnswer() {
        Object value = Json.parse("{\"success\":true,\"messageId\":\"6aea\",\"data\":{\"branded\":false,"
                + "\"remaining\":{\"clean\":95,\"total\":\"unlimited\"},\"list\":[1,2.5,null,\"x\"]}}");
        Map<String, Object> json = Fields.object(value);
        assertEquals(Boolean.TRUE, json.get("success"));
        Map<String, Object> data = Fields.object(json, "data");
        assertEquals(95L, Fields.object(data, "remaining").get("clean"));
        assertEquals(Arrays.asList(1L, 2.5, null, "x"), data.get("list"));
    }

    @Test
    void readsEscapesAndUnicode() {
        assertEquals("a\"b\\c/d\b\f\n\r\t", Json.parse("\"a\\\"b\\\\c\\/d\\b\\f\\n\\r\\t\""));
        assertEquals("ăîșț — 😀", Json.parse("\"\\u0103\\u00ee\\u0219\\u021b \\u2014 \\ud83d\\ude00\""));
        assertEquals("ăîșț 😀", Json.parse("\"ăîșț 😀\""));
    }

    @Test
    void readsNumbers() {
        assertEquals(0L, Json.parse("0"));
        assertEquals(-12L, Json.parse("-12"));
        assertEquals(1.5e3, Json.parse("1.5e3"));
        assertEquals(1e20, Json.parse("100000000000000000000"));
    }

    @Test
    void readsWhitespaceAndEmptyContainers() {
        Map<String, Object> json = Fields.object(Json.parse(" \n{ \"a\" : [ ] , \"b\" : { } }\t"));
        assertTrue(((List<?>) json.get("a")).isEmpty());
        assertTrue(((Map<?, ?>) json.get("b")).isEmpty());
        assertNull(Json.parse("null"));
    }

    @Test
    void refusesWhatIsNotJson() {
        for (String bad : new String[] {"", "<html>Proxy error</html>", "{\"a\":1,}", "[1 2]", "\"open", "01",
                "{a:1}", "tru", "1.", "-", "\"bad \\x escape\"", "{\"a\":1} trailing", "\"tab\there\""}) {
            assertThrows(IllegalArgumentException.class, () -> Json.parse(bad), bad);
        }
    }

    @Test
    void refusesVeryDeepNesting() {
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            deep.append('[');
        }
        assertThrows(IllegalArgumentException.class, () -> Json.parse(deep.toString()));
    }

    @Test
    void writesCompactJsonThatReadsBack() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("to", "+14155550123");
        body.put("message", "Line 1\nLine \"2\" \\ ăîșț 😀 \u0001");
        body.put("n", 42);
        body.put("d", 2.5);
        body.put("whole", 3.0);
        body.put("ok", true);
        body.put("none", null);
        body.put("list", Arrays.asList("a", 1));
        String json = Json.write(body);
        assertEquals("{\"to\":\"+14155550123\",\"message\":\"Line 1\\nLine \\\"2\\\" \\\\ ăîșț 😀 \\u0001\","
                + "\"n\":42,\"d\":2.5,\"whole\":3,\"ok\":true,\"none\":null,\"list\":[\"a\",1]}", json);
        Map<String, Object> back = Fields.object(Json.parse(json));
        assertEquals(body.get("message"), back.get("message"));
    }

    @Test
    void escapesLoneSurrogatesAndLineSeparators() {
        assertEquals("\"\\ud800 \\u2028\"", Json.write("\ud800 \u2028"));
    }

    @Test
    void refusesValuesJsonCannotHold() {
        assertThrows(IllegalArgumentException.class, () -> Json.write(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Json.write(new Object()));
    }
}
