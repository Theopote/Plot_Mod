package com.plot.plugin.building.ui;

import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;

/** Generate Tab 建造区：Ghost 状态 + 建造按钮。 */
public final class BuildingBuildAction {
    private BuildingBuildAction() {
    }

    public static void render(BuildingUiContext ctx, List<BuildingFootprint> targets) {
        ImGui.separator();
        boolean ghostVisible = ctx.hasPreviewResult()
            && ctx.previewValidity(targets) == BuildingPreviewIdentity.Validity.VALID;
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            ghostVisible
                ? PlotI18n.tr("plugin.building.generate.ghost_visible")
                : PlotI18n.tr("plugin.building.generate.ghost_hidden"));

        BuildingPreviewIdentity.Validity validity = ctx.previewValidity(targets);
        com.plot.api.world.PlacementReadiness readiness =
            ctx.host().projection().checkWorldModificationReadiness();
        if (!readiness.ready()) {
            ImGui.textColored(PluginUiColors.ERROR_SOFT, readiness.message());
        } else if (validity == BuildingPreviewIdentity.Validity.VALID) {
            ImGui.textColored(PluginUiColors.STATUS_OK, PlotI18n.tr("plugin.building.generate.build_ready"));
        } else if (validity == BuildingPreviewIdentity.Validity.STALE) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.building.generate.build_stale"));
        } else {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.generate.build_needs_preview"));
        }

        boolean buildDisabled = validity != BuildingPreviewIdentity.Validity.VALID
            || !readiness.ready()
            || ctx.host().placement().isBusy()
            || ctx.isDistrictPreviewBusy();
        if (buildDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(
                PlotI18n.tr("plugin.building.generate.build_this_preview", targets.size()),
                ImGui.getContentRegionAvailX(),
                0)) {
            ctx.requestBuildFromCurrentPreview(targets);
        }
        if (buildDisabled) {
            ImGui.endDisabled();
        }
    }
}
