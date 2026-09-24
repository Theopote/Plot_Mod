package com.plot.plugin.building.ui;

import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import com.plot.ui.component.UIUtils;
import imgui.ImGui;
import imgui.type.ImBoolean;

import java.util.List;

/** Generate Tab 建造区：就绪检查 + 落地按钮。 */
public final class BuildingBuildAction {
    private BuildingBuildAction() {
    }

    public static void render(BuildingUiContext ctx, List<BuildingFootprint> targets) {
        ImGui.separator();
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

        ImBoolean frameOnly = new ImBoolean(ctx.frameOnlyGenerate());
        if (ImGui.checkbox(PlotI18n.tr("plugin.building.generate.frame_only"), frameOnly)) {
            ctx.setFrameOnlyGenerate(frameOnly.get());
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.generate.frame_only");

        boolean buildDisabled = validity != BuildingPreviewIdentity.Validity.VALID
            || !readiness.ready()
            || ctx.host().placement().isBusy()
            || ctx.isDistrictPreviewBusy();
        if (buildDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(
                PlotI18n.tr("plugin.building.generate.build_this_preview"),
                ImGui.getContentRegionAvailX(),
                0)) {
            ctx.requestBuildFromCurrentPreview(targets);
        }
        if (buildDisabled) {
            ImGui.endDisabled();
        }
    }
}
