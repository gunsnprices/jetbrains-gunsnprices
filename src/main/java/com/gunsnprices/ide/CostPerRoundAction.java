package com.gunsnprices.ide;

import com.gunsnprices.ide.core.ParsedTitle;
import com.gunsnprices.ide.core.Prices;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Cost per round from a price and a round count, or a price and a title. When the caret is on a title with a price,
 * both fields are filled in.
 */
public final class CostPerRoundAction extends DumbAwareAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        String[] prefill = editor == null ? new String[] {"", ""} : ReadAction.compute(() -> prefill(editor, file));
        new CostPerRoundDialog(e.getProject(), prefill[0], prefill[1]).show();
    }

    /** {price, rounds or title} from the title at the caret; empty strings when there is none. */
    static String[] prefill(@NotNull Editor editor, PsiFile file) {
        EditorTargets.Found found = EditorTargets.titleAt(editor, file);
        if (found == null) {
            return new String[] {"", ""};
        }
        ParsedTitle t = found.title();
        String price = t.price() == null ? "" : Prices.plain(t.price());
        String rounds = t.result().rounds() == null ? "" : t.title();
        return new String[] {price, rounds};
    }
}
