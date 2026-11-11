package com.gunsnprices.ide;

import com.gunsnprices.ide.core.CaliberIndex;
import com.gunsnprices.ide.core.ParsedTitle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import javax.swing.JButton;
import javax.swing.JComponent;
import java.util.List;

/**
 * Headless tests in a real IntelliJ Platform: the plugin descriptor loads, the actions and the intention are
 * registered and work on editor text.
 */
public class PluginSmokeTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ResultPopups.clear();
        Feedback.clear();
        GunsNPricesSettings.getInstance().loadState(new GunsNPricesSettings.State());
    }

    public void testDescriptorAndRegistrations() {
        IdeaPluginDescriptor d = PluginManagerCore.getPlugin(PluginId.getId("com.gunsnprices.gun-title-parser"));
        assertNotNull(d);
        assertEquals("Gun & Ammo Title Parser", d.getName());
        assertEquals("GunsNPrices", d.getVendor());
        ActionManager am = ActionManager.getInstance();
        for (String id : List.of("GunsNPrices.Menu", "GunsNPrices.ParseTitle", "GunsNPrices.CostPerRound",
                "GunsNPrices.NormalizeCaliber", "GunsNPrices.CaliberLookup")) {
            assertNotNull(id, am.getAction(id));
        }
    }

    public void testParseTitleOnPlainTextLine() {
        myFixture.configureByText("titles.txt", "Glock 19 Gen 5 MOS 9mm 4.02\" 15rd Pis<caret>tol PA195S203MOS\n");
        myFixture.performEditorAction("GunsNPrices.ParseTitle");
        ParsedTitle t = (ParsedTitle) ResultPopups.lastShown();
        assertNotNull(t);
        assertEquals("Glock 19 Gen 5 MOS", t.result().model().model());
        assertEquals(Integer.valueOf(15), t.result().capacity());
    }

    public void testParseTitleInJsonStringUsesThePriceOnTheLine() {
        myFixture.configureByText("feed.json",
                "[{\"title\": \"Federal American Eagle 9mm Luger 115gr FMJ <caret>50 Rounds\", \"price\": 18.99}]");
        myFixture.performEditorAction("GunsNPrices.ParseTitle");
        ParsedTitle t = (ParsedTitle) ResultPopups.lastShown();
        assertNotNull(t);
        assertEquals("Federal American Eagle 9mm Luger 115gr FMJ 50 Rounds", t.title());
        assertEquals(0.3798, t.result().costPerRound(), 1e-9);
    }

    public void testParseTitleInCodeStringWithEscapes() {
        myFixture.configureByText("Titles.java",
                "class Titles { String t = \"Ruger 10/22 Carbine .22 LR 18.5\\\" 1<caret>0rd\"; }");
        myFixture.performEditorAction("GunsNPrices.ParseTitle");
        ParsedTitle t = (ParsedTitle) ResultPopups.lastShown();
        assertNotNull(t);
        assertEquals("Ruger 10/22 Carbine .22 LR 18.5\" 10rd", t.title());
        assertEquals(18.5, t.result().barrelLength(), 1e-9);
    }

    public void testNothingFound() {
        myFixture.configureByText("notes.txt", "just some <caret>words\n");
        myFixture.performEditorAction("GunsNPrices.ParseTitle");
        assertNull(ResultPopups.lastShown());
        assertTrue(Feedback.lastMessage().startsWith("No gun or ammo title here"));
    }

    @SuppressWarnings("unchecked")
    public void testSeveralSelectedLinesGiveATable() {
        myFixture.configureByText("feed.csv", """
                sku,title,price
                <selection>PA195S203MOS,"Glock 19 Gen 5 MOS 9mm 4.02"" 15rd",549.99
                AE9DP,Federal American Eagle 9mm Luger 115gr FMJ 50 Rounds,18.99

                X1,gift card,25.00
                </selection>""");
        myFixture.performEditorAction("GunsNPrices.ParseTitle");
        List<ParsedTitle> rows = (List<ParsedTitle>) ResultPopups.lastShown();
        assertNotNull(rows);
        assertEquals(2, rows.size());
        assertEquals(2, rows.get(0).line());
        assertEquals(549.99, rows.get(0).price(), 1e-9);
        assertEquals(0.3798, rows.get(1).result().costPerRound(), 1e-9);
    }

    public void testIntentionIsOfferedOnTitlesOnly() {
        myFixture.configureByText("feed.json", "{\"title\": \"Sig Sauer P365 XL 9mm 3.7\\\" 12+1 Optic <caret>Ready\"}");
        IntentionAction action = myFixture.getAvailableIntention("Parse gun or ammo title");
        assertNotNull(action);
        IntentionPreviewInfo preview = EditorTargets.read(() ->
                action.generatePreview(getProject(), myFixture.getEditor(), myFixture.getFile()));
        assertTrue(preview instanceof IntentionPreviewInfo.Html);
        String html = ((IntentionPreviewInfo.Html) preview).content().toString();
        assertTrue(html, html.contains("Sig Sauer P365 XL"));
        assertTrue(html, html.contains("Capacity: 12 rounds"));
        myFixture.launchAction(action);
        assertEquals("Sig Sauer P365 XL", ((ParsedTitle) ResultPopups.lastShown()).result().model().model());

        myFixture.configureByText("other.json", "{\"note\": \"call me at 308 <caret>tomorrow\"}");
        assertNull(myFixture.getAvailableIntention("Parse gun or ammo title"));
        myFixture.configureByText("Code.java", "class Code { int glock19<caret> = 9; }");
        assertNull(myFixture.getAvailableIntention("Parse gun or ammo title"));
    }

    public void testNormalizeSelectedCalibers() {
        myFixture.configureByText("calibers.txt", "<selection>9mm Luger\n12ga\nfoo\n300 BLK</selection>\n");
        myFixture.performEditorAction("GunsNPrices.NormalizeCaliber");
        myFixture.checkResult("9×19mm Parabellum\n12 Gauge\nfoo\n.300 AAC Blackout\n");
        assertEquals("Normalized 3 calibers. Not recognized, left as is: \"foo\".", Feedback.lastMessage());
    }

    public void testNormalizeCaliberInStringAtCaretWithAsciiSetting() {
        GunsNPricesSettings.getInstance().setAsciiCaliberNames(true);
        myFixture.configureByText("item.json", "{\"caliber\": \"9mm Lu<caret>ger\", \"title\": \"x\"}");
        myFixture.performEditorAction("GunsNPrices.NormalizeCaliber");
        myFixture.checkResult("{\"caliber\": \"9x19mm Parabellum\", \"title\": \"x\"}");
        assertEquals("9mm Luger → 9x19mm Parabellum.", Feedback.lastMessage());
    }

    public void testCostPerRoundPrefill() {
        myFixture.configureByText("t.txt", "Winchester 9mm 115gr FMJ 1000 Round <caret>Case - $289.99\n");
        String[] prefill = EditorTargets.read(() -> CostPerRoundAction.prefill(myFixture.getEditor(),
                myFixture.getFile()));
        assertEquals("289.99", prefill[0]);
        assertEquals("Winchester 9mm 115gr FMJ 1000 Round Case", prefill[1]);
    }

    public void testCostPerRoundDialog() {
        CostPerRoundDialog dialog = new CostPerRoundDialog(getProject(), "289.99", "1000rd case");
        try {
            assertEquals(0.29, dialog.costPerRound(), 1e-4);
            assertEquals("$289.99 for 1000 rounds = $0.29 (29.00¢) per round", dialog.resultText());
        } finally {
            Disposer.dispose(dialog.getDisposable());
        }
    }

    public void testResultPopupContent() {
        ParsedTitle t = com.gunsnprices.ide.core.Titles.parse("Glock 19 Gen 5 MOS 9mm 4.02\" 15rd");
        JComponent panel = ResultPopups.titlePanel(t, new com.gunsnprices.ide.core.Output.Options(true, false),
                new JButton("Copy as JSON"));
        assertTrue(panel.getComponentCount() > 5);
    }

    public void testCaliberLookupPanel() {
        CaliberLookupPanel panel = new CaliberLookupPanel(getProject());
        try {
            panel.loadForTests();
            assertEquals(CaliberIndex.all().size(), panel.shownForTests().size());
            panel.setQuery("300 blk");
            assertEquals(".300 AAC Blackout", panel.shownForTests().get(0).entry().name());
        } finally {
            Disposer.dispose(panel);
        }
    }

    public void testSettingsPage() {
        GunsNPricesConfigurable c = new GunsNPricesConfigurable();
        try {
            assertNotNull(c.createComponent());
            assertFalse(c.isModified());
            GunsNPricesSettings.getInstance().setLinks(true);
            assertTrue(c.isModified());
            c.reset();
            assertFalse(c.isModified());
        } finally {
            c.disposeUIResources();
        }
        assertFalse("links are off by default", new GunsNPricesSettings.State().links);
    }
}
