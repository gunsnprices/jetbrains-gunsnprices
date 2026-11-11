package com.gunsnprices.ide;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/** Opens Caliber Lookup and searches for the selected text or the string literal at the caret. */
public final class CaliberLookupAction extends DumbAwareAction {
    private static final int MAX_QUERY = 60;

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        String query = editor == null ? null : EditorTargets.read(() -> {
            EditorTargets.Target t = EditorTargets.valueAt(editor, file, editor.getCaretModel().getOffset());
            return t == null || t.text().isBlank() || t.text().length() > MAX_QUERY || t.text().contains("\n")
                    ? null : t.text().strip();
        });
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(CaliberLookupToolWindowFactory.ID);
        if (toolWindow == null) {
            return;
        }
        toolWindow.activate(() -> {
            CaliberLookupPanel panel = CaliberLookupToolWindowFactory.panelOf(toolWindow);
            if (panel != null && query != null) {
                panel.setQuery(query);
            }
        });
    }
}
