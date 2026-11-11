package com.gunsnprices.ide.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Finds the text to parse on one line without a language-specific parser: quoted strings, CSV/TSV cells, list items.
 * Offsets are columns in the line; {@code start}/{@code end} delimit the raw text between the quotes.
 */
public final class TextSegments {
    private TextSegments() {
    }

    /** A piece of a line: raw columns [start, end) and its text with quotes and escapes resolved. */
    public record Segment(int start, int end, String text) {
    }

    private static final String OPENERS = " \t=:(,[{;!+|<>";
    private static final String CLOSERS = " \t,;)]}:+.|<>";
    private static final String STRING_PREFIXES = "fFrRbBuU@$";
    private static final Pattern LIST_MARKER = Pattern.compile("^\\s*(?:[-*+•]|\\d{1,4}[.)])\\s+");

    /**
     * Every quoted string on the line: "...", '...' and `...`. A quote opens a string only at the start of the line,
     * after a space or punctuation, so apostrophes ("Henry's") and inch marks ("4.02\"") are not taken for quotes.
     * Backslash escapes and doubled quotes (CSV) are allowed inside.
     */
    public static List<Segment> quotedSegments(String line) {
        List<Segment> out = new ArrayList<>();
        int n = line.length();
        int i = 0;
        while (i < n) {
            char q = line.charAt(i);
            if ((q == '"' || q == '\'' || q == '`') && opensAt(line, i)) {
                int close = findClose(line, i + 1, q);
                if (close >= 0) {
                    String raw = line.substring(i + 1, close);
                    out.add(new Segment(i + 1, close, unquote(raw, q)));
                    i = close + 1;
                    continue;
                }
            }
            i++;
        }
        return out;
    }

    /** The quoted string at column {@code col} (on its quotes or between them), or null. */
    public static Segment quotedSegmentAt(String line, int col) {
        for (Segment s : quotedSegments(line)) {
            if (col >= s.start() - 1 && col <= s.end() + 1) {
                return s;
            }
        }
        return null;
    }

    private static boolean opensAt(String line, int i) {
        if (i == 0) {
            return true;
        }
        char prev = line.charAt(i - 1);
        if (OPENERS.indexOf(prev) >= 0) {
            return true;
        }
        // f"...", r'...', @"...": a one-letter string prefix after a boundary
        return STRING_PREFIXES.indexOf(prev) >= 0 && (i == 1 || OPENERS.indexOf(line.charAt(i - 2)) >= 0);
    }

    private static int findClose(String line, int from, char q) {
        int n = line.length();
        int k = from;
        while (k < n) {
            char c = line.charAt(k);
            if (c == '\\' && q != '`') {
                k += 2;
                continue;
            }
            if (c == q) {
                if (k + 1 < n && line.charAt(k + 1) == q && k > from) {
                    k += 2; // "" inside a CSV cell
                    continue;
                }
                if (k + 1 >= n || CLOSERS.indexOf(line.charAt(k + 1)) >= 0) {
                    return k;
                }
            }
            k++;
        }
        return -1;
    }

    private static String unquote(String raw, char q) {
        String s = raw;
        if (q == '"' && s.contains("\"\"") && !s.contains("\\")) {
            s = s.replace("\"\"", "\"");
        }
        return unescape(s);
    }

    /** Resolves backslash escapes: \" \' \\ \/ \n \t \r and \\uXXXX. Text without backslashes is returned as is. */
    public static String unescape(String s) {
        if (s.indexOf('\\') < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c != '\\' || k + 1 >= s.length()) {
                sb.append(c);
                continue;
            }
            char e = s.charAt(++k);
            switch (e) {
                case 'n', 'r', 't' -> sb.append(' ');
                case 'u' -> {
                    if (k + 4 < s.length() && s.substring(k + 1, k + 5).matches("[0-9a-fA-F]{4}")) {
                        sb.append((char) Integer.parseInt(s.substring(k + 1, k + 5), 16));
                        k += 4;
                    } else {
                        sb.append('\\').append(e);
                    }
                }
                case '"', '\'', '\\', '/', '`', '$' -> sb.append(e);
                default -> sb.append('\\').append(e);
            }
        }
        return sb.toString();
    }

    /**
     * Splits a CSV or TSV line into cells (RFC 4180 quotes). Each cell's columns are its raw text, without the
     * surrounding quotes; its text is unquoted and trimmed.
     */
    public static List<Segment> cells(String line, char delimiter) {
        List<Segment> out = new ArrayList<>();
        for (int[] b : cellBounds(line, delimiter)) {
            out.add(cell(line, b[0], b[1]));
        }
        return out;
    }

    /** The cell at column {@code col} (anywhere between its delimiters), or null. */
    public static Segment cellAt(String line, int col, char delimiter) {
        for (int[] b : cellBounds(line, delimiter)) {
            if (col >= b[0] && col <= b[1]) {
                return cell(line, b[0], b[1]);
            }
        }
        return null;
    }

    /** [start, end) of each cell, between delimiters that are not inside quotes. */
    private static List<int[]> cellBounds(String line, char delimiter) {
        List<int[]> out = new ArrayList<>();
        boolean quoted = false;
        int start = 0;
        for (int k = 0; k < line.length(); k++) {
            char c = line.charAt(k);
            if (c == '"') {
                quoted = !quoted;
            } else if (c == delimiter && !quoted) {
                out.add(new int[] {start, k});
                start = k + 1;
            }
        }
        out.add(new int[] {start, line.length()});
        return out;
    }

    private static Segment cell(String line, int start, int end) {
        String raw = line.substring(start, end);
        int lead = 0;
        while (lead < raw.length() && Character.isWhitespace(raw.charAt(lead))) {
            lead++;
        }
        int open = start + lead;
        if (open < end && line.charAt(open) == '"') {
            int close = line.lastIndexOf('"', end - 1);
            if (close <= open) {
                close = end;
            }
            String text = line.substring(open + 1, close).replace("\"\"", "\"").trim();
            return new Segment(open + 1, close, text);
        }
        return new Segment(start, end, raw.trim());
    }

    /** "- Glock 19", "1. Glock 19", "• Glock 19" -> "Glock 19". */
    public static String stripListMarker(String s) {
        return LIST_MARKER.matcher(s).replaceFirst("").trim();
    }
}
