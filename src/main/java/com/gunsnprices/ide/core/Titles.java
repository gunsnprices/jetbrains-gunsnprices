package com.gunsnprices.ide.core;

import com.gunsnprices.Caliber;
import com.gunsnprices.GunsNPrices;
import com.gunsnprices.ParseOptions;
import com.gunsnprices.ParseResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Glue between editor text and the gunsnprices parser. Same rules as the VS Code extension. */
public final class Titles {
    private Titles() {
    }

    /** Longest text that is parsed as a title. */
    public static final int MAX_TITLE_LENGTH = 400;

    private static final Pattern PRODUCT_WORDS = Pattern.compile(
            "\\b(\\d+\\s?gr|grains?|fmj|jhp|jsp|hp|sp|otm|bthp|v-?max|buckshot|birdshot|slugs?|pistols?|rifles?"
                    + "|shotguns?|revolvers?|handguns?|carbines?|ammo|ammunition|rounds?|rds?|cartridges?|shells?|bbl"
                    + "|barrel|semi-?auto|bolt[\\s-]action|pump|o/u|over[\\s/-]under|lever[\\s-]action|optics? ready"
                    + "|threaded)\\b",
            Pattern.CASE_INSENSITIVE);

    // words that may appear next to a caliber without making the text "more than a caliber"
    private static final Set<String> CALIBER_WORDS = new HashSet<>(Arrays.asList((
            "mm cal caliber calibre x × win winchester rem remington mag magnum wby weatherby spl special spc sprg "
                    + "springfield spfd government govt gov acp auto automatic s&w sw smith wesson & and blackout blk "
                    + "black out aac nato creedmoor creed cm crd creedmore parabellum para luger gauge ga gauges bore "
                    + "lr long rifle h&h holland h&r bm bushmaster bush short colt legend lgnd leg wmr hmr wsm wssm rum "
                    + "ultra super express nitro lapua norma prc grendel arc sharps carbine m1 +p +p+ ultramag bmg "
                    + "hornet mauser kurz makarov tokarev").split(" ")));

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[\\s,/()]+");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern ONLY_NUMBER = Pattern.compile("^[.\\d\\s]+$");
    private static final Pattern NUMBER = Pattern.compile("^\\d[\\d,]*$");
    private static final Pattern VALUE_LINE = Pattern.compile("^(\\s*)([\"'`]?)([\\s\\S]*?)\\2(\\s*)$");

    /** How much a parse result looks like a real product title. 0 = nothing recognized. */
    public static int score(ParseResult r) {
        return (r.model() != null ? 8 : 0)
                + (r.calibers().isEmpty() ? 0 : 4)
                + (r.capacity() != null || r.barrelLength() != null || r.rounds() != null ? 2 : 0);
    }

    /**
     * True when the text looks like a gun or ammo product title, not just any text that happens to contain a
     * caliber-like number ("380 people").
     */
    public static boolean looksLikeTitle(ParseResult r, String text) {
        if (text.length() < 4 || text.length() > MAX_TITLE_LENGTH) {
            return false;
        }
        if (r.model() != null) {
            return true;
        }
        if (r.calibers().isEmpty()) {
            return false;
        }
        return r.rounds() != null || r.capacity() != null || r.barrelLength() != null
                || PRODUCT_WORDS.matcher(text).find();
    }

    /**
     * Parses a title. A price written in the text ("$18.99") is taken out of the title and used for the cost per
     * round; otherwise {@code contextPrice} (a price from the same CSV row or JSON object) is used.
     */
    public static ParsedTitle parse(String text, Double contextPrice, int line) {
        Prices.InText p = Prices.findPriceInText(text.trim());
        String title = p.rest().trim();
        Double price = p.price() != null ? p.price() : contextPrice;
        ParseResult r = GunsNPrices.parseTitle(title,
                price == null ? ParseOptions.NONE : ParseOptions.NONE.withPrice(price));
        return new ParsedTitle(line, title, price, r);
    }

    /** {@link #parse(String, Double, int)} without a context price or line number. */
    public static ParsedTitle parse(String text) {
        return parse(text, null, 0);
    }

    /**
     * The title on one line of a selection: the best quoted string, the best CSV/TSV cell (with a price from
     * another cell), or the whole line without a list marker. Null for a blank line or one with no title.
     *
     * @param delimiter ',' or '\t' for CSV/TSV files, 0 otherwise
     */
    public static ParsedTitle parseLine(String line, int lineNumber, char delimiter) {
        String text = line.strip();
        if (text.isEmpty() || text.length() > 4000) {
            return null;
        }
        Double jsonPrice = Prices.priceInJsonLine(text);
        ParsedTitle best = null;
        if (delimiter != 0 && text.indexOf(delimiter) >= 0) {
            List<TextSegments.Segment> cells = TextSegments.cells(text, delimiter);
            Double cellPrice = null;
            for (TextSegments.Segment c : cells) {
                if (Prices.isPriceCell(c.text())) {
                    cellPrice = Prices.parsePrice(c.text());
                    break;
                }
            }
            for (TextSegments.Segment c : cells) {
                if (c.text().length() >= 3 && c.text().length() <= MAX_TITLE_LENGTH && !Prices.isPriceCell(c.text())) {
                    best = better(best, parse(c.text(), cellPrice, lineNumber));
                }
            }
            if (best != null && score(best.result()) > 0) {
                return best;
            }
        }
        for (TextSegments.Segment s : TextSegments.quotedSegments(text)) {
            if (s.text().length() >= 3 && s.text().length() <= MAX_TITLE_LENGTH) {
                best = better(best, parse(s.text(), jsonPrice, lineNumber));
            }
        }
        if (best != null && score(best.result()) > 0) {
            return best;
        }
        String plain = TextSegments.stripListMarker(text);
        if (plain.length() > MAX_TITLE_LENGTH) {
            return null;
        }
        ParsedTitle whole = parse(plain, null, lineNumber);
        return score(whole.result()) > 0 ? whole : null;
    }

    private static ParsedTitle better(ParsedTitle a, ParsedTitle b) {
        if (a == null) {
            return b;
        }
        return score(b.result()) > score(a.result()) ? b : a;
    }

    // ---------------------------------------------------------------------------------------------- calibers

    /** The caliber when the text is a caliber and nothing else, or why not. */
    public record CaliberOnly(Caliber caliber, List<Caliber> multiple) {
    }

    /**
     * The caliber when the text is a caliber and nothing else ("9mm Luger", ".223 Rem", "12ga"). {@code multiple} is
     * set when the text names more than one caliber.
     */
    public static CaliberOnly caliberOnly(String text) {
        CaliberOnly none = new CaliberOnly(null, List.of());
        String t = text.trim();
        if (t.isEmpty() || t.length() > 40) {
            return none;
        }
        List<Caliber> found = GunsNPrices.findCalibers(t);
        if (found.size() > 1) {
            return new CaliberOnly(null, found);
        }
        Optional<Caliber> c = GunsNPrices.normalizeCaliber(t);
        if (c.isEmpty()) {
            return none;
        }
        String name = c.get().name().toLowerCase(Locale.ROOT);
        List<String> tokens = new ArrayList<>();
        for (String tok : TOKEN_SPLIT.split(t.toLowerCase(Locale.ROOT))) {
            if (!tok.isEmpty()) {
                tokens.add(tok);
            }
        }
        if (tokens.size() > 6) {
            return none;
        }
        for (String tok : tokens) {
            String bare = tok.replaceAll("^\\.|\\.$", "");
            boolean ok = DIGIT.matcher(tok).find() || CALIBER_WORDS.contains(bare)
                    || (tok.length() > 1 && name.contains(tok));
            if (!ok) {
                return none;
            }
        }
        return new CaliberOnly(c.get(), List.of());
    }

    /** The caliber when the text is only a caliber; null for a bare number ("380", "5.56"), which is too ambiguous. */
    public static Caliber caliberValue(String text) {
        if (ONLY_NUMBER.matcher(text).matches()) {
            return null;
        }
        return caliberOnly(text).caliber();
    }

    /** The caliber name, with "×" written as "x" when {@code ascii} is set. */
    public static String caliberName(Caliber c, boolean ascii) {
        return ascii ? c.name().replace('×', 'x') : c.name();
    }

    /** Result of {@link #normalizeCaliberText}. */
    public record NormalizeResult(String text, List<String[]> changed, List<String> unknown, List<String> ambiguous) {
    }

    /**
     * Normalizes caliber text. Each line of the input is one value; surrounding whitespace and quotes are kept.
     * Text that is not recognized, or that holds more than one caliber or more than a caliber, is left as it is.
     */
    public static NormalizeResult normalizeCaliberText(String input, boolean ascii) {
        List<String[]> changed = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        List<String> ambiguous = new ArrayList<>();
        StringBuilder out = new StringBuilder();
        Matcher lines = Pattern.compile("\\r?\\n").matcher(input);
        int pos = 0;
        while (true) {
            boolean more = lines.find();
            int end = more ? lines.start() : input.length();
            out.append(normalizeValue(input.substring(pos, end), ascii, changed, unknown, ambiguous));
            if (!more) {
                break;
            }
            out.append(lines.group());
            pos = lines.end();
        }
        return new NormalizeResult(out.toString(), changed, unknown, ambiguous);
    }

    private static String normalizeValue(String part, boolean ascii, List<String[]> changed, List<String> unknown,
                                         List<String> ambiguous) {
        Matcher m = VALUE_LINE.matcher(part);
        if (!m.matches() || m.group(3).isBlank()) {
            return part;
        }
        String value = m.group(3);
        CaliberOnly c = caliberOnly(value);
        if (c.caliber() == null) {
            if (!c.multiple().isEmpty() || GunsNPrices.normalizeCaliber(value).isPresent()) {
                ambiguous.add(value);
            } else {
                unknown.add(value);
            }
            return part;
        }
        String name = caliberName(c.caliber(), ascii);
        if (!name.equals(value)) {
            changed.add(new String[] {value, name});
        }
        return m.group(1) + m.group(2) + name + m.group(2) + m.group(4);
    }

    // ---------------------------------------------------------------------------------------------- rounds

    /**
     * A round count from a number ("50", "1,000") or from a title that has one ("50 Rounds", "20/box"), or null.
     */
    public static Integer roundsFrom(String value) {
        String v = value == null ? "" : value.trim();
        if (NUMBER.matcher(v).matches()) {
            try {
                int n = Integer.parseInt(v.replace(",", ""));
                return n > 0 ? n : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (v.isEmpty()) {
            return null;
        }
        var r = GunsNPrices.roundsPerBox(v);
        return r.isPresent() ? r.getAsInt() : null;
    }
}
