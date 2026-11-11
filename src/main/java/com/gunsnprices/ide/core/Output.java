package com.gunsnprices.ide.core;

import com.gunsnprices.Caliber;
import com.gunsnprices.Kind;
import com.gunsnprices.ModelMatch;
import com.gunsnprices.ParseResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Output formats: the fields shown in the popup, JSON and CSV. */
public final class Output {
    private Output() {
    }

    /**
     * Output settings.
     *
     * @param links add GunsNPrices caliber page URLs
     * @param ascii write "×" as "x" in caliber names
     */
    public record Options(boolean links, boolean ascii) {
        /** No links, "×" kept. */
        public static final Options DEFAULT = new Options(false, false);
    }

    /** A label and a value for the result popup. */
    public record Field(String label, String value) {
    }

    private static final Pattern CSV_QUOTE_NEEDED = Pattern.compile("[\",\\r\\n]|^\\s|\\s$");

    /** "Glock 19 Gen 5 MOS", or "Ammo · 9×19mm Parabellum · 50 rounds", or "Firearm". */
    public static String heading(ParsedTitle t, Options o) {
        ParseResult r = t.result();
        if (r.kind() == Kind.AMMO) {
            List<String> parts = new ArrayList<>();
            parts.add("Ammo");
            if (r.caliber() != null) {
                parts.add(Titles.caliberName(r.caliber(), o.ascii()));
            }
            if (r.rounds() != null) {
                parts.add(r.rounds() + " rounds");
            }
            return String.join(" · ", parts);
        }
        return r.model() != null ? r.model().model() : "Firearm";
    }

    /** The fields that were found, in display order. */
    public static List<Field> fields(ParsedTitle t, Options o) {
        ParseResult r = t.result();
        List<Field> f = new ArrayList<>();
        f.add(new Field("Kind", r.kind() == Kind.AMMO ? "ammo" : "firearm"));
        if (!r.brand().isEmpty()) {
            f.add(new Field("Brand", r.brand()));
        }
        ModelMatch m = r.model();
        if (m != null) {
            f.add(new Field("Model", m.model()));
            if (!m.family().isEmpty() && !m.family().equals(m.model())) {
                f.add(new Field("Family", m.family()));
            }
            if (!m.generation().isEmpty()) {
                f.add(new Field("Generation", m.generation()));
            }
            if (!m.configs().isEmpty()) {
                f.add(new Field("Configuration", String.join(", ", m.configs())));
            }
            if (!m.features().isEmpty()) {
                f.add(new Field("Features", String.join(", ", m.features())));
            }
        }
        if (!r.calibers().isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Caliber c : r.calibers()) {
                names.add(Titles.caliberName(c, o.ascii()) + " (" + c.type() + ")");
            }
            f.add(new Field(names.size() == 1 ? "Caliber" : "Calibers", String.join(", ", names)));
        }
        if (r.capacity() != null) {
            f.add(new Field("Capacity", r.capacity() + " rounds"));
        }
        if (r.barrelLength() != null) {
            f.add(new Field("Barrel", Json.number(r.barrelLength()) + " in"));
        }
        if (r.rounds() != null) {
            f.add(new Field("Rounds per box", String.valueOf(r.rounds())));
        }
        if (t.price() != null) {
            f.add(new Field("Price", Prices.money(t.price())));
        }
        if (r.costPerRound() != null) {
            f.add(new Field("Cost per round", Prices.perRound(r.costPerRound(), true)));
        }
        return f;
    }

    /** "$289.99 for 1000 rounds = $0.29 (29.00¢) per round". */
    public static String costPerRoundText(double price, int rounds, double costPerRound) {
        return Prices.money(price) + " for " + rounds + " rounds = " + Prices.perRound(costPerRound, true)
                + " per round";
    }

    // -------------------------------------------------------------------------------------------------- JSON

    /**
     * The result as a map with the keys of the gunsnprices library's {@code ParseResult.toMap()} (the Python
     * package's {@code to_dict()}), plus {@code line} (when set) and {@code price}. Caliber {@code url}s are only
     * included when links are on.
     */
    public static Map<String, Object> toMap(ParsedTitle t, Options o) {
        ParseResult r = t.result();
        Map<String, Object> m = new LinkedHashMap<>();
        if (t.line() > 0) {
            m.put("line", t.line());
        }
        m.put("title", t.title());
        m.put("kind", r.kind().value());
        m.put("brand", r.brand());
        ModelMatch mm = r.model();
        if (mm == null) {
            m.put("model", null);
        } else {
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("brand", mm.brand());
            model.put("family", mm.family());
            model.put("model", mm.model());
            model.put("generation", mm.generation());
            model.put("configs", mm.configs());
            model.put("features", mm.features());
            model.put("source", mm.source());
            m.put("model", model);
        }
        m.put("caliber", r.caliber() == null ? null : caliberMap(r.caliber(), o));
        List<Object> calibers = new ArrayList<>();
        for (Caliber c : r.calibers()) {
            calibers.add(caliberMap(c, o));
        }
        m.put("calibers", calibers);
        m.put("capacity", r.capacity());
        m.put("barrel_length", r.barrelLength());
        m.put("rounds", r.rounds());
        m.put("price", t.price());
        m.put("cost_per_round", r.costPerRound());
        return m;
    }

    private static Map<String, Object> caliberMap(Caliber c, Options o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.id());
        m.put("name", Titles.caliberName(c, o.ascii()));
        m.put("type", c.type());
        if (o.links() && !c.url().isEmpty()) {
            m.put("url", c.url());
        }
        return m;
    }

    /** One result as indented JSON. */
    public static String toJson(ParsedTitle t, Options o) {
        return Json.write(toMap(t, o), 2) + "\n";
    }

    /** Several results as an indented JSON array. */
    public static String toJson(List<ParsedTitle> rows, Options o) {
        List<Object> list = new ArrayList<>();
        for (ParsedTitle t : rows) {
            list.add(toMap(t, o));
        }
        return Json.write(list, 2) + "\n";
    }

    // --------------------------------------------------------------------------------------------------- CSV

    /** The CSV columns; {@code caliber_url} is added when links are on. Same columns as the VS Code extension. */
    public static List<String> csvHeader(Options o) {
        List<String> head = new ArrayList<>(List.of("line", "title", "kind", "brand", "model", "family", "generation",
                "configs", "features", "caliber", "caliber_type", "calibers", "capacity", "barrel_in", "rounds", "price",
                "cost_per_round"));
        if (o.links()) {
            head.add("caliber_url");
        }
        return head;
    }

    /** CSV (RFC 4180, CRLF line ends); several values in one cell are separated by "; ". */
    public static String toCsv(List<ParsedTitle> rows, Options o) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", csvHeader(o))).append("\r\n");
        for (ParsedTitle t : rows) {
            ParseResult r = t.result();
            ModelMatch m = r.model();
            List<String> names = new ArrayList<>();
            for (Caliber c : r.calibers()) {
                names.add(Titles.caliberName(c, o.ascii()));
            }
            List<Object> cells = new ArrayList<>(List.of(
                    t.line(),
                    t.title(),
                    r.kind().value(),
                    r.brand(),
                    m == null ? "" : m.model(),
                    m == null ? "" : m.family(),
                    m == null ? "" : m.generation(),
                    m == null ? "" : String.join("; ", m.configs()),
                    m == null ? "" : String.join("; ", m.features()),
                    r.caliber() == null ? "" : Titles.caliberName(r.caliber(), o.ascii()),
                    r.caliber() == null ? "" : r.caliber().type(),
                    String.join("; ", names)));
            cells.add(r.capacity());
            cells.add(r.barrelLength());
            cells.add(r.rounds());
            cells.add(t.price());
            cells.add(r.costPerRound());
            if (o.links()) {
                cells.add(r.caliber() == null ? "" : r.caliber().url());
            }
            List<String> out = new ArrayList<>();
            for (Object c : cells) {
                out.add(csvField(c));
            }
            sb.append(String.join(",", out)).append("\r\n");
        }
        return sb.toString();
    }

    /** One CSV field, quoted when needed. */
    public static String csvField(Object v) {
        String s;
        if (v == null) {
            s = "";
        } else if (v instanceof Double d) {
            s = Json.number(d);
        } else {
            s = v.toString();
        }
        return CSV_QUOTE_NEEDED.matcher(s).find() ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
