package com.gunsnprices.ide.core;

import com.gunsnprices.Caliber;
import com.gunsnprices.GunsNPrices;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The caliber list with the spellings stores use, read from the data file inside the gunsnprices library
 * ({@code com/gunsnprices/data/calibers.json}, CC BY 4.0, GunsNPrices).
 */
public final class CaliberIndex {
    private CaliberIndex() {
    }

    static final String DATA_FILE = "/com/gunsnprices/data/calibers.json";

    /**
     * One caliber.
     *
     * @param id       stable id, e.g. "9x19mmparabellum"
     * @param name     display name, e.g. "9×19mm Parabellum"
     * @param type     handgun, rifle, rimfire, shotgun, airgun or muzzleloader
     * @param url      GunsNPrices price comparison page, or ""
     * @param listings number of GunsNPrices listings with this caliber (used to sort)
     * @param spellings other names stores use, without the display name
     */
    public record Entry(String id, String name, String type, String url, long listings, List<String> spellings) {
    }

    /**
     * A search hit.
     *
     * @param entry   the caliber
     * @param matched the spelling that matched the query when it is not the display name, else null
     */
    public record Match(Entry entry, String matched) {
    }

    private static volatile List<Entry> entries;

    /** All calibers, most listed first. Loaded once. */
    public static List<Entry> all() {
        List<Entry> e = entries;
        if (e == null) {
            synchronized (CaliberIndex.class) {
                e = entries;
                if (e == null) {
                    e = load();
                    entries = e;
                }
            }
        }
        return e;
    }

    /** The entry with this id, or null. */
    public static Entry byId(String id) {
        for (Entry e : all()) {
            if (e.id().equals(id)) {
                return e;
            }
        }
        return null;
    }

    private static List<Entry> load() {
        Map<String, Entry> out = new LinkedHashMap<>();
        try (InputStream in = GunsNPrices.class.getResourceAsStream(DATA_FILE)) {
            if (in != null) {
                Object root = Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                if (root instanceof Map<?, ?> m && m.get("calibers") instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof Map<?, ?> c && c.get("id") instanceof String id
                                && c.get("name") instanceof String name) {
                            Set<String> spellings = new LinkedHashSet<>();
                            if (c.get("site_name") instanceof String site) {
                                spellings.add(site);
                            }
                            if (c.get("aliases") instanceof List<?> aliases) {
                                for (Object a : aliases) {
                                    if (a instanceof String s) {
                                        spellings.add(s);
                                    }
                                }
                            }
                            spellings.remove(name);
                            String type = c.get("type") instanceof String t ? t : "";
                            String url = c.get("url") instanceof String u ? u : "";
                            long n = c.get("n") instanceof Number num ? num.longValue() : 0;
                            out.put(id, new Entry(id, name, type, url, n, List.copyOf(spellings)));
                        }
                    }
                }
            }
        } catch (IOException | IllegalArgumentException ignored) {
            // fall back to the list without spellings below
        }
        // the library's list is the reference: add anything the data file did not give us
        for (Caliber c : GunsNPrices.listCalibers()) {
            out.putIfAbsent(c.id(), new Entry(c.id(), c.name(), c.type(), c.url(), 0, List.of()));
        }
        List<Entry> list = new ArrayList<>(out.values());
        list.sort(Comparator.comparingLong(Entry::listings).reversed().thenComparing(Entry::name));
        return List.copyOf(list);
    }

    /**
     * Searches by display name, id and store spellings. The caliber that the parser reads from the query
     * ("300 BLK", "6.5 CM", "12ga") comes first. An empty query returns every caliber, most listed first.
     */
    public static List<Match> search(String query) {
        String q = norm(query);
        List<Match> out = new ArrayList<>();
        if (q.isEmpty()) {
            for (Entry e : all()) {
                out.add(new Match(e, null));
            }
            return out;
        }
        Map<String, Integer> rank = new LinkedHashMap<>();
        Map<String, String> matched = new LinkedHashMap<>();
        Optional<Caliber> parsed = GunsNPrices.normalizeCaliber(query.trim());
        parsed.ifPresent(c -> {
            rank.put(c.id(), 0);
            if (!norm(c.name()).equals(q)) {
                matched.put(c.id(), query.trim());
            }
        });
        for (Caliber c : GunsNPrices.findCalibers(query)) {
            rank.putIfAbsent(c.id(), 1);
        }
        String qc = compact(q);
        for (Entry e : all()) {
            int best = Integer.MAX_VALUE;
            String bestSpelling = null;
            List<String> names = new ArrayList<>();
            names.add(e.name());
            names.addAll(e.spellings());
            for (String s : names) {
                int r = rank(norm(s), q, qc);
                if (r < best) {
                    best = r;
                    bestSpelling = s;
                }
            }
            if (best == Integer.MAX_VALUE && !qc.isEmpty() && e.id().contains(qc)) {
                best = 7;
            }
            if (best == Integer.MAX_VALUE) {
                continue;
            }
            Integer prev = rank.get(e.id());
            if (prev == null || best < prev) {
                rank.put(e.id(), best);
            }
            if (bestSpelling != null && !bestSpelling.equals(e.name())) {
                matched.putIfAbsent(e.id(), bestSpelling);
            }
        }
        List<Entry> hits = new ArrayList<>();
        for (Entry e : all()) {
            if (rank.containsKey(e.id())) {
                hits.add(e);
            }
        }
        hits.sort(Comparator.<Entry>comparingInt(e -> rank.get(e.id()))
                .thenComparing(Comparator.comparingLong(Entry::listings).reversed())
                .thenComparing(Entry::name));
        for (Entry e : hits) {
            out.add(new Match(e, matched.get(e.id())));
        }
        return out;
    }

    // 2 = same text, 3 = starts with, 4 = a word starts with, 5 = contains, 6 = contains without spaces and dots
    private static int rank(String s, String q, String qc) {
        if (s.equals(q)) {
            return 2;
        }
        if (s.startsWith(q)) {
            return 3;
        }
        int at = s.indexOf(q);
        if (at > 0) {
            char before = s.charAt(at - 1);
            return Character.isLetterOrDigit(before) ? 5 : 4;
        }
        if (!qc.isEmpty() && compact(s).contains(qc)) {
            return 6;
        }
        return Integer.MAX_VALUE;
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replace('×', 'x').replaceAll("\\s+", " ").trim();
    }

    private static String compact(String s) {
        return s.replaceAll("[\\s.\\-_/]", "");
    }
}
