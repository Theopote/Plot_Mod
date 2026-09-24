package com.plot.plugin.road.ui;
import com.plot.plugin.ui.PluginUiColors;

import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadLongitudinalProfileRenderer;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.RoadNetworkValidationReport;
import com.plot.plugin.road.RoadNetworkEngineeringValidator;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.vertical.VerticalAlignmentProfileOverlay;
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
    private final VerticalProfileEditor profileEditor = new VerticalProfileEditor();
    private String profileEdgeId = "";
    private boolean profileSectionForceOpen = false;
    private long cachedValidationKey = Long.MIN_VALUE;
    private RoadNetworkValidationReport cachedValidationReport = new RoadNetworkValidationReport(List.of());

    public RoadGeneratePanel(RoadUiContext ctx) {
        this.ctx = ctx;
    }

    /** 从概览等入口跳转时，聚焦指定边的纵断面区块。 */
    public void openProfileForEdge(String edgeId) {
        if (edgeId == null || edgeId.isBlank()) {
            return;
        }
        profileEdgeId = edgeId;
        profileSectionForceOpen = true;
    }

    public void render() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        RoadNetworkValidationReport preflight = preflightReport(network);
        com.plot.api.world.PlacementReadiness buildReadiness =
            ctx.host().projection().checkWorldModificationReadiness();

        RoadSelectionHeader.render(ctx);
        ImGui.separator();
        renderNetworkBuildScope(network);
        ImGui.separator();

        RoadUiSections.section("plugin.road.section.generation_settings");
        RoadGenerationSettingsPanel.renderPrimary(ctx);
        RoadGenerationSettingsPanel.renderAdvanced(ctx);
        ImGui.separator();

        renderPreviewActions(network, preflight, buildReadiness);

        if (ctx.previewManager().hasValidPreview()) {
            renderProfileWorkspace(network);
        }

        RoadGenerationResult lastGenerationResult = ctx.previewManager().getLastGenerationResult();
        if (ctx.previewManager().hasValidPreview() && lastGenerationResult != null) {
            renderCompactPreviewSummary(lastGenerationResult);
            renderBuildAction(lastGenerationResult, buildReadiness, validationReport());
            renderPreviewDetailsCollapsible(network, lastGenerationResult);
        }
    }

    private void renderProfileWorkspace(RoadNetwork network) {
        ImGui.separator();
        RoadUiSections.section("plugin.road.generate.profile_section");
        String edgeId = ctx.networkManager().getPrimarySelectedEdgeId();
        RoadEdge edge = edgeId != null ? network.getEdge(edgeId) : null;
        if (edge == null && !network.getEdges().isEmpty()) {
            edge = network.getEdge(network.getEdges().keySet().iterator().next());
        }
        if (edge != null) {
            profileEditor.renderInline(ctx, network, edge);
        }
    }

    private void renderNetworkBuildScope(RoadNetwork network) {
        RoadUiSections.section("plugin.road.build.generation_scope");
        if (network.getEdges().isEmpty()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.build.network_scope_empty"));
            return;
        }
        RoadUiWidgets.textWrapped(PlotI18n.tr(
            "plugin.road.build.network_scope_summary",
            network.getRoads().size(),
            network.getJunctionCount(),
            RoadUiFormat.format(RoadEdgeListHelper.computeNetworkWorldLength(
                network, ctx.host().coordinates()))));
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.build.network_scope_hint"));
    }

    private void renderPreviewActions(
            RoadNetwork network,
            RoadNetworkValidationReport preflight,
            com.plot.api.world.PlacementReadiness buildReadiness) {
        float half = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;
        boolean hasNetwork = !network.getEdges().isEmpty();
        boolean previewBlocked = !hasNetwork || preflight.blocksBuild() || ctx.previewManager().isPreviewJobRunning();

        if (!preflight.items().isEmpty()) {
            RoadNetworkValidationPanel.render(preflight, ctx);
        }

        if (previewBlocked) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.calc_preview"), half, 0)) {
            ctx.previewManager().startNetworkPreview(network);
        }
        if (previewBlocked) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();
        boolean hasPreview = ctx.previewManager().hasValidPreview();
        if (!hasPreview || ctx.previewManager().isPreviewJobRunning()) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.clear_preview"), half, 0)) {
            ctx.previewManager().clearPreview();
            profileEdgeId = "";
        }
        if (!hasPreview) {
            ImGui.endDisabled();
        }

        if (!buildReadiness.ready()) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.ERROR_SOFT, buildReadiness.message());
        }
        RoadUiWidgets.renderRoadVisibilityWarning(ctx);
    }

    private void renderCompactPreviewSummary(RoadGenerationResult result) {
        ImGui.separator();
        ImGui.text(PlotI18n.tr(
            "plugin.road.build.compact_summary",
            result.placementRecords.size(),
            result.bridgeCount,
            result.tunnelCount,
            result.streetlightCount));
    }

    private void renderBuildAction(
            RoadGenerationResult lastGenerationResult,
            com.plot.api.world.PlacementReadiness buildReadiness,
            RoadNetworkValidationReport validationReport) {
        ImGui.separator();
        boolean hasPlacements = !lastGenerationResult.placementRecords.isEmpty();
        RoadNetworkGenerator.NetworkGenerationResult networkGenerationResult =
            ctx.previewManager().getLastNetworkGenerationResult();
        boolean partialFailure = networkGenerationResult != null
            && networkGenerationResult.hasPartialFailure();
        if (partialFailure) {
            ImGui.textColored(PluginUiColors.ERROR_SOFT, PlotI18n.tr("plugin.road.build_blocked_partial_generation"));
        } else if (!hasPlacements) {
            ImGui.textColored(PluginUiColors.WARNING_LIGHT, PlotI18n.tr("plugin.road.generate_empty_result"));
        }

        boolean buildDisabled = !hasPlacements
            || !buildReadiness.ready()
            || ctx.host().placement().isBusy()
            || ctx.previewManager().isPreviewJobRunning()
            || validationReport.blocksBuild()
            || partialFailure;
        if (buildDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.build"), ImGui.getContentRegionAvailX(), 0)) {
            ctx.requestBuildConfirm();
        }
        if (buildDisabled) {
            ImGui.endDisabled();
        }
    }

    private void renderPreviewDetailsCollapsible(RoadNetwork network, RoadGenerationResult lastGenerationResult) {
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.build.preview_details"))) {
            return;
        }
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

        RoadNetworkValidationReport validationReport = validationReport();
        RoadNetworkValidationPanel.render(validationReport, ctx);
        renderLongitudinalProfile(network);
    }

    private RoadNetworkValidationReport preflightReport(RoadNetwork network) {
        return RoadNetworkEngineeringValidator.analyzePreGeneration(network);
    }

    private RoadNetworkValidationReport validationReport() {
        long key = ctx.networkManager().getNetworkRevision() * 31L
            + ctx.previewManager().getTerrainRevision();
        if (key != cachedValidationKey) {
            cachedValidationReport = RoadNetworkValidationPanel.analyze(ctx);
            cachedValidationKey = key;
        }
        return cachedValidationReport;
    }

    private void renderLongitudinalProfile(RoadNetwork network) {
        List<String> edgeIds = listProfileEdgeIds();
        if (edgeIds.isEmpty()) {
            return;
        }
        syncProfileEdgeSelection(network);
        int headerFlags = profileSectionForceOpen ? imgui.flag.ImGuiTreeNodeFlags.DefaultOpen : 0;
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.longitudinal_profile"), headerFlags)) {
            return;
        }
        profileSectionForceOpen = false;

        if (edgeIds.size() > 1) {
            renderProfileEdgeSelector(network, edgeIds);
        } else {
            RoadEdge edge = network.getEdge(edgeIds.getFirst());
            if (edge != null) {
                ImGui.textColored(
                    PluginUiColors.HINT_GRAY,
                    RoadEdgeListHelper.formatEdgeLabel(network, edge));
            }
        }

        RoadGenerationResult profileResult = ctx.previewManager().getLastEdgeResult(profileEdgeId);
        if (profileResult != null && profileResult.hasProfileData()) {
            RoadEdge edge = network.getEdge(profileEdgeId);
            VerticalAlignmentProfileOverlay designOverlay = edge != null
                ? VerticalAlignmentProfileOverlay.forEdge(network, edge).orElse(null)
                : null;
            RoadLongitudinalProfileRenderer.render(profileResult, false, designOverlay);
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

    public void renderBuildConfirmPopup() {
        if (ctx.buildConfirmPending()) {
            ImGui.openPopup("##road_build_confirm");
            ctx.clearBuildConfirmPending();
        }

        if (!ImGui.isPopupOpen("##road_build_confirm")) {
            return;
        }

        RoadGenerationResult lastGenerationResult = ctx.previewManager().getLastGenerationResult();
        if (!ImGui.beginPopupModal("##road_build_confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            return;
        }
        RoadNetworkValidationReport validationReport = validationReport();
        int blockCount = lastGenerationResult != null ? lastGenerationResult.placementRecords.size() : 0;
        ImGui.text(String.format(PlotI18n.tr("plugin.road.build_confirm"), blockCount));

            if (lastGenerationResult != null) {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr(
                        "plugin.road.build_confirm_volumes",
                        lastGenerationResult.cutVolume,
                        lastGenerationResult.fillVolume,
                        lastGenerationResult.bridgeCount,
                        lastGenerationResult.tunnelCount));
            }

            com.plot.api.world.PlacementReadiness readiness =
                ctx.host().projection().checkWorldModificationReadiness();
            if (!readiness.ready()) {
                RoadUiWidgets.textWrappedColored(PluginUiColors.ERROR, readiness.message());
            }
            RoadUiWidgets.renderRoadVisibilityWarning(ctx);
            RoadNetworkValidationPanel.renderConfirmWarnings(validationReport);

            ImGui.spacing();
            RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.build_confirm_cancel_hint"));
            RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.build_confirm_undo_hint"));

            RoadNetworkGenerator.NetworkGenerationResult networkGenerationResult =
                ctx.previewManager().getLastNetworkGenerationResult();
            boolean partialFailure = networkGenerationResult != null
                && networkGenerationResult.hasPartialFailure();
            if (partialFailure) {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.ERROR_SOFT,
                    PlotI18n.tr("plugin.road.build_blocked_partial_generation"));
            }

            ImGui.separator();
            boolean canBuild = readiness.ready()
                && !ctx.host().placement().isBusy()
                && !ctx.previewManager().isPreviewJobRunning()
                && !validationReport.blocksBuild()
                && !partialFailure;
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
