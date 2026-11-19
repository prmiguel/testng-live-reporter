package io.github.prmiguel.testng.live.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny recursive-descent JSON reader used by tests.
 * <p>
 * The reporter itself must not depend on a JSON library, but its output still has to be
 * proven to be genuinely parseable, so tests bring their own reader.
 */
public final class TestJsonParser {

    private final String input;
    private int index;

    private TestJsonParser(String input) {
        this.input = input;
    }

    public static Object parse(String json) {
        TestJsonParser parser = new TestJsonParser(json);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.index != json.length()) {
            throw parser.error("trailing content");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object value = parse(json);
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Not a JSON object: " + json);
        }
        return (Map<String, Object>) value;
    }

    public static String readLine(String json, String key) {
        Object value = parseObject(json).get(key);
        return value == null ? null : value.toString();
    }

    private Object readValue() {
        skipWhitespace();
        if (index >= input.length()) {
            throw error("unexpected end of input");
        }
        char c = input.charAt(index);
        return switch (c) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> readNumber();
        };
    }

    private Map<String, Object> readObject() {
        Map<String, Object> result = new LinkedHashMap<>();
        index++;
        skipWhitespace();
        if (peek() == '}') {
            index++;
            return result;
        }
        while (true) {
            skipWhitespace();
            String name = readString();
            skipWhitespace();
            expect(':');
            result.put(name, readValue());
            skipWhitespace();
            char next = next();
            if (next == ',') {
                continue;
            }
            if (next == '}') {
                return result;
            }
            throw error("expected ',' or '}'");
        }
    }

    private List<Object> readArray() {
        List<Object> result = new ArrayList<>();
        index++;
        skipWhitespace();
        if (peek() == ']') {
            index++;
            return result;
        }
        while (true) {
            result.add(readValue());
            skipWhitespace();
            char next = next();
            if (next == ',') {
                continue;
            }
            if (next == ']') {
                return result;
            }
            throw error("expected ',' or ']'");
        }
    }

    private String readString() {
        expect('"');
        StringBuilder result = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return result.toString();
            }
            if (c != '\\') {
                result.append(c);
                continue;
            }
            char escape = next();
            switch (escape) {
                case '"' -> result.append('"');
                case '\\' -> result.append('\\');
                case '/' -> result.append('/');
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> result.append(readUnicode());
                default -> throw error("invalid escape \\" + escape);
            }
        }
    }

    private char readUnicode() {
        String hex = input.substring(index, index + 4);
        index += 4;
        return (char) Integer.parseInt(hex, 16);
    }

    private Object readNumber() {
        int start = index;
        while (index < input.length() && "-+.eE0123456789".indexOf(input.charAt(index)) >= 0) {
            index++;
        }
        if (start == index) {
            throw error("expected a value");
        }
        String token = input.substring(start, index);
        if (token.contains(".") || token.contains("e") || token.contains("E")) {
            return Double.parseDouble(token);
        }
        return Long.parseLong(token);
    }

    private Object readLiteral(String literal, Object value) {
        if (!input.startsWith(literal, index)) {
            throw error("expected " + literal);
        }
        index += literal.length();
        return value;
    }

    private void expect(char expected) {
        if (next() != expected) {
            throw error("expected '" + expected + "'");
        }
    }

    private char peek() {
        if (index >= input.length()) {
            throw error("unexpected end of input");
        }
        return input.charAt(index);
    }

    private char next() {
        char c = peek();
        index++;
        return c;
    }

    private void skipWhitespace() {
        while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
            index++;
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at index " + index + " of: " + input);
    }
}
