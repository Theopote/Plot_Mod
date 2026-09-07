package com.plot.plugin.powerline.ui;

import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTabBarFlags;

/** 电力线路 ImGui 界面编排。 */
public final class PowerLineUIManager {
    private final PowerLineUiContext ctx;
    private final PowerLineToolbarPanel toolbarPanel;
    private final PowerLineOverviewPanel overviewPanel;
    private final PowerLineAdoptPanel adoptPanel;
    private final PowerLineEditPanel editPanel;
    private final PowerLineGeneratePanel generatePanel;

    public PowerLineUIManager(PowerLineUiContext ctx) {
        this.ctx = ctx;
        this.toolbarPanel = new PowerLineToolbarPanel(ctx);
        this.overviewPanel = new PowerLineOverviewPanel(ctx);
        this.adoptPanel = new PowerLineAdoptPanel(ctx);
        this.editPanel = new PowerLineEditPanel(ctx);
        this.generatePanel = new PowerLineGeneratePanel(ctx);
    }

    public void render() {
        toolbarPanel.render();
        if (ImGui.beginTabBar("##powerline_tabs", ImGuiTabBarFlags.None)) {
            renderTab("plugin.powerline.tab.overview", overviewPanel::render);
            renderTab("plugin.powerline.tab.adopt", adoptPanel::render);
            renderTab("plugin.powerline.tab.edit", editPanel::render);
            renderTab("plugin.powerline.tab.generate", generatePanel::render);
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
