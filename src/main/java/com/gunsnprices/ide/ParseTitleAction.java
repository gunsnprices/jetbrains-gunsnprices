package com.gunsnprices.ide;

import com.gunsnprices.ide.core.ParsedTitle;
import com.gunsnprices.ide.core.Titles;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the selected text, the string literal at the caret or the current line and shows the result. Several
 * selected lines are parsed one by one into a table.
 */
public final class ParseTitleAction extends DumbAwareAction {
    /** Selections with more lines than this are parsed with a progress bar. */
    static final int LARGE_SELECTION = 2000;

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null && e.getData(CommonDataKeys.EDITOR) != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        if (project == null || editor == null) {
            return;
        }
        if (EditorTargets.hasMultiLineSelection(editor)) {
            parseLines(project, editor, file);
            return;
        }
        EditorTargets.Found found = ReadAction.compute(() -> EditorTargets.titleAt(editor, file));
        if (found == null) {
            Feedback.info(editor, "No gun or ammo title here. Select a title, or put the caret in a string or "
                    + "on a line that holds one.");
            return;
        }
        if (Titles.score(found.title().result()) == 0) {
            Feedback.info(editor, "Nothing recognized in the selected text.");
            return;
        }
        ResultPopups.showTitle(project, editor, found.title());
    }

    private static void parseLines(@NotNull Project project, @NotNull Editor editor, PsiFile file) {
        Document doc = editor.getDocument();
        int[] range = EditorTargets.selectedLineRange(editor);
        char delimiter = EditorTargets.delimiterFor(file);
        List<String> lines = new ArrayList<>();
        for (int l = range[0]; l <= range[1]; l++) {
            lines.add(doc.getText(new TextRange(doc.getLineStartOffset(l), doc.getLineEndOffset(l))));
        }
        int firstLine = range[0] + 1;
        List<ParsedTitle> rows = new ArrayList<>();
        int[] nonBlank = {0};
        Runnable parse = () -> {
            for (int k = 0; k < lines.size(); k++) {
                if (k % 100 == 0) {
                    ProgressManager.checkCanceled();
                }
                String line = lines.get(k);
                if (line.isBlank()) {
                    continue;
                }
                nonBlank[0]++;
                ParsedTitle t = Titles.parseLine(line, firstLine + k, delimiter);
                if (t != null) {
                    rows.add(t);
                }
            }
        };
        if (lines.size() > LARGE_SELECTION) {
            boolean done = ProgressManager.getInstance().runProcessWithProgressSynchronously(parse,
                    "Parsing " + lines.size() + " lines", true, project);
            if (!done) {
                return;
            }
        } else {
            parse.run();
        }
        if (rows.isEmpty()) {
            Feedback.info(editor, "No gun or ammo titles found in the selected lines.");
            return;
        }
        ResultPopups.showTable(project, editor, rows, nonBlank[0]);
    }
}
