package com.gunsnprices.ide;

import com.gunsnprices.Caliber;
import com.gunsnprices.ide.core.Json;
import com.gunsnprices.ide.core.Output;
import com.gunsnprices.ide.core.ParsedTitle;
import com.gunsnprices.ide.core.Prices;
import com.gunsnprices.ide.core.Titles;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.components.BrowserLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;

/** The popups that show parse results. */
final class ResultPopups {
    private ResultPopups() {
    }

    private static volatile Object lastShown;

    /** Shows one result next to the caret. */
    static void showTitle(@NotNull Project project, @NotNull Editor editor, @NotNull ParsedTitle t) {
        Output.Options o = GunsNPricesSettings.getInstance().outputOptions();
        String json = Output.toJson(t, o);
        JButton copy = new JButton("Copy as JSON");
        JComponent panel = titlePanel(t, o, copy);
        if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
            lastShown = t;
            return;
        }
        JBPopup popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(panel, copy)
                .setTitle("Gun & Ammo Title")
                .setProject(project)
                .setMovable(true)
                .setResizable(true)
                .setRequestFocus(true)
                .setCancelOnClickOutside(true)
                .setCancelKeyEnabled(true)
                .createPopup();
        copy.addActionListener(e -> {
            CopyPasteManager.getInstance().setContents(new StringSelection(json));
            popup.cancel();
            Feedback.info(editor, "Copied the result as JSON");
        });
        popup.showInBestPositionFor(editor);
    }

    /** The content of the result popup: heading, title, fields, optional caliber links and the copy button. */
    static JComponent titlePanel(@NotNull ParsedTitle t, @NotNull Output.Options o, @NotNull JButton copy) {
        JBLabel heading = new JBLabel(Output.heading(t, o));
        heading.setFont(JBFont.label().asBold().biggerOn(1));
        String shown = t.title().length() > 110 ? t.title().substring(0, 107) + "..." : t.title();
        JBLabel title = new JBLabel(shown);
        title.setToolTipText(t.title());
        title.setForeground(UIUtil.getContextHelpForeground());

        FormBuilder form = FormBuilder.createFormBuilder()
                .addComponent(heading)
                .addComponent(title)
                .addVerticalGap(JBUI.scale(6));
        for (Output.Field f : Output.fields(t, o)) {
            JBLabel value = new JBLabel(f.value());
            value.setCopyable(true);
            form.addLabeledComponent(f.label() + ":", value);
        }
        if (o.links()) {
            for (Caliber c : t.result().calibers()) {
                if (!c.url().isEmpty()) {
                    String name = Titles.caliberName(c, o.ascii());
                    form.addComponent(new BrowserLink("Compare " + name + " prices on GunsNPrices", c.url()));
                }
            }
        }
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.add(copy);
        form.addVerticalGap(JBUI.scale(6)).addComponent(buttons);

        JPanel panel = form.getPanel();
        panel.setBorder(JBUI.Borders.empty(10, 12));
        return panel;
    }

    private static final String[] COLUMNS = {
            "Line", "Title", "Kind", "Brand", "Model", "Caliber", "Capacity", "Barrel, in", "Rounds", "Price",
            "Per round"};

    /** Shows the results of several lines as a table. */
    static void showTable(@NotNull Project project, @NotNull Editor editor, @NotNull List<ParsedTitle> rows,
                          int linesRead) {
        Output.Options o = GunsNPricesSettings.getInstance().outputOptions();
        JButton json = new JButton("Copy as JSON");
        JButton csv = new JButton("Copy as CSV");
        JComponent panel = tablePanel(rows, linesRead, o, json, csv);
        if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
            lastShown = rows;
            return;
        }
        JBPopup popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(panel, json)
                .setTitle("Gun & Ammo Titles")
                .setProject(project)
                .setMovable(true)
                .setResizable(true)
                .setRequestFocus(true)
                .setCancelOnClickOutside(true)
                .setCancelKeyEnabled(true)
                .setDimensionServiceKey(project, "GunsNPrices.ResultsTable", false)
                .createPopup();
        json.addActionListener(e -> {
            CopyPasteManager.getInstance().setContents(new StringSelection(Output.toJson(rows, o)));
            popup.cancel();
            Feedback.info(editor, "Copied " + rows.size() + " results as JSON");
        });
        csv.addActionListener(e -> {
            CopyPasteManager.getInstance().setContents(new StringSelection(Output.toCsv(rows, o)));
            popup.cancel();
            Feedback.info(editor, "Copied " + rows.size() + " results as CSV");
        });
        popup.showInBestPositionFor(editor);
    }

    static JComponent tablePanel(@NotNull List<ParsedTitle> rows, int linesRead, @NotNull Output.Options o,
                                 @NotNull JButton json, @NotNull JButton csv) {
        DefaultTableModel model = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (ParsedTitle t : rows) {
            var r = t.result();
            List<String> calibers = new ArrayList<>();
            for (Caliber c : r.calibers()) {
                calibers.add(Titles.caliberName(c, o.ascii()));
            }
            model.addRow(new Object[] {
                    t.line(),
                    t.title(),
                    r.kind().value(),
                    r.brand(),
                    r.model() == null ? "" : r.model().model(),
                    String.join(", ", calibers),
                    r.capacity() == null ? "" : r.capacity(),
                    r.barrelLength() == null ? "" : Json.number(r.barrelLength()),
                    r.rounds() == null ? "" : r.rounds(),
                    t.price() == null ? "" : Prices.money(t.price()),
                    r.costPerRound() == null ? "" : Prices.perRound(r.costPerRound(), false)});
        }
        JBTable table = new JBTable(model);
        table.setShowGrid(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(JBUI.scale(40));
        table.getColumnModel().getColumn(1).setPreferredWidth(JBUI.scale(330));
        table.getColumnModel().getColumn(4).setPreferredWidth(JBUI.scale(160));
        table.getColumnModel().getColumn(5).setPreferredWidth(JBUI.scale(140));
        JBScrollPane scroll = new JBScrollPane(table);
        scroll.setPreferredSize(JBUI.size(1000, Math.min(420, 40 + rows.size() * 24)));

        int skipped = linesRead - rows.size();
        String summary = "Parsed " + rows.size() + (rows.size() == 1 ? " title" : " titles")
                + (skipped > 0 ? "; " + skipped + (skipped == 1 ? " line" : " lines") + " without a title skipped." : ".");
        JBLabel status = new JBLabel(summary);
        status.setForeground(UIUtil.getContextHelpForeground());

        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.X_AXIS));
        bottom.add(status);
        bottom.add(javax.swing.Box.createHorizontalGlue());
        bottom.add(json);
        bottom.add(javax.swing.Box.createHorizontalStrut(JBUI.scale(6)));
        bottom.add(csv);
        bottom.setBorder(JBUI.Borders.emptyTop(8));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        panel.setBorder(JBUI.Borders.empty(8, 10));
        return panel;
    }

    @TestOnly
    static Object lastShown() {
        return lastShown;
    }

    @TestOnly
    static void clear() {
        lastShown = null;
    }
}
