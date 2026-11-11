package com.gunsnprices.ide.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PricesTest {

    @Test
    public void parsesPrices() {
        assertEquals(18.99, Prices.parsePrice("18.99"), 1e-9);
        assertEquals(18.99, Prices.parsePrice("$18.99"), 1e-9);
        assertEquals(1299.99, Prices.parsePrice("$1,299.99"), 1e-9);
        assertEquals(18.99, Prices.parsePrice("USD 18.99"), 1e-9);
        assertEquals(18.99, Prices.parsePrice("18.99 usd"), 1e-9);
        assertNull(Prices.parsePrice("0"));
        assertNull(Prices.parsePrice("abc"));
        assertNull(Prices.parsePrice("-5"));
        assertNull(Prices.parsePrice(null));
    }

    @Test
    public void findsPriceInText() {
        Prices.InText p = Prices.findPriceInText("Federal American Eagle 9mm Luger 115gr FMJ 50 Rounds $18.99");
        assertEquals(18.99, p.price(), 1e-9);
        assertEquals("Federal American Eagle 9mm Luger 115gr FMJ 50 Rounds", p.rest());

        p = Prices.findPriceInText("Winchester 9mm 115gr FMJ 1000 Round Case - $289.99");
        assertEquals(289.99, p.price(), 1e-9);
        assertEquals("Winchester 9mm 115gr FMJ 1000 Round Case", p.rest());

        p = Prices.findPriceInText("Glock 19 Gen 5 (USD 549.00)");
        assertEquals(549.0, p.price(), 1e-9);
        assertEquals("Glock 19 Gen 5", p.rest());

        p = Prices.findPriceInText("Ruger 10/22 Carbine .22 LR 18.5\" 10rd");
        assertNull(p.price());
        assertEquals("Ruger 10/22 Carbine .22 LR 18.5\" 10rd", p.rest());
    }

    @Test
    public void priceCells() {
        assertTrue(Prices.isPriceCell("24.99"));
        assertTrue(Prices.isPriceCell("$24.99"));
        assertTrue(Prices.isPriceCell("$24"));
        assertTrue(Prices.isPriceCell("1,299.99"));
        assertFalse(Prices.isPriceCell("15"));
        assertFalse(Prices.isPriceCell("9mm"));
    }

    @Test
    public void priceInJsonLine() {
        assertEquals(18.99, Prices.priceInJsonLine("{\"title\": \"Federal 9mm 50rd\", \"price\": 18.99}"), 1e-9);
        assertEquals(18.99, Prices.priceInJsonLine("{\"sale_price\": \"$18.99\"}"), 1e-9);
        assertEquals(18.0, Prices.priceInJsonLine("price: 1, 'our_price': 18"), 1e-9);
        assertNull(Prices.priceInJsonLine("{\"title\": \"Federal 9mm 50rd\"}"));
    }

    @Test
    public void formats() {
        assertEquals("$1,299.99", Prices.money(1299.99));
        assertEquals("$0.38", Prices.perRound(0.3798, false));
        assertEquals("$0.38 (37.98¢)", Prices.perRound(0.3798, true));
        assertEquals("18.99", Prices.plain(18.99));
        assertEquals("549", Prices.plain(549.0));
    }
}
