package com.plot.plugin.building.ui;

import com.plot.plugin.ui.PluginTabScrollUi;
import imgui.ImGui;
import imgui.flag.ImGuiTabBarFlags;

/** 建筑 ImGui 界面编排。 */
public final class BuildingUIManager {
    private final BuildingUiContext ctx;
    private final BuildingToolbarPanel toolbarPanel;
    private final BuildingFootprintsPanel footprintsPanel;
    private final BuildingEditPanel editPanel;
    private final BuildingGeneratePanel generatePanel;
    private boolean footprintsTabOpenLastFrame;

    public BuildingUIManager(BuildingUiContext ctx) {
        this.ctx = ctx;
        this.toolbarPanel = new BuildingToolbarPanel(ctx);
        this.footprintsPanel = new BuildingFootprintsPanel(ctx);
        this.editPanel = new BuildingEditPanel(ctx);
        this.generatePanel = new BuildingGeneratePanel(ctx);
    }

    public void render() {
        ctx.tickGhostProjection();
        ctx.buildingRename().tickFrame();
        ctx.tickDistrictPreviewJob();
        ctx.dismissDistrictPreviewJobUi();

        if (ctx.pickSession().isActive()) {
            footprintsPanel.tickPickSession();
        }

        toolbarPanel.render();

        boolean footprintsTabOpen = false;
        if (ImGui.beginTabBar("##building_tabs", ImGuiTabBarFlags.None)) {
            footprintsTabOpen = PluginTabScrollUi.renderTab(
                "plugin.building.tab.footprints",
                "##building_tab_footprints",
                footprintsPanel::render);
            PluginTabScrollUi.renderTab(
                "plugin.building.tab.edit",
                "##building_tab_edit",
                editPanel::render);
            PluginTabScrollUi.renderTab(
                "plugin.building.tab.generate",
                "##building_tab_generate",
                generatePanel::render);
            ImGui.endTabBar();
        }

        if (footprintsTabOpenLastFrame && !footprintsTabOpen) {
            ctx.buildingRename().cancelActive();
        }
        footprintsTabOpenLastFrame = footprintsTabOpen;
    }

    public void renderDeferredModals() {
        footprintsPanel.renderDeleteConfirmPopup();
        generatePanel.renderBuildConfirmPopup();
    }
}
