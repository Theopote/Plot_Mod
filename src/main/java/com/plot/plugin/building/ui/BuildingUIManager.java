package com.plot.plugin.building.ui;

import com.plot.plugin.ui.PluginTabScrollUi;
import imgui.ImGui;
import imgui.flag.ImGuiTabBarFlags;
import imgui.flag.ImGuiWindowFlags;

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

        if (ctx.pickSession().isActive()) {
            footprintsPanel.tickPickSession();
        }

        // 延续中的检查任务：先推进一帧，生成 Tab 内进度条才能读到最新完成数。
        ctx.tickDistrictPreviewJob();

        toolbarPanel.render();

        float tabHeight = Math.max(80f, ImGui.getContentRegionAvailY());
        boolean footprintsTabOpen = false;
        if (ImGui.beginChild("##building_tab_area", 0, tabHeight, false, ImGuiWindowFlags.NoScrollbar)) {
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
            ImGui.endChild();
        }

        // 本帧在 Tab 内新启动的检查任务，在 Tab 渲染后再推进一帧。
        ctx.tickDistrictPreviewJob();

        ctx.dismissDistrictPreviewJobUi();

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
