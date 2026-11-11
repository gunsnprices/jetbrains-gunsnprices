package com.gunsnprices.ide;

import com.gunsnprices.GunsNPrices;
import com.gunsnprices.ide.core.Output;
import com.gunsnprices.ide.core.Prices;
import com.gunsnprices.ide.core.Titles;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.event.DocumentEvent;
import java.awt.datatransfer.StringSelection;
import java.util.Locale;
import java.util.OptionalDouble;

/** Asks for a price and a round count (or a title) and shows the cost per round as you type. */
final class CostPerRoundDialog extends DialogWrapper {
    private final JBTextField price = new JBTextField(12);
    private final JBTextField rounds = new JBTextField(44);
    private final JBLabel result = new JBLabel(" ");

    CostPerRoundDialog(@Nullable Project project, @NotNull String priceText, @NotNull String roundsText) {
        super(project);
        setTitle("Cost per Round");
        price.setText(priceText);
        rounds.setText(roundsText);
        price.getEmptyText().setText("18.99");
        rounds.getEmptyText().setText("50, or Federal American Eagle 9mm 115gr FMJ 50 Rounds");
        result.setFont(JBFont.label().asBold());
        DocumentAdapter update = new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                updateResult();
            }
        };
        price.getDocument().addDocumentListener(update);
        rounds.getDocument().addDocumentListener(update);
        setOKButtonText("Copy");
        setCancelButtonText("Close");
        init();
        initValidation();
        updateResult();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JBLabel hint = new JBLabel("Copy puts the cost per round (two decimals) on the clipboard.");
        hint.setForeground(UIUtil.getContextHelpForeground());
        hint.setComponentStyle(UIUtil.ComponentStyle.SMALL);
        var panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("Price of the box or case:", price)
                .addLabeledComponent("Rounds, or the product title:", rounds)
                .addVerticalGap(JBUI.scale(8))
                .addComponent(result)
                .addComponent(hint)
                .getPanel();
        panel.setPreferredSize(JBUI.size(560, -1).withHeight(panel.getPreferredSize().height));
        return panel;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return price.getText().isBlank() ? price : rounds;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (Prices.parsePrice(price.getText()) == null) {
            return new ValidationInfo("Enter a price, for example 18.99", price);
        }
        if (Titles.roundsFrom(rounds.getText()) == null) {
            return new ValidationInfo("Enter a number of rounds, or a title that has one (\"50 Rounds\", \"20/box\")",
                    rounds);
        }
        return null;
    }

    /** The cost per round for the current input, or null. */
    Double costPerRound() {
        Double p = Prices.parsePrice(price.getText());
        Integer n = Titles.roundsFrom(rounds.getText());
        if (p == null || n == null) {
            return null;
        }
        OptionalDouble cpr = GunsNPrices.costPerRound(p, n);
        return cpr.isPresent() ? cpr.getAsDouble() : null;
    }

    /** The result line, or a blank when the input is not complete. */
    String resultText() {
        Double p = Prices.parsePrice(price.getText());
        Integer n = Titles.roundsFrom(rounds.getText());
        Double cpr = costPerRound();
        return p == null || n == null || cpr == null ? " " : Output.costPerRoundText(p, n, cpr);
    }

    private void updateResult() {
        result.setText(resultText());
    }

    @Override
    protected void doOKAction() {
        Double cpr = costPerRound();
        if (cpr != null) {
            CopyPasteManager.getInstance().setContents(
                    new StringSelection(String.format(Locale.US, "%.2f", cpr)));
        }
        super.doOKAction();
    }
}
