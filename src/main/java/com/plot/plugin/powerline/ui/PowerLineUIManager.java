package com.plot.plugin.powerline.ui;

import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTabBarFlags;

/** 电力线路 ImGui 界面编排。 */
public final class PowerLineUIManager {
    private final PowerLineUiContext ctx;
    private final PowerLineToolbarPanel toolbarPanel;
    private final PowerLineOverviewPanel overviewPanel;
    private final PoleDesignerPanel poleDesignerPanel;
    private final PowerLineRoutePanel routePanel;
    private final PowerLineStylePanel stylePanel;
    private final PowerLineGeneratePanel generatePanel;
    private final PowerLineEngineeringPanel engineeringPanel;
    private final PowerLineBuildPanel buildPanel;

    public PowerLineUIManager(PowerLineUiContext ctx) {
        this.ctx = ctx;
        this.toolbarPanel = new PowerLineToolbarPanel(ctx);
        this.overviewPanel = new PowerLineOverviewPanel(ctx);
        this.poleDesignerPanel = new PoleDesignerPanel(ctx);
        this.routePanel = new PowerLineRoutePanel(ctx);
        this.stylePanel = new PowerLineStylePanel(ctx, poleDesignerPanel);
        this.generatePanel = new PowerLineGeneratePanel(ctx);
        this.engineeringPanel = new PowerLineEngineeringPanel(ctx);
        this.buildPanel = new PowerLineBuildPanel(ctx, generatePanel, engineeringPanel);
    }

    public void render() {
        toolbarPanel.render();
        if (ImGui.beginTabBar("##powerline_tabs", ImGuiTabBarFlags.None)) {
            renderTab("plugin.powerline.tab.overview", overviewPanel::render);
            renderTab("plugin.powerline.tab.route", routePanel::render);
            renderTab("plugin.powerline.tab.style", stylePanel::render);
            renderTab("plugin.powerline.tab.build", buildPanel::render);
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
        buildPanel.renderBuildConfirmPopup();
        buildPanel.renderOptimizationConfirmPopup();
        poleDesignerPanel.render();
    }
}
