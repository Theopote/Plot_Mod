package com.plot.plugin.building.ui;

import com.plot.utils.PlotI18n;
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
        ctx.tickDistrictPreviewJob();
        ctx.tickGhostProjection();
        ctx.buildingRename().tickFrame();

        if (ctx.pickSession().isActive()) {
            footprintsPanel.tickPickSession();
        }

        toolbarPanel.render();

        boolean footprintsTabOpen = false;
        if (ImGui.beginTabBar("##building_tabs", ImGuiTabBarFlags.None)) {
            footprintsTabOpen = renderTab("plugin.building.tab.footprints", footprintsPanel::render);
            renderTab("plugin.building.tab.edit", editPanel::render);
            renderTab("plugin.building.tab.generate", generatePanel::render);
            ImGui.endTabBar();
        }

        if (footprintsTabOpenLastFrame && !footprintsTabOpen) {
            ctx.buildingRename().cancelActive();
        }
        footprintsTabOpenLastFrame = footprintsTabOpen;
    }

    private static boolean renderTab(String labelKey, Runnable body) {
        if (ImGui.beginTabItem(PlotI18n.tr(labelKey))) {
            body.run();
            ImGui.endTabItem();
            return true;
        }
        return false;
    }

    public void renderDeferredModals() {
        footprintsPanel.renderDeleteConfirmPopup();
        generatePanel.renderBuildConfirmPopup();
    }
}
