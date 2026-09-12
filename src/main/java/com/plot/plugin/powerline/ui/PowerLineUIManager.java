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
    private final PowerLineBuildPanel buildPanel;
    private final PlacedSingleTowerPanel placedSingleTowerPanel;

    public PowerLineUIManager(PowerLineUiContext ctx) {
        this.ctx = ctx;
        this.toolbarPanel = new PowerLineToolbarPanel(ctx);
        this.overviewPanel = new PowerLineOverviewPanel(ctx);
        this.poleDesignerPanel = new PoleDesignerPanel(ctx);
        this.placedSingleTowerPanel = new PlacedSingleTowerPanel(ctx, ctx.placedSingleTowerActions());
        this.routePanel = new PowerLineRoutePanel(ctx, overviewPanel, placedSingleTowerPanel);
        this.stylePanel = new PowerLineStylePanel(ctx, poleDesignerPanel);
        PowerLineValidationPanel validationPanel = new PowerLineValidationPanel(ctx);
        this.buildPanel = new PowerLineBuildPanel(ctx, validationPanel);
    }

    public void render() {
        toolbarPanel.render();
        if (ImGui.beginTabBar("##powerline_tabs", ImGuiTabBarFlags.None)) {
            renderTab("plugin.powerline.tab.route", routePanel::render);
            renderTab("plugin.powerline.tab.style", stylePanel::render);
            renderTab("plugin.powerline.tab.build", buildPanel::render);
            ImGui.endTabBar();
        }
        ctx.singleTowerPlacement().tick();
    }

    private static void renderTab(String labelKey, Runnable body) {
        if (ImGui.beginTabItem(PlotI18n.tr(labelKey))) {
            body.run();
            ImGui.endTabItem();
        }
    }

    public void renderDeferredModals() {
        routePanel.renderDeleteConfirmPopup();
        placedSingleTowerPanel.renderDeleteConfirmPopup();
        buildPanel.renderBuildConfirmPopup();
        buildPanel.renderOptimizationConfirmPopup();
        poleDesignerPanel.render();
    }
}
