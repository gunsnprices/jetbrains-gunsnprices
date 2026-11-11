package com.gunsnprices.ide.core;

import com.gunsnprices.Kind;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TitlesTest {

    @Test
    public void parsesFirearmTitle() {
        ParsedTitle t = Titles.parse("Glock 19 Gen 5 MOS 9mm 4.02\" 15rd Pistol PA195S203MOS");
        assertEquals(Kind.FIREARM, t.result().kind());
        assertEquals("Glock", t.result().brand());
        assertEquals("Glock 19 Gen 5 MOS", t.result().model().model());
        assertEquals("9×19mm Parabellum", t.result().caliber().name());
        assertEquals(Integer.valueOf(15), t.result().capacity());
        assertEquals(4.02, t.result().barrelLength(), 1e-9);
        assertTrue(Titles.looksLikeTitle(t.result(), t.title()));
    }

    @Test
    public void parsesAmmoWithPrice() {
        ParsedTitle t = Titles.parse("Winchester 9mm 115gr FMJ 1000 Round Case - $289.99");
        assertEquals(Kind.AMMO, t.result().kind());
        assertEquals("Winchester 9mm 115gr FMJ 1000 Round Case", t.title());
        assertEquals(289.99, t.price(), 1e-9);
        assertEquals(Integer.valueOf(1000), t.result().rounds());
        assertEquals(0.29, t.result().costPerRound(), 1e-4);
    }

    @Test
    public void contextPriceIsUsedWhenTheTitleHasNone() {
        ParsedTitle t = Titles.parse("Federal American Eagle 9mm Luger 115gr FMJ 50 Rounds", 18.99, 3);
        assertEquals(3, t.line());
        assertEquals(0.3798, t.result().costPerRound(), 1e-9);
    }

    @Test
    public void ignoresTextThatOnlyHasANumber() {
        ParsedTitle t = Titles.parse("call me at 308 tomorrow");
        assertFalse(Titles.looksLikeTitle(t.result(), t.title()));
        ParsedTitle u = Titles.parse("hello world");
        assertEquals(0, Titles.score(u.result()));
    }

    @Test
    public void caliberOnly() {
        assertEquals("9×19mm Parabellum", Titles.caliberOnly("9mm Luger").caliber().name());
        assertEquals("9×19mm Parabellum", Titles.caliberOnly("9x19").caliber().name());
        assertEquals("12 Gauge", Titles.caliberOnly("12ga").caliber().name());
        assertEquals(".300 AAC Blackout", Titles.caliberOnly("300 BLK").caliber().name());
        assertEquals("6.5mm Creedmoor", Titles.caliberOnly("6.5 CM").caliber().name());
        assertNull(Titles.caliberOnly("Glock 19 Gen 5 9mm").caliber());
        assertEquals(2, Titles.caliberOnly("9mm / .380").multiple().size());
        assertNull(Titles.caliberValue("380"));
        assertNotNull(Titles.caliberValue("380 ACP"));
    }

    @Test
    public void normalizesCaliberText() {
        Titles.NormalizeResult r = Titles.normalizeCaliberText("9mm Luger\n  \"5.56 NATO\"\nfoo\n9mm / .380\n", false);
        assertEquals("9×19mm Parabellum\n  \"5.56×45mm NATO\"\nfoo\n9mm / .380\n", r.text());
        assertEquals(2, r.changed().size());
        assertEquals("foo", r.unknown().get(0));
        assertEquals("9mm / .380", r.ambiguous().get(0));

        Titles.NormalizeResult ascii = Titles.normalizeCaliberText("9mm Luger", true);
        assertEquals("9x19mm Parabellum", ascii.text());

        Titles.NormalizeResult same = Titles.normalizeCaliberText("12 Gauge", false);
        assertEquals("12 Gauge", same.text());
        assertEquals(0, same.changed().size());
    }

    @Test
    public void roundsFrom() {
        assertEquals(Integer.valueOf(50), Titles.roundsFrom("50"));
        assertEquals(Integer.valueOf(1000), Titles.roundsFrom("1,000"));
        assertEquals(Integer.valueOf(20), Titles.roundsFrom("Winchester 308 150gr PowerMax Bonded 20rds"));
        assertEquals(Integer.valueOf(20), Titles.roundsFrom("20/box"));
        assertNull(Titles.roundsFrom("0"));
        assertNull(Titles.roundsFrom(""));
        assertNull(Titles.roundsFrom("no count here"));
    }

    @Test
    public void parsesLinesOfDifferentShapes() {
        ParsedTitle csv = Titles.parseLine("PA195S203MOS,\"Glock 19 Gen 5 MOS 9mm 4.02\"\" 15rd\",Glock,549.99", 2, ',');
        assertEquals("Glock 19 Gen 5 MOS 9mm 4.02\" 15rd", csv.title());
        assertEquals(549.99, csv.price(), 1e-9);
        assertEquals(2, csv.line());

        ParsedTitle json = Titles.parseLine("{\"title\": \"Federal American Eagle 9mm 115gr FMJ 50 Rounds\", "
                + "\"price\": 18.99}", 1, (char) 0);
        assertEquals("Federal American Eagle 9mm 115gr FMJ 50 Rounds", json.title());
        assertEquals(0.3798, json.result().costPerRound(), 1e-9);

        ParsedTitle code = Titles.parseLine("    \"Ruger 10/22 Carbine .22 LR 18.5\\\" 10rd\",", 5, (char) 0);
        assertEquals("Ruger 10/22 Carbine .22 LR 18.5\" 10rd", code.title());

        ParsedTitle list = Titles.parseLine("- Federal 308 150gr PowerShok 20rds $31.99", 7, (char) 0);
        assertEquals("Federal 308 150gr PowerShok 20rds", list.title());
        assertEquals(1.5995, list.result().costPerRound(), 1e-9);

        assertNull(Titles.parseLine("title,price", 1, ','));
        assertNull(Titles.parseLine("   ", 1, (char) 0));
    }
}
