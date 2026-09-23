package com.plot.plugin.building.ui;

import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Generate Tab 体量预览主体 + 预览操作按钮。 */
public final class BuildingMassingPreview {
    private BuildingMassingPreview() {
    }

    public static void render(BuildingUiContext ctx, List<BuildingFootprint> targets) {
        BuildingPreviewIdentity.Validity validity = ctx.previewValidity(targets);
        Set<String> warningIds = validity == BuildingPreviewIdentity.Validity.VALID
            ? ctx.state().getOverlayWarningBuildingIds()
            : Set.of();
        Set<String> skippedIds = skippedBuildingIds(ctx);

        Map<String, Double> previewHeights = ctx.state().getMassingPreviewHeightBlocks();
        BuildingMassingPreviewHeights.HeightRange heightRange =
            BuildingMassingPreviewHeights.range(targets, previewHeights);

        BuildingMassingPreviewRenderer.render(
            "##building_generate_massing_preview",
            new BuildingMassingPreviewRenderer.Model(
                targets,
                previewHeights,
                warningIds,
                skippedIds,
                new HashSet<>(ctx.selection().ids()),
                heightRange.min(),
                heightRange.max(),
                ""),
            id -> ctx.selection().select(id, ImGui.getIO().getKeyCtrl()));

        ImGui.textColored(
            com.plot.plugin.ui.PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.building.generate.massing_live_hint"));
        renderPreviewControls(ctx, targets, validity);
    }

    private static Set<String> skippedBuildingIds(BuildingUiContext ctx) {
        DistrictGenerationResult district = ctx.lastDistrictResult();
        if (district == null) {
            return Set.of();
        }
        Set<String> ids = new HashSet<>();
        for (DistrictGenerationResult.BuildingOutcome skipped : district.skippedOutcomes()) {
            ids.add(skipped.buildingId());
        }
        return ids;
    }

    private static void renderPreviewControls(
            BuildingUiContext ctx,
            List<BuildingFootprint> targets,
            BuildingPreviewIdentity.Validity validity) {
        boolean previewBusy = ctx.isDistrictPreviewBusy();
        float half = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) * 0.5f;

        if (previewBusy) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.building.generate.update_preview"), half, 0)) {
            ctx.calculateDistrictPreview(targets, false);
        }
        if (previewBusy) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();
        boolean clearDisabled = !ctx.hasPreviewResult() || previewBusy;
        if (clearDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.building.clear_preview"), half, 0)) {
            ctx.clearPreview();
        }
        if (clearDisabled) {
            ImGui.endDisabled();
        }

        if (ctx.isDistrictPreviewBusy()) {
            DistrictPreviewJob job = ctx.state().getDistrictPreviewJob();
            if (job != null) {
                ImGui.textColored(
                    com.plot.plugin.ui.PluginUiColors.STATUS_INFO,
                    PlotI18n.tr(
                        "plugin.building.district_preview_progress",
                        job.processedCount(),
                        job.totalCount()));
            } else {
                ImGui.textColored(
                    com.plot.plugin.ui.PluginUiColors.STATUS_INFO,
                    PlotI18n.tr("plugin.building.generate.preview_running"));
            }
            return;
        }
        switch (validity) {
            case NONE -> ImGui.textColored(
                com.plot.plugin.ui.PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.building.generate.check_not_run"));
            case STALE -> ImGui.textColored(
                com.plot.plugin.ui.PluginUiColors.WARNING,
                PlotI18n.tr("plugin.building.generate.parameters_changed"));
            case VALID -> ImGui.textColored(
                com.plot.plugin.ui.PluginUiColors.STATUS_OK,
                PlotI18n.tr("plugin.building.generate.check_ready"));
        }
    }
}
