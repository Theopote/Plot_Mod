package com.plot.plugin.building.ui;

import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTabBarFlags;

/** 建筑 ImGui 界面编排。 */
public final class BuildingUIManager {
    private final BuildingUiContext ctx;
    private final BuildingToolbarPanel toolbarPanel;
    private final BuildingOverviewPanel overviewPanel;
    private final BuildingAdoptPanel adoptPanel;
    private final BuildingEditPanel editPanel;
    private final BuildingGeneratePanel generatePanel;

    public BuildingUIManager(BuildingUiContext ctx) {
        this.ctx = ctx;
        this.toolbarPanel = new BuildingToolbarPanel(ctx);
        this.overviewPanel = new BuildingOverviewPanel(ctx);
        this.adoptPanel = new BuildingAdoptPanel(ctx);
        this.editPanel = new BuildingEditPanel(ctx);
        this.generatePanel = new BuildingGeneratePanel(ctx);
    }

    public void render() {
        if (ctx.pickSession().isActive()) {
            adoptPanel.tickPickSession();
        }

        toolbarPanel.render();

        if (ImGui.beginTabBar("##building_tabs", ImGuiTabBarFlags.None)) {
            renderTab("plugin.building.tab.overview", overviewPanel::render);
            renderTab("plugin.building.tab.adopt", adoptPanel::render);
            renderTab("plugin.building.tab.edit", editPanel::render);
            renderTab("plugin.building.tab.generate", generatePanel::render);
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
