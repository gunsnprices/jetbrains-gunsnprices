package com.gunsnprices.ide;

import com.gunsnprices.ide.core.Output;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Alt+Enter on a string literal (or a selection, a CSV cell, a plain text line) that looks like a gun or ammo title.
 * Shows the parsed fields in the preview and, when invoked, in the result popup. Changes nothing in the file.
 */
public final class ParseTitleIntention implements IntentionAction, LowPriorityAction, DumbAware {

    @Override
    public @IntentionName @NotNull String getText() {
        return "Parse gun or ammo title";
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
        return "Parse gun or ammo title";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return editor != null && file != null && EditorTargets.titleForIntention(editor, file) != null;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
        if (editor == null || file == null) {
            return;
        }
        EditorTargets.Found found = EditorTargets.titleForIntention(editor, file);
        if (found != null) {
            ResultPopups.showTitle(project, editor, found.title());
        }
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor,
                                                         @NotNull PsiFile file) {
        EditorTargets.Found found = EditorTargets.titleForIntention(editor, file);
        if (found == null) {
            return IntentionPreviewInfo.EMPTY;
        }
        Output.Options o = GunsNPricesSettings.getInstance().outputOptions();
        HtmlBuilder b = new HtmlBuilder();
        b.append(HtmlChunk.text(Output.heading(found.title(), o)).bold()).br();
        for (Output.Field f : Output.fields(found.title(), o)) {
            b.append(f.label() + ": ").append(f.value()).br();
        }
        return new IntentionPreviewInfo.Html(b.toFragment());
    }
}
