package com.gunsnprices.ide;

import com.gunsnprices.GunsNPrices;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/** Settings | Tools | Gun &amp; Ammo Title Parser. */
public final class GunsNPricesConfigurable implements Configurable {
    private JBCheckBox links;
    private JBCheckBox ascii;
    private JPanel panel;

    @Override
    public @Nls String getDisplayName() {
        return "Gun & Ammo Title Parser";
    }

    @Override
    public @Nullable JComponent createComponent() {
        links = new JBCheckBox("Link calibers to their price comparison pages on GunsNPrices");
        ascii = new JBCheckBox("Write × as x in caliber names");
        JBLabel about = new JBLabel("Parser: gunsnprices " + GunsNPrices.VERSION
                + ". Model and caliber data: GunsNPrices, CC BY 4.0.");
        about.setForeground(UIUtil.getContextHelpForeground());
        about.setComponentStyle(UIUtil.ComponentStyle.SMALL);
        panel = FormBuilder.createFormBuilder()
                .addComponent(withComment(links, "Shows a link to the caliber's page in the result popup and in "
                        + "Caliber Lookup, and adds the page URL to JSON (url) and CSV (caliber_url). Pages open in "
                        + "your browser only when you click a link. Off by default."))
                .addComponent(withComment(ascii, "9x19mm Parabellum instead of 9×19mm Parabellum, for feeds and "
                        + "databases that expect plain ASCII. Applies to results, Caliber Lookup and Normalize "
                        + "Caliber."))
                .addVerticalGap(JBUI.scale(8))
                .addComponent(about)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        reset();
        return panel;
    }

    private static JComponent withComment(JComponent box, String comment) {
        JBLabel text = new JBLabel("<html>" + StringUtil.escapeXmlEntities(comment) + "</html>");
        text.setAllowAutoWrapping(true);
        text.setComponentStyle(UIUtil.ComponentStyle.SMALL);
        text.setForeground(UIUtil.getContextHelpForeground());
        text.setBorder(JBUI.Borders.empty(2, 24, 6, 0));
        JPanel p = new JPanel(new BorderLayout());
        p.add(box, BorderLayout.NORTH);
        p.add(text, BorderLayout.CENTER);
        return p;
    }

    @Override
    public boolean isModified() {
        GunsNPricesSettings s = GunsNPricesSettings.getInstance();
        return links.isSelected() != s.links() || ascii.isSelected() != s.asciiCaliberNames();
    }

    @Override
    public void apply() {
        GunsNPricesSettings s = GunsNPricesSettings.getInstance();
        s.setLinks(links.isSelected());
        s.setAsciiCaliberNames(ascii.isSelected());
        ApplicationManager.getApplication().getMessageBus()
                .syncPublisher(GunsNPricesSettings.CHANGED).settingsChanged();
    }

    @Override
    public void reset() {
        GunsNPricesSettings s = GunsNPricesSettings.getInstance();
        links.setSelected(s.links());
        ascii.setSelected(s.asciiCaliberNames());
    }

    @Override
    public void disposeUIResources() {
        panel = null;
        links = null;
        ascii = null;
    }
}
