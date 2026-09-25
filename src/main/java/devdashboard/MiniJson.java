package devdashboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MiniJson {
    private MiniJson() {}

    static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.value();
        parser.space();
        if (parser.position != text.length()) throw new IllegalArgumentException("Trailing JSON data");
        return value;
    }

    static String write(Object value) {
        if (value == null) return "null";
        if (value instanceof String string) return quote(string);
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{");
            boolean comma = false;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (comma) out.append(',');
                out.append(quote(String.valueOf(entry.getKey()))).append(':').append(write(entry.getValue()));
                comma = true;
            }
            return out.append('}').toString();
        }
        if (value instanceof Iterable<?> values) {
            StringBuilder out = new StringBuilder("[");
            boolean comma = false;
            for (Object item : values) {
                if (comma) out.append(',');
                out.append(write(item));
                comma = true;
            }
            return out.append(']').toString();
        }
        throw new IllegalArgumentException("Cannot encode " + value.getClass().getName());
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 32) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }

    private static final class Parser {
        private final String text;
        private int position;

        private Parser(String text) { this.text = text; }

        private Object value() {
            space();
            if (position >= text.length()) throw new IllegalArgumentException("Expected JSON value");
            return switch (text.charAt(position)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", true);
                case 'f' -> literal("false", false);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> result = new LinkedHashMap<>();
            position++;
            space();
            if (take('}')) return result;
            do {
                space();
                String key = string();
                space();
                expect(':');
                result.put(key, value());
                space();
            } while (take(','));
            expect('}');
            return result;
        }

        private List<Object> array() {
            List<Object> result = new ArrayList<>();
            position++;
            space();
            if (take(']')) return result;
            do {
                result.add(value());
                space();
            } while (take(','));
            expect(']');
            return result;
        }

        private String string() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (position < text.length()) {
                char c = text.charAt(position++);
                if (c == '"') return result.toString();
                if (c != '\\') result.append(c);
                else {
                    if (position >= text.length()) throw new IllegalArgumentException("Incomplete JSON escape");
                    char escaped = text.charAt(position++);
                    switch (escaped) {
                        case '"', '\\', '/' -> result.append(escaped);
                        case 'b' -> result.append('\b');
                        case 'f' -> result.append('\f');
                        case 'n' -> result.append('\n');
                        case 'r' -> result.append('\r');
                        case 't' -> result.append('\t');
                        case 'u' -> {
                            if (position + 4 > text.length()) throw new IllegalArgumentException("Incomplete unicode escape");
                            result.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                            position += 4;
                        }
                        default -> throw new IllegalArgumentException("Invalid JSON escape");
                    }
                }
            }
            throw new IllegalArgumentException("Unclosed JSON string");
        }

        private Object number() {
            int start = position;
            if (take('-')) {}
            while (position < text.length() && Character.isDigit(text.charAt(position))) position++;
            if (take('.')) while (position < text.length() && Character.isDigit(text.charAt(position))) position++;
            if (position < text.length() && (text.charAt(position) == 'e' || text.charAt(position) == 'E')) {
                position++;
                if (position < text.length() && (text.charAt(position) == '+' || text.charAt(position) == '-')) position++;
                while (position < text.length() && Character.isDigit(text.charAt(position))) position++;
            }
            String token = text.substring(start, position);
            try { return token.contains(".") || token.contains("e") || token.contains("E")
                    ? Double.parseDouble(token) : Long.parseLong(token); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid JSON number"); }
        }

        private Object literal(String token, Object value) {
            if (!text.startsWith(token, position)) throw new IllegalArgumentException("Invalid JSON literal");
            position += token.length();
            return value;
        }

        private void space() { while (position < text.length() && Character.isWhitespace(text.charAt(position))) position++; }
        private boolean take(char expected) {
            if (position < text.length() && text.charAt(position) == expected) { position++; return true; }
            return false;
        }
        private void expect(char expected) {
            if (!take(expected)) throw new IllegalArgumentException("Expected '" + expected + "'");
        }
    }
}
