package com.gunsnprices.ide.core;

import com.gunsnprices.GunsNPrices;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CaliberIndexTest {

    @Test
    public void loadsEveryCaliberWithStoreSpellings() {
        List<CaliberIndex.Entry> all = CaliberIndex.all();
        assertEquals(GunsNPrices.listCalibers().size(), all.size());
        assertEquals(293, all.size());
        // most listed first
        assertEquals("9x19mmparabellum", all.get(0).id());
        CaliberIndex.Entry nine = CaliberIndex.byId("9x19mmparabellum");
        assertNotNull(nine);
        assertTrue(nine.spellings().contains("9mm Luger (9x19)"));
        assertTrue(nine.url().startsWith("https://gunsnprices.com/"));
    }

    @Test
    public void searchPutsTheParsedCaliberFirst() {
        assertEquals(".300 AAC Blackout", first("300 BLK"));
        assertEquals("9×19mm Parabellum", first("9mm luger"));
        assertEquals("12 Gauge", first("12ga"));
        assertEquals("6.5mm Creedmoor", first("6.5 CM"));
        assertEquals(".30-06 Springfield", first("30-06 Cal"));
    }

    @Test
    public void searchMatchesPartsOfNamesAndSpellings() {
        List<CaliberIndex.Match> creed = CaliberIndex.search("creed");
        assertTrue(creed.size() >= 2);
        assertTrue(creed.stream().anyMatch(m -> m.entry().name().equals("6mm Creedmoor")));
        List<CaliberIndex.Match> wby = CaliberIndex.search("weatherby");
        assertTrue(wby.stream().anyMatch(m -> m.entry().name().equals(".300 Wby Mag")));
        // the spelling that matched is reported when it is not the display name
        CaliberIndex.Match m = CaliberIndex.search("9mm Luger (9x19)").get(0);
        assertEquals("9×19mm Parabellum", m.entry().name());
        assertEquals("9mm Luger (9x19)", m.matched());
        assertEquals(0, CaliberIndex.search("zzzz-not-a-caliber").size());
        assertEquals(293, CaliberIndex.search("  ").size());
    }

    @Test
    public void jsonReader() {
        Object v = Json.parse("{\"a\": [1, 2.5, \"x\\u00d7y\", true, null], \"b\": {}}");
        Map<?, ?> m = (Map<?, ?>) v;
        assertEquals(List.of(1.0, 2.5, "x×y", true), ((List<?>) m.get("a")).subList(0, 4));
        assertEquals("{\"a\": [1.0, 2.5, \"x×y\", true, null], \"b\": {}}", Json.write(v, 0));
        assertEquals("0.0001", Json.number(1e-4));
        assertEquals("15000000.0", Json.number(1.5e7));
        assertEquals("22.0", Json.number(22.0));
    }

    private static String first(String q) {
        return CaliberIndex.search(q).get(0).entry().name();
    }
}
