package com.gunsnprices.ide.core;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Prices in titles, cells and JSON lines. Same rules as the VS Code extension. */
public final class Prices {
    private Prices() {
    }

    /** A price found in free text, and the text without it. */
    public record InText(Double price, String rest) {
    }

    private static final Pattern USD_PREFIX = Pattern.compile("^usd\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern USD_SUFFIX = Pattern.compile("\\s*usd$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOLLAR = Pattern.compile("^\\$\\s*");
    private static final Pattern THOUSANDS = Pattern.compile(",(?=\\d{3}\\b)");
    private static final Pattern PLAIN_NUMBER = Pattern.compile("^\\d+(\\.\\d+)?$");

    // A cell that holds nothing but a price: "18.99", "$18.99", "$18", "1,299.99".
    private static final Pattern PRICE_CELL = Pattern.compile(
            "^(\\$\\s?\\d{1,3}(,\\d{3})*(\\.\\d{1,2})?|\\$\\s?\\d+(\\.\\d{1,2})?|\\d{1,3}(,\\d{3})*\\.\\d{2}|\\d+\\.\\d{2})$");

    // A price inside free text: "$18.99", "USD 18.99", "18.99 USD".
    private static final Pattern PRICE_IN_TEXT = Pattern.compile(
            "(?:^|[\\s(@:,;|-])(\\$\\s?(\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d{1,2})?"
                    + "|USD\\s?(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d{1,2})?"
                    + "|(?:\\d{1,3}(?:,\\d{3})+|\\d+)\\.\\d{2}\\s?USD)(?=$|[\\s),;|])",
            Pattern.CASE_INSENSITIVE);

    // "price": 18.99 or "sale_price": "$18.99" in a JSON line
    private static final Pattern JSON_PRICE = Pattern.compile(
            "[\"'](?:sale_?price|price|our_?price|regular_?price|current_?price|offer_?price|list_?price)[\"']"
                    + "\\s*:\\s*[\"']?(?:USD\\s*)?\\$?\\s*(\\d[\\d,]*(?:\\.\\d+)?)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EMPTY_BRACKETS = Pattern.compile("[(\\[]\\s*[-|@:,;]?\\s*[)\\]]");
    private static final Pattern TRAILING_SEPARATOR = Pattern.compile("\\s*[-|@:,;]\\s*$");
    private static final Pattern SPACES = Pattern.compile("\\s{2,}");

    /**
     * Reads a price: "18.99", "$1,299.99", "USD 18.99". Returns null for anything else and for zero.
     */
    public static Double parsePrice(String value) {
        if (value == null) {
            return null;
        }
        String s = value.trim();
        s = USD_PREFIX.matcher(s).replaceFirst("");
        s = USD_SUFFIX.matcher(s).replaceFirst("");
        s = DOLLAR.matcher(s).replaceFirst("");
        s = THOUSANDS.matcher(s).replaceAll("");
        if (!PLAIN_NUMBER.matcher(s).matches()) {
            return null;
        }
        double n;
        try {
            n = Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
        return n > 0 && Double.isFinite(n) ? n : null;
    }

    /** True when the cell holds nothing but a price. */
    public static boolean isPriceCell(String cell) {
        return cell != null && PRICE_CELL.matcher(cell.trim()).matches();
    }

    /** Finds a price in free text and returns it with the text minus the price. */
    public static InText findPriceInText(String text) {
        Matcher m = PRICE_IN_TEXT.matcher(text);
        if (!m.find()) {
            return new InText(null, text);
        }
        int start = m.start(1);
        String rest = text.substring(0, start) + text.substring(m.end(1));
        rest = EMPTY_BRACKETS.matcher(rest).replaceAll("");
        rest = TRAILING_SEPARATOR.matcher(rest).replaceFirst("");
        rest = SPACES.matcher(rest).replaceAll(" ").trim();
        return new InText(parsePrice(m.group(1)), rest);
    }

    /** The value of a "price" (or "sale_price", ...) property on a JSON or JavaScript line, or null. */
    public static Double priceInJsonLine(String line) {
        Matcher m = JSON_PRICE.matcher(line);
        return m.find() ? parsePrice(m.group(1)) : null;
    }

    /** "$1,299.99". */
    public static String money(double n) {
        return "$" + String.format(Locale.US, "%,.2f", n);
    }

    /** "$0.38", or with cents: "$0.38 (37.98¢)". */
    public static String perRound(double n, boolean withCents) {
        String dollars = "$" + String.format(Locale.US, "%.2f", n);
        return withCents ? dollars + " (" + String.format(Locale.US, "%.2f", n * 100) + "¢)" : dollars;
    }

    /** A price as typed into a field: "18.99", "1299.5". */
    public static String plain(double n) {
        String s = String.format(Locale.US, "%.2f", n);
        return s.endsWith(".00") ? s.substring(0, s.length() - 3) : s;
    }
}
