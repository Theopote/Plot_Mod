package com.plot.plugin.building.ui;

import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.HashSet;
import java.util.List;
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

        int minFloors = Integer.MAX_VALUE;
        int maxFloors = 0;
        for (BuildingFootprint building : targets) {
            minFloors = Math.min(minFloors, building.getFloors());
            maxFloors = Math.max(maxFloors, building.getFloors());
        }
        if (minFloors == Integer.MAX_VALUE) {
            minFloors = 0;
        }

        String emptyHint = validity == BuildingPreviewIdentity.Validity.STALE
            ? PlotI18n.tr("plugin.building.generate.massing_preview_stale")
            : PlotI18n.tr("plugin.building.generate.massing_preview_idle");

        BuildingMassingPreviewRenderer.render(
            "##building_generate_massing_preview",
            new BuildingMassingPreviewRenderer.Model(
                targets,
                warningIds,
                skippedIds,
                new HashSet<>(ctx.selection().ids()),
                minFloors,
                maxFloors,
                emptyHint),
            id -> ctx.selection().select(id, ImGui.getIO().getKeyCtrl()));

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
            if (targets.size() == 1) {
                ctx.calculatePreview(targets.getFirst());
            } else {
                ctx.calculateDistrictPreview(targets, true);
            }
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

        if (previewBusy) {
            ImGui.textColored(
                com.plot.plugin.ui.PluginUiColors.STATUS_INFO,
                PlotI18n.tr("plugin.building.generate.preview_running"));
            return;
        }

        switch (validity) {
            case NONE -> { }
            case STALE -> ImGui.textColored(
                com.plot.plugin.ui.PluginUiColors.WARNING,
                PlotI18n.tr("plugin.building.generate.preview_stale"));
            case VALID -> ImGui.textColored(
                com.plot.plugin.ui.PluginUiColors.STATUS_OK,
                PlotI18n.tr("plugin.building.generate.preview_ready"));
        }
    }
}
