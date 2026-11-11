package com.gunsnprices.ide.core;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OutputTest {
    private static final ParsedTitle GLOCK = Titles.parse("Glock 19 Gen 5 MOS 9mm 4.02\" 15rd Pistol PA195S203MOS");
    private static final ParsedTitle FEDERAL =
            Titles.parse("Federal American Eagle 9mm Luger 115gr FMJ 50 Rounds $18.99", null, 2);

    @Test
    public void jsonHasTheLibraryKeysAndThePrice() {
        Map<String, Object> m = Output.toMap(FEDERAL, Output.Options.DEFAULT);
        assertEquals(List.of("line", "title", "kind", "brand", "model", "caliber", "calibers", "capacity",
                "barrel_length", "rounds", "price", "cost_per_round"), List.copyOf(m.keySet()));
        String json = Output.toJson(FEDERAL, Output.Options.DEFAULT);
        assertTrue(json.contains("\"kind\": \"ammo\""));
        assertTrue(json.contains("\"rounds\": 50"));
        assertTrue(json.contains("\"price\": 18.99"));
        assertTrue(json.contains("\"cost_per_round\": 0.3798"));
        assertFalse("no URLs unless links are on", json.contains("gunsnprices.com"));
        // valid JSON that reads back
        Object back = Json.parse(json);
        assertEquals("ammo", ((Map<?, ?>) back).get("kind"));
    }

    @Test
    public void linksAndAsciiOptions() {
        String json = Output.toJson(GLOCK, new Output.Options(true, true));
        assertTrue(json.contains("\"url\": \"https://gunsnprices.com/"));
        assertTrue(json.contains("\"name\": \"9x19mm Parabellum\""));
        assertFalse(json.contains("×"));
        assertFalse(Output.toMap(GLOCK, Output.Options.DEFAULT).containsKey("line"));
    }

    @Test
    public void popupFields() {
        List<Output.Field> f = Output.fields(GLOCK, Output.Options.DEFAULT);
        assertEquals("Glock 19 Gen 5 MOS", Output.heading(GLOCK, Output.Options.DEFAULT));
        assertTrue(f.contains(new Output.Field("Brand", "Glock")));
        assertTrue(f.contains(new Output.Field("Family", "Glock 19")));
        assertTrue(f.contains(new Output.Field("Generation", "5")));
        assertTrue(f.contains(new Output.Field("Caliber", "9×19mm Parabellum (handgun)")));
        assertTrue(f.contains(new Output.Field("Capacity", "15 rounds")));
        assertTrue(f.contains(new Output.Field("Barrel", "4.02 in")));

        assertEquals("Ammo · 9×19mm Parabellum · 50 rounds", Output.heading(FEDERAL, Output.Options.DEFAULT));
        List<Output.Field> a = Output.fields(FEDERAL, Output.Options.DEFAULT);
        assertTrue(a.contains(new Output.Field("Price", "$18.99")));
        assertTrue(a.contains(new Output.Field("Cost per round", "$0.38 (37.98¢)")));
        assertEquals("$289.99 for 1000 rounds = $0.29 (29.00¢) per round",
                Output.costPerRoundText(289.99, 1000, 0.28999));
    }

    @Test
    public void csv() {
        String csv = Output.toCsv(List.of(GLOCK, FEDERAL), Output.Options.DEFAULT);
        String[] lines = csv.split("\r\n");
        assertEquals("line,title,kind,brand,model,family,generation,configs,features,caliber,caliber_type,calibers,"
                + "capacity,barrel_in,rounds,price,cost_per_round", lines[0]);
        assertEquals("0,\"Glock 19 Gen 5 MOS 9mm 4.02\"\" 15rd Pistol PA195S203MOS\",firearm,Glock,Glock 19 Gen 5 MOS,"
                + "Glock 19,5,MOS,,9×19mm Parabellum,handgun,9×19mm Parabellum,15,4.02,,,", lines[1]);
        assertEquals("2,Federal American Eagle 9mm Luger 115gr FMJ 50 Rounds,ammo,,,,,,,9×19mm Parabellum,handgun,"
                + "9×19mm Parabellum,,,50,18.99,0.3798", lines[2]);
        assertTrue(Output.toCsv(List.of(GLOCK), new Output.Options(true, false)).startsWith(
                String.join(",", Output.csvHeader(new Output.Options(true, false)))));
        assertEquals("caliber_url", Output.csvHeader(new Output.Options(true, false)).get(17));
    }
}
