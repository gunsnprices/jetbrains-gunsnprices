package com.gunsnprices.ide;

import com.gunsnprices.ide.core.ParsedTitle;
import com.gunsnprices.ide.core.Prices;
import com.gunsnprices.ide.core.TextSegments;
import com.gunsnprices.ide.core.Titles;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Finds the text to parse in an editor: the selection, the string literal at the caret (from the language's PSI when
 * it has one, else a quoted string on the line), the CSV/TSV cell at the caret, or the current line. Callers hold a
 * read action.
 */
public final class EditorTargets {
    private EditorTargets() {
    }

    /** Where the text came from. */
    public enum Source { SELECTION, LITERAL, CELL, LINE }

    /**
     * Text to parse.
     *
     * @param source       where it came from
     * @param range        the raw text in the document (for a literal: between the quotes, escapes included)
     * @param text         the text, with quotes and escapes resolved
     * @param contextPrice a price from the same CSV row or JSON object, or null
     * @param line         one-based line number
     */
    public record Target(Source source, TextRange range, String text, Double contextPrice, int line) {
    }

    /** A target and what the parser read from it. */
    public record Found(Target target, ParsedTitle title) {
    }

    private static final int MAX_LINE = 4000;

    /** The text to parse for an action: the first candidate with a recognized title, or the selection. */
    public static @Nullable Found titleAt(@NotNull Editor editor, @Nullable PsiFile file) {
        for (Target t : candidates(editor, file, editor.getCaretModel().getOffset(), true)) {
            if (t.text().isBlank() || t.text().length() > Titles.MAX_TITLE_LENGTH) {
                continue;
            }
            ParsedTitle p = Titles.parse(t.text(), t.contextPrice(), 0);
            if (t.source() == Source.SELECTION || Titles.score(p.result()) > 0) {
                return new Found(t, p);
            }
        }
        return null;
    }

    /**
     * The text for the intention: a one-line selection, a string literal or a CSV cell (and, in plain text files, the
     * line) that looks like a gun or ammo title. Null when there is none, so the intention is not offered.
     */
    public static @Nullable Found titleForIntention(@NotNull Editor editor, @NotNull PsiFile file) {
        SelectionModel sel = editor.getSelectionModel();
        if (sel.hasSelection()) {
            String s = sel.getSelectedText();
            if (s == null || s.strip().contains("\n")) {
                return null;
            }
        }
        for (Target t : candidates(editor, file, editor.getCaretModel().getOffset(), isPlainTextLike(file))) {
            String text = t.text().strip();
            if (text.length() < 4 || text.length() > Titles.MAX_TITLE_LENGTH) {
                continue;
            }
            ParsedTitle p = Titles.parse(text, t.contextPrice(), 0);
            if (Titles.looksLikeTitle(p.result(), p.title())) {
                return new Found(t, p);
            }
        }
        return null;
    }

    /** The selection, or the string literal at {@code offset}: the value for Normalize Caliber and Caliber Lookup. */
    public static @Nullable Target valueAt(@NotNull Editor editor, @Nullable PsiFile file, int offset) {
        for (Target t : candidates(editor, file, offset, false)) {
            if (t.source() == Source.SELECTION || t.source() == Source.LITERAL) {
                return t;
            }
        }
        return null;
    }

    /** Candidates in the order they are tried. The selection, when there is one, is the only candidate. */
    public static List<Target> candidates(@NotNull Editor editor, @Nullable PsiFile file, int offset,
                                          boolean includeLine) {
        List<Target> out = new ArrayList<>();
        Document doc = editor.getDocument();
        SelectionModel sel = editor.getSelectionModel();
        int lineIndex = doc.getLineNumber(Math.min(offset, doc.getTextLength()));
        if (sel.hasSelection() && sel.getSelectionStart() <= offset && offset <= sel.getSelectionEnd()) {
            TextRange range = new TextRange(sel.getSelectionStart(), sel.getSelectionEnd());
            out.add(new Target(Source.SELECTION, range, doc.getText(range), null, lineIndex + 1));
            return out;
        }
        int lineStart = doc.getLineStartOffset(lineIndex);
        int lineEnd = doc.getLineEndOffset(lineIndex);
        if (lineEnd - lineStart > MAX_LINE) {
            return out;
        }
        String line = doc.getText(new TextRange(lineStart, lineEnd));
        int col = offset - lineStart;
        char delimiter = delimiterFor(file);
        Double rowPrice = rowPrice(line, delimiter);

        Target literal = file == null ? null : psiLiteral(file, doc, offset, rowPrice);
        if (literal == null) {
            TextSegments.Segment q = TextSegments.quotedSegmentAt(line, col);
            if (q != null) {
                literal = new Target(Source.LITERAL, new TextRange(lineStart + q.start(), lineStart + q.end()),
                        q.text(), rowPrice, lineIndex + 1);
            }
        }
        if (literal != null) {
            out.add(literal);
        }
        if (delimiter != 0) {
            TextSegments.Segment c = TextSegments.cellAt(line, col, delimiter);
            if (c != null && !c.text().isEmpty()) {
                out.add(new Target(Source.CELL, new TextRange(lineStart + c.start(), lineStart + c.end()), c.text(),
                        rowPrice, lineIndex + 1));
            }
        }
        if (includeLine) {
            String text = TextSegments.stripListMarker(line);
            if (!text.isEmpty()) {
                int start = lineStart + line.indexOf(text.charAt(0));
                out.add(new Target(Source.LINE, new TextRange(Math.max(lineStart, start), lineEnd), text, null,
                        lineIndex + 1));
            }
        }
        return out;
    }

    /** A price in another cell of the CSV row, or in a "price" property on the line. */
    private static Double rowPrice(String line, char delimiter) {
        if (delimiter != 0 && line.indexOf(delimiter) >= 0) {
            for (TextSegments.Segment c : TextSegments.cells(line, delimiter)) {
                if (Prices.isPriceCell(c.text())) {
                    return Prices.parsePrice(c.text());
                }
            }
        }
        return Prices.priceInJsonLine(line);
    }

    private static @Nullable Target psiLiteral(@NotNull PsiFile file, @NotNull Document doc, int offset,
                                               Double rowPrice) {
        if (!PsiDocumentManager.getInstance(file.getProject()).isCommitted(doc)
                || file.getTextLength() != doc.getTextLength()) {
            return null;
        }
        for (int at : new int[] {offset, offset - 1}) {
            if (at < 0 || at >= file.getTextLength()) {
                continue;
            }
            PsiElement el = file.findElementAt(at);
            for (int depth = 0; el != null && !(el instanceof PsiFile) && depth < 4; depth++, el = el.getParent()) {
                if (el instanceof PsiLanguageInjectionHost host && !(el instanceof PsiComment) && host.isValidHost()
                        && ElementManipulators.getManipulator(el) != null) {
                    TextRange value = ElementManipulators.getValueTextRange(el)
                            .shiftRight(el.getTextRange().getStartOffset());
                    if (value.isEmpty() || value.getLength() > Titles.MAX_TITLE_LENGTH
                            || value.getEndOffset() > doc.getTextLength()) {
                        return null;
                    }
                    String raw = doc.getText(value);
                    return new Target(Source.LITERAL, value, TextSegments.unescape(raw), rowPrice,
                            doc.getLineNumber(value.getStartOffset()) + 1);
                }
            }
        }
        return null;
    }

    /** ',' for .csv files, tab for .tsv/.tab files, 0 for anything else. */
    public static char delimiterFor(@Nullable PsiFile file) {
        String ext = extension(file);
        return switch (ext) {
            case "csv" -> ',';
            case "tsv", "tab" -> '\t';
            default -> 0;
        };
    }

    /** Plain text, CSV, TSV and Markdown files, where a whole line can be a title. */
    public static boolean isPlainTextLike(@Nullable PsiFile file) {
        if (file == null) {
            return true;
        }
        if (file.getFileType() == PlainTextFileType.INSTANCE) {
            return true;
        }
        return switch (extension(file)) {
            case "txt", "text", "csv", "tsv", "tab", "md", "markdown", "list" -> true;
            default -> false;
        };
    }

    private static String extension(@Nullable PsiFile file) {
        if (file == null) {
            return "";
        }
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Lines covered by the selection, as [first, last] zero-based line numbers; a selection ending at column 0
     * does not include that line. */
    public static int[] selectedLineRange(@NotNull Editor editor) {
        Document doc = editor.getDocument();
        SelectionModel sel = editor.getSelectionModel();
        int first = doc.getLineNumber(sel.getSelectionStart());
        int last = doc.getLineNumber(sel.getSelectionEnd());
        if (last > first && sel.getSelectionEnd() == doc.getLineStartOffset(last)) {
            last--;
        }
        return new int[] {first, last};
    }

    /** True when the selection spans more than one line. */
    public static boolean hasMultiLineSelection(@NotNull Editor editor) {
        if (!editor.getSelectionModel().hasSelection()) {
            return false;
        }
        int[] r = selectedLineRange(editor);
        return r[1] > r[0];
    }
}
