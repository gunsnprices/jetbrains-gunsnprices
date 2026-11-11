package com.gunsnprices.ide.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader and writer: enough for the caliber data file and for the "Copy as JSON" output. Numbers are
 * written the way the gunsnprices library writes them.
 */
public final class Json {
    private Json() {
    }

    // ------------------------------------------------------------------------------------------------ writing

    /** Writes Map, List, String, Number, Boolean or null as JSON, indented with {@code indent} spaces per level. */
    public static String write(Object value, int indent) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, indent, 0);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v, int indent, int level) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            quote(sb, s);
        } else if (v instanceof Double || v instanceof Float) {
            sb.append(number(((Number) v).doubleValue()));
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Map<?, ?> m) {
            if (m.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) {
                    sb.append(indent > 0 ? "," : ", ");
                }
                first = false;
                newline(sb, indent, level + 1);
                quote(sb, String.valueOf(e.getKey()));
                sb.append(": ");
                write(sb, e.getValue(), indent, level + 1);
            }
            newline(sb, indent, level);
            sb.append('}');
        } else if (v instanceof List<?> a) {
            if (a.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append('[');
            boolean first = true;
            for (Object x : a) {
                if (!first) {
                    sb.append(indent > 0 ? "," : ", ");
                }
                first = false;
                newline(sb, indent, level + 1);
                write(sb, x, indent, level + 1);
            }
            newline(sb, indent, level);
            sb.append(']');
        } else {
            quote(sb, v.toString());
        }
    }

    private static void newline(StringBuilder sb, int indent, int level) {
        if (indent > 0) {
            sb.append('\n').append(" ".repeat(indent * level));
        }
    }

    /** "4.02", "22.0", "0.0001": like Python's repr, never in exponent form. */
    public static String number(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return "null";
        }
        String t = Double.toString(d);
        if (t.indexOf('E') < 0) {
            return t;
        }
        String plain = new BigDecimal(t).stripTrailingZeros().toPlainString();
        return plain.indexOf('.') < 0 ? plain + ".0" : plain;
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------------------------------------ reading

    /**
     * Parses JSON text into Map (insertion-ordered), List, String, Double, Boolean or null.
     *
     * @throws IllegalArgumentException if the text is not valid JSON
     */
    public static Object parse(String text) {
        Reader r = new Reader(text);
        r.skipSpace();
        Object v = r.value();
        r.skipSpace();
        if (r.pos != text.length()) {
            throw r.error("unexpected text after the value");
        }
        return v;
    }

    private static final class Reader {
        private final String s;
        private int pos;

        Reader(String s) {
            this.s = s;
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException("Invalid JSON at " + pos + ": " + message);
        }

        void skipSpace() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object value() {
            if (pos >= s.length()) {
                throw error("unexpected end");
            }
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Object literal(String word, Object v) {
            if (!s.startsWith(word, pos)) {
                throw error("expected " + word);
            }
            pos += word.length();
            return v;
        }

        private Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            pos++; // {
            skipSpace();
            if (peek() == '}') {
                pos++;
                return m;
            }
            while (true) {
                skipSpace();
                if (peek() != '"') {
                    throw error("expected a property name");
                }
                String key = string();
                skipSpace();
                expect(':');
                skipSpace();
                m.put(key, value());
                skipSpace();
                char c = next();
                if (c == '}') {
                    return m;
                }
                if (c != ',') {
                    throw error("expected , or }");
                }
            }
        }

        private List<Object> array() {
            List<Object> a = new ArrayList<>();
            pos++; // [
            skipSpace();
            if (peek() == ']') {
                pos++;
                return a;
            }
            while (true) {
                skipSpace();
                a.add(value());
                skipSpace();
                char c = next();
                if (c == ']') {
                    return a;
                }
                if (c != ',') {
                    throw error("expected , or ]");
                }
            }
        }

        private String string() {
            pos++; // "
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= s.length()) {
                    throw error("unterminated string");
                }
                char c = s.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = next();
                switch (e) {
                    case '"', '\\', '/' -> sb.append(e);
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > s.length()) {
                            throw error("bad \\u escape");
                        }
                        try {
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw error("bad \\u escape");
                        }
                        pos += 4;
                    }
                    default -> throw error("bad escape");
                }
            }
        }

        private Double number() {
            int start = pos;
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) {
                pos++;
            }
            if (start == pos) {
                throw error("unexpected character");
            }
            try {
                return Double.parseDouble(s.substring(start, pos));
            } catch (NumberFormatException ex) {
                throw error("bad number");
            }
        }

        private char peek() {
            if (pos >= s.length()) {
                throw error("unexpected end");
            }
            return s.charAt(pos);
        }

        private char next() {
            char c = peek();
            pos++;
            return c;
        }

        private void expect(char c) {
            if (next() != c) {
                throw error("expected " + c);
            }
        }
    }
}
