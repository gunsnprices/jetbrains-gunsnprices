package com.gunsnprices.ide;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/** Short messages next to the caret. */
final class Feedback {
    private Feedback() {
    }

    private static volatile String lastMessage;

    static void info(@NotNull Editor editor, @NotNull String text) {
        lastMessage = text;
        if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
            HintManager.getInstance().showInformationHint(editor, text);
        }
    }

    static void error(@NotNull Editor editor, @NotNull String text) {
        lastMessage = text;
        if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
            HintManager.getInstance().showErrorHint(editor, text);
        }
    }

    @TestOnly
    static String lastMessage() {
        return lastMessage;
    }

    @TestOnly
    static void clear() {
        lastMessage = null;
    }
}
