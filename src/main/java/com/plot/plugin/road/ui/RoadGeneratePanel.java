package com.plot.plugin.road.ui;

import com.plot.infrastructure.event.block.BlockPlacementScheduler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/**
 * 道路生成 Tab：预览计算、投影与世界落地。
 */
public final class RoadGeneratePanel {
    private final RoadUiContext ctx;

    public RoadGeneratePanel(RoadUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        float half = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;
        boolean hasNetwork = !network.getEdges().isEmpty();

        if (!hasNetwork) {
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.no_edges"));
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.calc_preview"), half, 0)) {
            ctx.previewManager().calculateNetworkPreview(network);
        }
        if (!hasNetwork) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();
        boolean hasPreview = ctx.previewManager().getLastGenerationResult() != null;
        if (!hasPreview) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.clear_preview"), half, 0)) {
            ctx.previewManager().clearPreview();
        }
        if (!hasPreview) {
            ImGui.endDisabled();
        }

        if (!hasNetwork) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.build_direct"), ImGui.getContentRegionAvailX(), 0)) {
            if (ctx.previewManager().calculateNetworkPreview(network)) {
                ctx.requestBuildConfirm();
            }
        }
        if (!hasNetwork) {
            ImGui.endDisabled();
        }

        if (!hasNetwork) {
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.draw_path_hint"));
        }

        BlockProjectionHandler.PlacementReadiness buildReadiness =
            BlockProjectionHandler.getInstance().checkWorldModificationReadiness();
        if (!buildReadiness.ready()) {
            ImGui.textColored((int) 0xFFFF8080FFL, buildReadiness.message());
        }
        RoadUiWidgets.renderRoadVisibilityWarning(ctx);

        RoadGenerator.RoadGenerationResult lastGenerationResult = ctx.previewManager().getLastGenerationResult();
        if (lastGenerationResult != null) {
            ImGui.separator();
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.preview_projection_hint"));
            ImGui.text(PlotI18n.tr("plugin.road.calc_results"));
            ImGui.text(PlotI18n.tr("plugin.road.cut_volume_result", lastGenerationResult.cutVolume));
            ImGui.text(PlotI18n.tr("plugin.road.fill_volume_result", lastGenerationResult.fillVolume));
            ImGui.text(PlotI18n.tr("plugin.road.bridge_count_result",
                lastGenerationResult.bridgeCount, lastGenerationResult.bridgeBlocks.size()));
            ImGui.text(PlotI18n.tr("plugin.road.tunnel_count_result",
                lastGenerationResult.tunnelCount, lastGenerationResult.tunnelBlocks.size()));
            ImGui.text(PlotI18n.tr("plugin.road.streetlight_count_result", lastGenerationResult.streetlightCount));

            boolean hasPlacements = !lastGenerationResult.placementRecords.isEmpty();
            if (!hasPlacements) {
                ImGui.textColored((int) 0xFFFFB060FFL, PlotI18n.tr("plugin.road.generate_empty_result"));
            }

            if (!hasPlacements) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(PlotI18n.tr("plugin.road.projection_ref"), half, 0)) {
                ctx.previewManager().projectRoadPreview();
            }
            if (!hasPlacements) {
                ImGui.endDisabled();
            }

            ImGui.sameLine();
            boolean buildDisabled = !hasPlacements
                || !buildReadiness.ready()
                || BlockPlacementScheduler.getInstance().isBusy();
            if (buildDisabled) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(PlotI18n.tr("plugin.road.build"), half, 0)) {
                ctx.requestBuildConfirm();
            }
            if (buildDisabled) {
                ImGui.endDisabled();
            }
            renderBuildConfirmPopup();
        }
    }

    private void renderBuildConfirmPopup() {
        if (ctx.buildConfirmPending()) {
            ImGui.openPopup("##road_build_confirm");
            ctx.clearBuildConfirmPending();
        }

        RoadGenerator.RoadGenerationResult lastGenerationResult = ctx.previewManager().getLastGenerationResult();
        if (ImGui.beginPopupModal("##road_build_confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            int blockCount = lastGenerationResult != null ? lastGenerationResult.placementRecords.size() : 0;
            ImGui.text(String.format(PlotI18n.tr("plugin.road.build_confirm"), blockCount));

            BlockProjectionHandler.PlacementReadiness readiness =
                BlockProjectionHandler.getInstance().checkWorldModificationReadiness();
            if (!readiness.ready()) {
                ImGui.textColored((int) 0xFFFF6060FFL, readiness.message());
            }
            RoadUiWidgets.renderRoadVisibilityWarning(ctx);

            ImGui.separator();
            boolean canBuild = readiness.ready() && !BlockPlacementScheduler.getInstance().isBusy();
            if (!canBuild) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(PlotI18n.tr("plugin.road.build"), 120, 0)) {
                ctx.previewManager().buildRoadInWorld();
                ImGui.closeCurrentPopup();
            }
            if (!canBuild) {
                ImGui.endDisabled();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 120, 0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }
}
