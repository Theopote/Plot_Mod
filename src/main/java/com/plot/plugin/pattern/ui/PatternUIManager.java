package com.plot.plugin.pattern.ui;

import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTabBarFlags;

/** 图案 ImGui 界面编排。 */
public final class PatternUIManager {
    private final PatternUiContext ctx;
    private final PatternToolbarPanel toolbarPanel;
    private final PatternOverviewPanel overviewPanel;
    private final PatternAdoptPanel adoptPanel;
    private final PatternEditPanel editPanel;
    private final PatternGeneratePanel generatePanel;

    public PatternUIManager(PatternUiContext ctx) {
        this.ctx = ctx;
        this.toolbarPanel = new PatternToolbarPanel(ctx);
        this.overviewPanel = new PatternOverviewPanel(ctx);
        this.adoptPanel = new PatternAdoptPanel(ctx);
        this.editPanel = new PatternEditPanel(ctx);
        this.generatePanel = new PatternGeneratePanel(ctx);
    }

    public void render() {
        if (ctx.pickSession().isActive()) {
            adoptPanel.tickPickSession();
        }

        toolbarPanel.render();

        if (ImGui.beginTabBar("##pattern_tabs", ImGuiTabBarFlags.None)) {
            renderTab("plugin.pattern.tab.overview", overviewPanel::render);
            renderTab("plugin.pattern.tab.adopt", adoptPanel::render);
            renderTab("plugin.pattern.tab.edit", editPanel::render);
            renderTab("plugin.pattern.tab.generate", generatePanel::render);
            ImGui.endTabBar();
        }
    }

    private static void renderTab(String labelKey, Runnable body) {
        if (ImGui.beginTabItem(PlotI18n.tr(labelKey))) {
            body.run();
            ImGui.endTabItem();
        }
    }

    public void renderDeferredModals() {
        overviewPanel.renderDeleteConfirmPopup();
        generatePanel.renderBuildConfirmPopup();
    }
}
