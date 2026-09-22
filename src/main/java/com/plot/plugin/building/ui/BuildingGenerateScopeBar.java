package com.plot.plugin.building.ui;

import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;

/** Generate Tab 目标摘要与范围切换。 */
public final class BuildingGenerateScopeBar {
    private BuildingGenerateScopeBar() {
    }

    public static void render(BuildingUiContext ctx, List<BuildingFootprint> targets) {
        int count = targets.size();
        int minFloors = Integer.MAX_VALUE;
        int maxFloors = 0;
        int footprintBlocks = 0;
        var projection = ctx.currentProjection();
        for (BuildingFootprint building : targets) {
            minFloors = Math.min(minFloors, building.getFloors());
            maxFloors = Math.max(maxFloors, building.getFloors());
            footprintBlocks += ctx.blockCountCache().blockCount(building, projection);
        }
        if (minFloors == Integer.MAX_VALUE) {
            minFloors = 0;
        }

        ImGui.text(PlotI18n.tr("plugin.building.generate.target_summary", count));
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr(
                "plugin.building.generate.target_meta",
                footprintBlocks,
                minFloors,
                maxFloors));

        ImGui.sameLine(ImGui.getContentRegionAvailX() - 72f);
        if (ImGui.button(PlotI18n.tr("plugin.building.generate.change_scope"), 0, 0)) {
            ImGui.openPopup("##building_generate_scope");
        }
        renderScopePopup(ctx);
    }

    private static void renderScopePopup(BuildingUiContext ctx) {
        if (!ImGui.beginPopup("##building_generate_scope")) {
            return;
        }
        int selectedCount = ctx.selection().size();
        int allCount = ctx.project().getBuildingCount();
        boolean useAll = ctx.generateScopeAll();
        if (ImGui.selectable(
                PlotI18n.tr("plugin.building.generate.scope_selected", selectedCount),
                !useAll)) {
            ctx.setGenerateScopeAll(false);
            ImGui.closeCurrentPopup();
        }
        if (ImGui.selectable(
                PlotI18n.tr("plugin.building.generate.scope_all", allCount),
                useAll)) {
            ctx.setGenerateScopeAll(true);
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }
}
