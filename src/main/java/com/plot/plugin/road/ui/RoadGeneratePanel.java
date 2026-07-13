package com.plot.plugin.road.ui;

import com.plot.infrastructure.event.block.BlockPlacementScheduler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadLongitudinalProfileRenderer;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 道路生成 Tab：预览计算、投影与世界落地。
 */
public final class RoadGeneratePanel {
    private final RoadUiContext ctx;
    private String profileEdgeId = "";

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
            if (ctx.previewManager().calculateNetworkPreview(network)) {
                syncProfileEdgeSelection(network);
            }
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
            profileEdgeId = "";
        }
        if (!hasPreview) {
            ImGui.endDisabled();
        }

        if (!hasNetwork) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.build_direct"), ImGui.getContentRegionAvailX(), 0)) {
            if (ctx.previewManager().calculateNetworkPreview(network)) {
                syncProfileEdgeSelection(network);
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

        RoadGenerationResult lastGenerationResult = ctx.previewManager().getLastGenerationResult();
        if (lastGenerationResult != null) {
            ImGui.separator();
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.preview_projection_hint"));
            ImGui.text(PlotI18n.tr("plugin.road.calc_results"));
            ImGui.text(PlotI18n.tr("plugin.road.cut_volume_result", lastGenerationResult.cutVolume));
            ImGui.text(PlotI18n.tr("plugin.road.fill_volume_result", lastGenerationResult.fillVolume));
            ImGui.text(PlotI18n.tr("plugin.road.construction_length_result",
                lastGenerationResult.normalRoadLength,
                lastGenerationResult.bridgeLength,
                lastGenerationResult.tunnelLength));
            ImGui.text(PlotI18n.tr("plugin.road.bridge_count_result",
                lastGenerationResult.bridgeCount, lastGenerationResult.bridgeBlocks.size()));
            ImGui.text(PlotI18n.tr("plugin.road.tunnel_count_result",
                lastGenerationResult.tunnelCount, lastGenerationResult.tunnelBlocks.size()));
            ImGui.text(PlotI18n.tr("plugin.road.streetlight_count_result", lastGenerationResult.streetlightCount));

            renderLongitudinalProfile(network);

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

    private void renderLongitudinalProfile(RoadNetwork network) {
        List<String> edgeIds = listProfileEdgeIds();
        if (edgeIds.isEmpty()) {
            return;
        }

        ensureProfileEdgeSelection(network, edgeIds);
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.longitudinal_profile"))) {
            return;
        }

        if (edgeIds.size() > 1) {
            renderProfileEdgeSelector(network, edgeIds);
        } else {
            RoadEdge edge = network.getEdge(edgeIds.getFirst());
            if (edge != null) {
                ImGui.textColored(
                    (int) 0xFF808080FFL,
                    RoadEdgeListHelper.formatEdgeLabel(network, edge));
            }
        }

        RoadGenerationResult profileResult = ctx.previewManager().getLastEdgeResult(profileEdgeId);
        if (profileResult != null && profileResult.hasProfileData()) {
            RoadLongitudinalProfileRenderer.render(profileResult, false);
        }
    }

    private List<String> listProfileEdgeIds() {
        List<String> edgeIds = new ArrayList<>();
        for (Map.Entry<String, RoadGenerationResult> entry
                : ctx.previewManager().getLastEdgeResults().entrySet()) {
            if (entry.getValue() != null && entry.getValue().hasProfileData()) {
                edgeIds.add(entry.getKey());
            }
        }
        return edgeIds;
    }

    private void syncProfileEdgeSelection(RoadNetwork network) {
        List<String> edgeIds = listProfileEdgeIds();
        if (edgeIds.isEmpty()) {
            profileEdgeId = "";
            return;
        }
        String primaryId = ctx.networkManager().getPrimarySelectedEdgeId();
        profileEdgeId = edgeIds.contains(primaryId) ? primaryId : edgeIds.getFirst();
    }

    private void ensureProfileEdgeSelection(RoadNetwork network, List<String> edgeIds) {
        if (profileEdgeId == null || profileEdgeId.isBlank() || !edgeIds.contains(profileEdgeId)) {
            syncProfileEdgeSelection(network);
        }
    }

    private void renderProfileEdgeSelector(RoadNetwork network, List<String> edgeIds) {
        RoadEdge current = network.getEdge(profileEdgeId);
        String previewLabel = current != null
            ? RoadEdgeListHelper.formatEdgeLabel(network, current)
            : profileEdgeId;

        if (ImGui.beginCombo(PlotI18n.tr("plugin.road.profile_edge_select") + "##profile_edge", previewLabel)) {
            for (String edgeId : edgeIds) {
                RoadEdge edge = network.getEdge(edgeId);
                if (edge == null) {
                    continue;
                }
                String label = RoadEdgeListHelper.formatEdgeLabel(network, edge);
                if (ImGui.selectable(label + "##profile_" + edgeId, edgeId.equals(profileEdgeId))) {
                    profileEdgeId = edgeId;
                }
            }
            ImGui.endCombo();
        }

        int currentIndex = Math.max(0, edgeIds.indexOf(profileEdgeId));
        float navButtonWidth = 28f;
        if (ImGui.button("<##profile_prev", navButtonWidth, 0)) {
            profileEdgeId = edgeIds.get((currentIndex - 1 + edgeIds.size()) % edgeIds.size());
        }
        ImGui.sameLine();
        ImGui.text(PlotI18n.tr("plugin.road.profile_edge_index", currentIndex + 1, edgeIds.size()));
        ImGui.sameLine();
        if (ImGui.button(">##profile_next", navButtonWidth, 0)) {
            profileEdgeId = edgeIds.get((currentIndex + 1) % edgeIds.size());
        }
    }

    private void renderBuildConfirmPopup() {
        if (ctx.buildConfirmPending()) {
            ImGui.openPopup("##road_build_confirm");
            ctx.clearBuildConfirmPending();
        }

        RoadGenerationResult lastGenerationResult = ctx.previewManager().getLastGenerationResult();
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
