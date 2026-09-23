package com.plot.plugin.powerline.ui;

import com.plot.plugin.ui.PluginTabScrollUi;
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

    public PowerLineUIManager(PowerLineUiContext ctx) {
        this.ctx = ctx;
        this.toolbarPanel = new PowerLineToolbarPanel(ctx);
        this.overviewPanel = new PowerLineOverviewPanel(ctx);
        this.poleDesignerPanel = new PoleDesignerPanel(ctx);
        this.routePanel = new PowerLineRoutePanel(ctx, overviewPanel);
        this.stylePanel = new PowerLineStylePanel(ctx, poleDesignerPanel);
        this.buildPanel = new PowerLineBuildPanel(ctx);
    }

    public void render() {
        ctx.state().tickPathPickActivationBlock();
        if (ctx.pathPickSession().isActive()) {
            ctx.actions().tickPathPickSession();
        }
        toolbarPanel.render();
        if (ImGui.beginTabBar("##powerline_tabs", ImGuiTabBarFlags.None)) {
            PluginTabScrollUi.renderTab(
                "plugin.powerline.tab.route",
                "##powerline_tab_route",
                routePanel::render);
            PluginTabScrollUi.renderTab(
                "plugin.powerline.tab.style",
                "##powerline_tab_style",
                stylePanel::render);
            PluginTabScrollUi.renderTab(
                "plugin.powerline.tab.build",
                "##powerline_tab_build",
                buildPanel::render);
            ImGui.endTabBar();
        }
    }

    public void renderDeferredModals() {
        routePanel.renderDeleteConfirmPopup();
        buildPanel.renderBuildConfirmPopup();
        poleDesignerPanel.render();
    }
}
