package com.gunsnprices.ide.core;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TextSegmentsTest {

    @Test
    public void quotedStringsInCode() {
        String line = "String t = \"Glock 19 Gen 5 MOS 9mm 4.02\\\" 15rd\"; // \"comment\"";
        List<TextSegments.Segment> s = TextSegments.quotedSegments(line);
        assertEquals(2, s.size());
        assertEquals("Glock 19 Gen 5 MOS 9mm 4.02\" 15rd", s.get(0).text());
        assertEquals("comment", s.get(1).text());
        assertEquals(line.indexOf("Glock"), s.get(0).start());
    }

    @Test
    public void apostrophesAndInchMarksAreNotQuotes() {
        assertEquals(0, TextSegments.quotedSegments("Henry's Big Boy .44 Mag 20\" barrel").size());
        List<TextSegments.Segment> s = TextSegments.quotedSegments("titles = ['Henry Big Boy .44 Mag', 'Ruger 10/22']");
        assertEquals(2, s.size());
        assertEquals("Henry Big Boy .44 Mag", s.get(0).text());
        assertEquals("Ruger 10/22", s.get(1).text());
    }

    @Test
    public void stringPrefixesAndCsvQuotes() {
        assertEquals("Glock 19", TextSegments.quotedSegments("x = f\"Glock 19\"").get(0).text());
        List<TextSegments.Segment> s = TextSegments.quotedSegments("\"Glock 19 4.02\"\" 15rd\",499.99");
        assertEquals("Glock 19 4.02\" 15rd", s.get(0).text());
    }

    @Test
    public void segmentAtCaret() {
        String line = "{\"title\": \"Federal 9mm 50rd\", \"price\": 18.99}";
        int inside = line.indexOf("9mm");
        assertEquals("Federal 9mm 50rd", TextSegments.quotedSegmentAt(line, inside).text());
        // on the opening quote and just after the closing one
        int open = line.indexOf("\"Federal");
        assertEquals("Federal 9mm 50rd", TextSegments.quotedSegmentAt(line, open).text());
        assertEquals("Federal 9mm 50rd", TextSegments.quotedSegmentAt(line, line.indexOf("50rd\"") + 5).text());
        assertNull(TextSegments.quotedSegmentAt(line, line.indexOf("18.99")));
    }

    @Test
    public void csvCells() {
        String line = "PA195S203MOS,\"Glock 19 Gen 5 MOS 9mm 4.02\"\" 15rd\",Glock,$549.99";
        List<TextSegments.Segment> cells = TextSegments.cells(line, ',');
        assertEquals(4, cells.size());
        assertEquals("PA195S203MOS", cells.get(0).text());
        assertEquals("Glock 19 Gen 5 MOS 9mm 4.02\" 15rd", cells.get(1).text());
        assertEquals("$549.99", cells.get(3).text());
        assertEquals("Glock 19 Gen 5 MOS 9mm 4.02\" 15rd", TextSegments.cellAt(line, line.indexOf("MOS 9mm"), ',').text());
        assertEquals("Glock", TextSegments.cellAt(line, line.indexOf(",Glock,") + 2, ',').text());

        List<TextSegments.Segment> tsv = TextSegments.cells("a\tb c\t", '\t');
        assertEquals(3, tsv.size());
        assertEquals("b c", tsv.get(1).text());
        assertEquals("", tsv.get(2).text());
    }

    @Test
    public void listMarkersAndEscapes() {
        assertEquals("Glock 19", TextSegments.stripListMarker("  - Glock 19"));
        assertEquals("Glock 19", TextSegments.stripListMarker("12. Glock 19"));
        assertEquals("Glock 19", TextSegments.stripListMarker("• Glock 19"));
        assertEquals("9×19mm", TextSegments.unescape("9\\u00d719mm"));
        assertEquals("a \"b\" c\\d", TextSegments.unescape("a \\\"b\\\" c\\\\d"));
        assertEquals("plain", TextSegments.unescape("plain"));
    }
}
