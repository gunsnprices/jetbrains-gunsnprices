package com.gunsnprices.ide;

import com.gunsnprices.ide.core.Titles;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Replaces caliber text with its standard name ("9mm Luger" with "9×19mm Parabellum"). Works on every selection (one
 * value per line) or, without a selection, on the string literal at each caret. Text that is not recognized, or that
 * holds more than a caliber, is left as it is.
 */
public final class NormalizeCaliberAction extends DumbAwareAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        e.getPresentation().setEnabledAndVisible(e.getProject() != null && editor != null);
        if (editor != null && !editor.getDocument().isWritable()) {
            e.getPresentation().setEnabled(false);
        }
    }

    /** What was done, for the message and for tests. */
    record Summary(List<String[]> changed, List<String> unknown, List<String> ambiguous) {
        String message() {
            List<String> parts = new ArrayList<>();
            if (changed.size() == 1) {
                parts.add(changed.get(0)[0] + " → " + changed.get(0)[1] + ".");
            } else if (changed.size() > 1) {
                parts.add("Normalized " + changed.size() + " calibers.");
            }
            if (!unknown.isEmpty()) {
                parts.add("Not recognized, left as is: " + quoteList(unknown) + ".");
            }
            if (!ambiguous.isEmpty()) {
                parts.add("More than one caliber, or more than a caliber, left as is: " + quoteList(ambiguous)
                        + ". Select just the caliber.");
            }
            if (parts.isEmpty()) {
                parts.add("Already normalized.");
            }
            return String.join(" ", parts);
        }
    }

    private record Edit(TextRange range, String text) {
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        if (project == null || editor == null) {
            return;
        }
        boolean ascii = GunsNPricesSettings.getInstance().asciiCaliberNames();
        Summary summary = new Summary(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        List<Edit> edits = new ArrayList<>();
        List<EditorTargets.Target> targets = ReadAction.compute(() -> targets(editor, file));
        if (targets.isEmpty()) {
            Feedback.info(editor, "Select the caliber text to normalize, for example 9mm Luger.");
            return;
        }
        Document doc = editor.getDocument();
        for (EditorTargets.Target t : targets) {
            String raw = doc.getText(t.range());
            if (!raw.equals(t.text())) {
                // a string literal with escapes: leave it alone rather than write it back wrong
                summary.unknown().add(t.text());
                continue;
            }
            Titles.NormalizeResult r = Titles.normalizeCaliberText(raw, ascii);
            summary.changed().addAll(r.changed());
            summary.unknown().addAll(r.unknown());
            summary.ambiguous().addAll(r.ambiguous());
            if (!r.changed().isEmpty()) {
                edits.add(new Edit(t.range(), r.text()));
            }
        }
        if (!edits.isEmpty()) {
            if (!EditorModificationUtil.checkModificationAllowed(editor) || !EditorModificationUtil.requestWriting(editor)) {
                return;
            }
            edits.sort(Comparator.comparingInt((Edit x) -> x.range().getStartOffset()).reversed());
            WriteCommandAction.runWriteCommandAction(project, "Normalize Caliber", null, () -> {
                for (Edit x : edits) {
                    doc.replaceString(x.range().getStartOffset(), x.range().getEndOffset(), x.text());
                }
            }, file == null ? new PsiFile[0] : new PsiFile[] {file});
        }
        Feedback.info(editor, summary.message());
    }

    /** One target per caret: its selection, or the string literal at it. Overlapping targets are dropped. */
    private static List<EditorTargets.Target> targets(@NotNull Editor editor, PsiFile file) {
        List<EditorTargets.Target> out = new ArrayList<>();
        boolean anySelection = false;
        for (Caret caret : editor.getCaretModel().getAllCarets()) {
            anySelection |= caret.hasSelection();
        }
        for (Caret caret : editor.getCaretModel().getAllCarets()) {
            EditorTargets.Target t;
            if (caret.hasSelection()) {
                TextRange range = new TextRange(caret.getSelectionStart(), caret.getSelectionEnd());
                t = new EditorTargets.Target(EditorTargets.Source.SELECTION, range,
                        editor.getDocument().getText(range), null, 0);
            } else if (!anySelection) {
                t = EditorTargets.valueAt(editor, file, caret.getOffset());
            } else {
                continue;
            }
            if (t != null && out.stream().noneMatch(o -> o.range().intersects(t.range()))) {
                out.add(t);
            }
        }
        return out;
    }

    private static String quoteList(List<String> values) {
        List<String> shown = new ArrayList<>();
        for (String v : values.subList(0, Math.min(5, values.size()))) {
            shown.add("\"" + (v.length() > 40 ? v.substring(0, 37) + "..." : v) + "\"");
        }
        return String.join(", ", shown) + (values.size() > 5 ? " and " + (values.size() - 5) + " more" : "");
    }
}
