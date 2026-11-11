package com.gunsnprices.ide;

import com.gunsnprices.ide.core.CaliberIndex;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.BrowserLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.datatransfer.StringSelection;
import java.util.List;

/** The Caliber Lookup tool window: search the caliber list by name and by the spellings stores use. */
public final class CaliberLookupPanel extends JPanel implements Disposable {
    private final Project project;
    private final SearchTextField search = new SearchTextField(true);
    private final JBLabel count = new JBLabel();
    private final CollectionListModel<CaliberIndex.Match> model = new CollectionListModel<>();
    private final JBList<CaliberIndex.Match> list = new JBList<>(model);
    private final JPanel details = new JPanel(new BorderLayout());
    private volatile boolean loaded;
    private boolean disposed;

    public CaliberLookupPanel(@NotNull Project project) {
        super(new BorderLayout());
        this.project = project;

        search.getTextEditor().getEmptyText().setText("9mm Luger, 300 BLK, 6.5 CM, 12ga...");
        search.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                refresh();
            }
        });
        count.setForeground(UIUtil.getContextHelpForeground());
        count.setComponentStyle(UIUtil.ComponentStyle.SMALL);
        JPanel top = new JPanel(new BorderLayout());
        top.add(search, BorderLayout.CENTER);
        top.add(count, BorderLayout.SOUTH);
        top.setBorder(JBUI.Borders.empty(6, 6, 4, 6));

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new ColoredListCellRenderer<>() {
            @Override
            protected void customizeCellRenderer(@NotNull JList<? extends CaliberIndex.Match> l,
                                                 CaliberIndex.Match m, int index, boolean selected,
                                                 boolean focused) {
                append(name(m.entry()));
                append("  " + m.entry().type(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
                if (m.matched() != null) {
                    append("  \"" + m.matched() + "\"", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
                }
            }
        });
        list.getEmptyText().setText("Loading calibers...");
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showDetails(list.getSelectedValue());
            }
        });

        OnePixelSplitter splitter = new OnePixelSplitter(true, 0.6f);
        splitter.setFirstComponent(new JBScrollPane(list));
        JBScrollPane detailsScroll = new JBScrollPane(details);
        detailsScroll.setBorder(BorderFactory.createEmptyBorder());
        splitter.setSecondComponent(detailsScroll);

        add(top, BorderLayout.NORTH);
        add(splitter, BorderLayout.CENTER);
        showDetails(null);

        ApplicationManager.getApplication().getMessageBus().connect(this)
                .subscribe(GunsNPricesSettings.CHANGED, this::refresh);

        // the parser loads its data (about 0.2 s) on first use: do that off the UI thread
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            CaliberIndex.search("9mm");
            loaded = true;
            ApplicationManager.getApplication().invokeLater(this::refresh);
        });
    }

    /** The search field, focused when the tool window opens. */
    public @NotNull SearchTextField searchField() {
        return search;
    }

    /** Puts {@code query} into the search field and selects the best match. */
    public void setQuery(@NotNull String query) {
        search.setText(query.strip());
        refresh();
    }

    private void refresh() {
        if (disposed || !loaded) {
            return;
        }
        String q = search.getText();
        CaliberIndex.Match selected = list.getSelectedValue();
        List<CaliberIndex.Match> hits = CaliberIndex.search(q);
        model.replaceAll(hits);
        list.getEmptyText().setText("No caliber matches \"" + q.strip() + "\"");
        int total = CaliberIndex.all().size();
        count.setText(q.isBlank() ? total + " calibers, most listed first" : hits.size() + " of " + total + " calibers");
        int index = 0;
        if (selected != null && q.isBlank()) {
            for (int k = 0; k < hits.size(); k++) {
                if (hits.get(k).entry().id().equals(selected.entry().id())) {
                    index = k;
                    break;
                }
            }
        }
        if (!hits.isEmpty()) {
            list.setSelectedIndex(index);
            list.ensureIndexIsVisible(index);
        } else {
            showDetails(null);
        }
    }

    private void showDetails(CaliberIndex.Match m) {
        details.removeAll();
        if (m == null) {
            JBLabel hint = new JBLabel("Type a caliber name or the way a store writes it.");
            hint.setForeground(UIUtil.getContextHelpForeground());
            hint.setBorder(JBUI.Borders.empty(8));
            details.add(hint, BorderLayout.NORTH);
        } else {
            CaliberIndex.Entry e = m.entry();
            GunsNPricesSettings settings = GunsNPricesSettings.getInstance();
            JBLabel name = new JBLabel(name(e));
            name.setFont(JBFont.label().asBold().biggerOn(1));
            name.setCopyable(true);
            JBTextArea spellings = new JBTextArea(e.spellings().isEmpty() ? "-" : String.join(", ", e.spellings()));
            spellings.setEditable(false);
            spellings.setLineWrap(true);
            spellings.setWrapStyleWord(true);
            spellings.setOpaque(false);
            spellings.setBorder(BorderFactory.createEmptyBorder());
            spellings.setFont(JBFont.label());

            JButton copy = new JButton("Copy Name");
            copy.addActionListener(ev -> CopyPasteManager.getInstance().setContents(new StringSelection(name(e))));
            JButton insert = new JButton("Insert into Editor");
            insert.setToolTipText("Replace the selection in the current editor, or insert at the caret");
            insert.addActionListener(ev -> insert(name(e)));
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            buttons.add(copy);
            buttons.add(insert);

            FormBuilder form = FormBuilder.createFormBuilder()
                    .addComponent(name)
                    .addLabeledComponent("Type:", copyable(e.type()))
                    .addLabeledComponent("Id:", copyable(e.id()))
                    .addLabeledComponent("Store spellings:", spellings, true);
            if (settings.links() && !e.url().isEmpty()) {
                form.addComponent(new BrowserLink("Compare " + name(e) + " prices on GunsNPrices", e.url()));
            }
            form.addVerticalGap(JBUI.scale(4)).addComponent(buttons);
            JPanel p = form.getPanel();
            p.setBorder(JBUI.Borders.empty(8));
            details.add(p, BorderLayout.NORTH);
        }
        details.revalidate();
        details.repaint();
    }

    private static JBLabel copyable(String text) {
        JBLabel l = new JBLabel(text);
        l.setCopyable(true);
        return l;
    }

    private static String name(CaliberIndex.Entry e) {
        return GunsNPricesSettings.getInstance().asciiCaliberNames() ? e.name().replace('×', 'x') : e.name();
    }

    private void insert(String text) {
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) {
            count.setText("Open a file in the editor first.");
            return;
        }
        if (!EditorModificationUtil.checkModificationAllowed(editor) || !EditorModificationUtil.requestWriting(editor)) {
            return;
        }
        Document doc = editor.getDocument();
        WriteCommandAction.runWriteCommandAction(project, "Insert Caliber", null, () -> {
            SelectionModel sel = editor.getSelectionModel();
            int start = sel.hasSelection() ? sel.getSelectionStart() : editor.getCaretModel().getOffset();
            int end = sel.hasSelection() ? sel.getSelectionEnd() : start;
            doc.replaceString(start, end, text);
            sel.removeSelection();
            editor.getCaretModel().moveToOffset(start + text.length());
        });
        IdeFocusManager.getInstance(project).requestFocus(editor.getContentComponent(), true);
    }

    @TestOnly
    List<CaliberIndex.Match> shownForTests() {
        return model.getItems();
    }

    @TestOnly
    void loadForTests() {
        CaliberIndex.all();
        loaded = true;
        refresh();
    }

    @Override
    public void dispose() {
        disposed = true;
    }
}
