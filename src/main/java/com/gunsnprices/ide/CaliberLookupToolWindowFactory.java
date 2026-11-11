package com.gunsnprices.ide;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Creates the Caliber Lookup tool window. */
public final class CaliberLookupToolWindowFactory implements ToolWindowFactory, DumbAware {
    /** The tool window id, as in plugin.xml. */
    public static final String ID = "Caliber Lookup";

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        CaliberLookupPanel panel = new CaliberLookupPanel(project);
        Content content = ContentFactory.getInstance().createContent(panel, null, false);
        content.setDisposer(panel);
        content.setPreferredFocusableComponent(panel.searchField().getTextEditor());
        toolWindow.getContentManager().addContent(content);
    }

    /** The panel of an opened Caliber Lookup tool window, or null. */
    static @Nullable CaliberLookupPanel panelOf(@NotNull ToolWindow toolWindow) {
        for (Content c : toolWindow.getContentManager().getContents()) {
            if (c.getComponent() instanceof CaliberLookupPanel p) {
                return p;
            }
        }
        return null;
    }
}
