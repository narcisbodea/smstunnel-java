package io.smstunnel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader and writer (RFC 8259) for SMSTunnel's requests and answers, so the library needs no JSON
 * dependency (and does not have to pick between Jackson 2, Jackson 3 and Gson for its users).
 *
 * <p>Reading gives unmodifiable {@code Map<String, Object>} for objects (keys in document order), unmodifiable
 * {@code List<Object>} for arrays, {@code Long} for integers that fit in a long, {@code Double} for other numbers,
 * {@code String}, {@code Boolean} and {@code null}. Writing takes the same types, plus any {@link Number},
 * {@link Map} with string keys and {@link Iterable}.
 */
final class Json {

    private static final int MAX_DEPTH = 256;

    private Json() {
    }

    /** Reads one JSON value. Throws {@link IllegalArgumentException} for text that is not JSON. */
    static Object parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Invalid JSON: no text");
        }
        Parser parser = new Parser(text);
        parser.skipWhitespace();
        Object value = parser.readValue(0);
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw parser.error("unexpected text after the JSON value");
        }
        return value;
    }

    /** Writes a value as compact JSON. */
    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(out, value, 0);
        return out.toString();
    }

    private static void writeValue(StringBuilder out, Object value, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("JSON nested too deeply");
        }
        if (value == null) {
            out.append("null");
        } else if (value instanceof CharSequence) {
            writeString(out, value.toString());
        } else if (value instanceof Boolean) {
            out.append(value.toString());
        } else if (value instanceof Number) {
            writeNumber(out, (Number) value);
        } else if (value instanceof Map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    throw new IllegalArgumentException("JSON object names must be strings");
                }
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(out, (String) entry.getKey());
                out.append(':');
                writeValue(out, entry.getValue(), depth + 1);
            }
            out.append('}');
        } else if (value instanceof Iterable) {
            out.append('[');
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeValue(out, item, depth + 1);
            }
            out.append(']');
        } else {
            throw new IllegalArgumentException("Cannot write " + value.getClass().getName() + " as JSON");
        }
    }

    private static void writeNumber(StringBuilder out, Number number) {
        if (number instanceof Double || number instanceof Float) {
            double d = number.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("JSON has no NaN or Infinity");
            }
            if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                out.append((long) d);
            } else {
                out.append(d);
            }
        } else {
            out.append(number.toString());
        }
    }

    private static void writeString(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                default:
                    if (Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                        out.append(c).append(s.charAt(++i));
                    } else if (c < 0x20 || Character.isSurrogate(c) || c == ' ' || c == ' ') {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return pos >= s.length();
        }

        void skipWhitespace() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    return;
                }
            }
        }

        IllegalArgumentException error(String what) {
            return new IllegalArgumentException("Invalid JSON at character " + pos + ": " + what);
        }

        private boolean peek(char c) {
            return pos < s.length() && s.charAt(pos) == c;
        }

        private boolean peekDigit() {
            return pos < s.length() && s.charAt(pos) >= '0' && s.charAt(pos) <= '9';
        }

        Object readValue(int depth) {
            if (depth > MAX_DEPTH) {
                throw error("nested too deeply");
            }
            if (atEnd()) {
                throw error("unexpected end of the text");
            }
            char c = s.charAt(pos);
            switch (c) {
                case '{':
                    return readObject(depth);
                case '[':
                    return readArray(depth);
                case '"':
                    return readString();
                case 't':
                    readWord("true");
                    return Boolean.TRUE;
                case 'f':
                    readWord("false");
                    return Boolean.FALSE;
                case 'n':
                    readWord("null");
                    return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return readNumber();
                    }
                    throw error("unexpected character '" + c + "'");
            }
        }

        private void readWord(String word) {
            if (!s.startsWith(word, pos)) {
                throw error("expected " + word);
            }
            pos += word.length();
        }

        private Map<String, Object> readObject(int depth) {
            pos++;
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                pos++;
                return Collections.unmodifiableMap(map);
            }
            while (true) {
                skipWhitespace();
                if (!peek('"')) {
                    throw error("expected a quoted name");
                }
                String name = readString();
                skipWhitespace();
                if (!peek(':')) {
                    throw error("expected ':'");
                }
                pos++;
                skipWhitespace();
                map.put(name, readValue(depth + 1));
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                } else if (peek('}')) {
                    pos++;
                    return Collections.unmodifiableMap(map);
                } else {
                    throw error("expected ',' or '}'");
                }
            }
        }

        private List<Object> readArray(int depth) {
            pos++;
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                pos++;
                return Collections.unmodifiableList(list);
            }
            while (true) {
                skipWhitespace();
                list.add(readValue(depth + 1));
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                } else if (peek(']')) {
                    pos++;
                    return Collections.unmodifiableList(list);
                } else {
                    throw error("expected ',' or ']'");
                }
            }
        }

        private String readString() {
            pos++;
            StringBuilder sb = null;
            int start = pos;
            while (true) {
                if (atEnd()) {
                    throw error("unterminated string");
                }
                char c = s.charAt(pos);
                if (c == '"') {
                    String result = sb == null ? s.substring(start, pos) : sb.append(s, start, pos).toString();
                    pos++;
                    return result;
                }
                if (c == '\\') {
                    if (sb == null) {
                        sb = new StringBuilder();
                    }
                    sb.append(s, start, pos);
                    pos++;
                    if (atEnd()) {
                        throw error("unterminated escape");
                    }
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '/':
                            sb.append('/');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'u':
                            sb.append(readUnicodeEscape());
                            break;
                        default:
                            throw error("unknown escape \\" + e);
                    }
                    start = pos;
                } else if (c < 0x20) {
                    throw error("control character inside a string");
                } else {
                    pos++;
                }
            }
        }

        private char readUnicodeEscape() {
            if (pos + 4 > s.length()) {
                throw error("short \\u escape");
            }
            int code = 0;
            for (int i = 0; i < 4; i++) {
                int digit = Character.digit(s.charAt(pos + i), 16);
                if (digit < 0) {
                    throw error("bad \\u escape");
                }
                code = code * 16 + digit;
            }
            pos += 4;
            return (char) code;
        }

        private Object readNumber() {
            int start = pos;
            if (peek('-')) {
                pos++;
            }
            if (peek('0')) {
                pos++;
            } else if (peekDigit()) {
                while (peekDigit()) {
                    pos++;
                }
            } else {
                throw error("bad number");
            }
            boolean integer = true;
            if (peek('.')) {
                integer = false;
                pos++;
                if (!peekDigit()) {
                    throw error("bad number");
                }
                while (peekDigit()) {
                    pos++;
                }
            }
            if (peek('e') || peek('E')) {
                integer = false;
                pos++;
                if (peek('+') || peek('-')) {
                    pos++;
                }
                if (!peekDigit()) {
                    throw error("bad number");
                }
                while (peekDigit()) {
                    pos++;
                }
            }
            String text = s.substring(start, pos);
            if (integer) {
                try {
                    return Long.parseLong(text);
                } catch (NumberFormatException tooBig) {
                    return Double.parseDouble(text);
                }
            }
            return Double.parseDouble(text);
        }
    }
}
