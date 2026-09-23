package com.plot.plugin.building.ui;

import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;

/** Generate Tab 目标范围下拉与摘要。 */
public final class BuildingGenerateScopeBar {
    private BuildingGenerateScopeBar() {
    }

    public static void render(BuildingUiContext ctx, List<BuildingFootprint> targets) {
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

        int selectedCount = ctx.selection().size();
        int allCount = ctx.project().getBuildingCount();
        boolean useAll = ctx.generateScopeAll();
        String preview = useAll
            ? PlotI18n.tr("plugin.building.generate.scope_all", allCount)
            : PlotI18n.tr("plugin.building.generate.scope_selected", selectedCount);

        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.beginCombo("##building_generate_scope", preview)) {
            if (ImGui.selectable(
                    PlotI18n.tr("plugin.building.generate.scope_selected", selectedCount),
                    !useAll)) {
                ctx.setGenerateScopeAll(false);
            }
            if (ImGui.selectable(
                    PlotI18n.tr("plugin.building.generate.scope_all", allCount),
                    useAll)) {
                ctx.setGenerateScopeAll(true);
            }
            ImGui.endCombo();
        }

        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr(
                "plugin.building.generate.target_meta",
                footprintBlocks,
                minFloors,
                maxFloors));
    }
}
