package com.plot.plugin.road.ui;
import com.plot.plugin.ui.PluginUiColors;

import com.plot.core.terrain.MinecraftTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadLongitudinalProfileRenderer;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.RoadNetworkValidationReport;
import com.plot.plugin.road.RoadNetworkEngineeringValidator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.station.ChainageDisplayContext;
import com.plot.plugin.road.station.ChainageDisplayMode;
import com.plot.plugin.road.station.RoadStationFormat;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.FlatElevationProfileOverlay;
import com.plot.plugin.road.vertical.RoadVerticalStrategy;
import com.plot.plugin.road.vertical.VerticalAlignmentProfileOverlay;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 道路生成 Tab：预览计算、投影与世界落地。
 */
public final class RoadGeneratePanel {
    private final RoadUiContext ctx;
    private final VerticalProfileEditor profileEditor = new VerticalProfileEditor();
    private final VerticalAlignmentEditor verticalAlignmentEditor = new VerticalAlignmentEditor();
    private final RoadNetworkToolsPanel networkToolsPanel;
    private String profileEdgeId = "";
    private boolean profileSectionForceOpen = false;
    private long cachedValidationKey = Long.MIN_VALUE;
    private RoadNetworkValidationReport cachedValidationReport = new RoadNetworkValidationReport(List.of());

    public RoadGeneratePanel(RoadUiContext ctx) {
        this.ctx = ctx;
        this.networkToolsPanel = new RoadNetworkToolsPanel(ctx);
    }

    /** 从概览等入口跳转时，聚焦指定边的纵断面区块。 */
    public void openProfileForEdge(String edgeId) {
        if (edgeId == null || edgeId.isBlank()) {
            return;
        }
        profileEdgeId = edgeId;
        profileSectionForceOpen = true;
        ctx.networkManager().setPrimarySelectedEdge(edgeId);
        profileEditor.openEditorForEdge(edgeId);
    }

    public void render() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        RoadNetworkValidationReport preflight = preflightReport(network);
        com.plot.api.world.PlacementReadiness buildReadiness =
            ctx.host().projection().checkWorldModificationReadiness();

        RoadSelectionHeader.render(ctx);
        ImGui.separator();
        renderNetworkBuildScope(network);
        if (!network.getEdges().isEmpty()) {
            renderGenerateWorkflowHint();
        }
        ImGui.separator();

        RoadUiSections.section("plugin.road.section.generation_settings");
        RoadGenerationSettingsPanel.renderPrimary(ctx);
        RoadGenerationSettingsPanel.renderAdvanced(ctx);
        ImGui.separator();

        renderPreviewActions(network, preflight, buildReadiness);

        if (!network.getEdges().isEmpty()) {
            renderProfileWorkspace(network);
            renderIntersectionCheckSection(network, preflight);
            renderAdvancedTerrainSection(network);
        }

        RoadGenerationResult lastGenerationResult = ctx.previewManager().getLastGenerationResult();
        if (ctx.previewManager().hasValidPreview() && lastGenerationResult != null) {
            renderCompactPreviewSummary(lastGenerationResult);
            renderBuildAction(lastGenerationResult, buildReadiness, validationReport());
            renderPreviewDetailsCollapsible(network, lastGenerationResult);
        }
    }

    void renderProfileEditorWindow(RoadNetwork network) {
        profileEditor.renderEditorWindow(ctx, network, resolveFlatProfileOverlay(network));
    }

    void renderUniformElevationConfirmPopup() {
        networkToolsPanel.renderConfirmPopup();
    }

    private void renderGenerateWorkflowHint() {
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.generate.workflow_hint"));
    }

    private void renderProfileWorkspace(RoadNetwork network) {
        ImGui.separator();
        RoadUiSections.section("plugin.road.generate.profile_section");
        RoadEdge edge = resolveProfileEdge(network);
        if (edge == null) {
            return;
        }
        Road road = network.getRoadForEdge(edge);
        if (road != null) {
            RoadVerticalStrategy strategy = RoadVerticalStrategy.fromRoad(road);
            if (strategy == RoadVerticalStrategy.TERRAIN_ADAPTIVE
                    && RoadGenerationSettingsPanel.showsTerrainAdaptiveControls(ctx)) {
                RoadStyleProductControls.renderRoadMaxSlopePresets(
                    ctx, road, ctx.networkManager()::pushHistory);
            }
            ChainageDisplayContext chainageDisplay = chainageContextOrNull(network, road);
            verticalAlignmentEditor.render(
                ctx,
                network,
                road,
                chainageDisplay,
                ctx.networkManager().getConfig(),
                this::requireTerrainOrNull,
                ctx.networkManager()::pushHistory);
            renderSlopeOverridesSection(network, road, edge, chainageDisplay);
        }
        profileEditor.renderInline(ctx, network, edge, resolveFlatProfileOverlay(network, road));
    }

    private FlatElevationProfileOverlay resolveFlatProfileOverlay(RoadNetwork network) {
        RoadEdge edge = resolveProfileEdge(network);
        if (edge == null) {
            return FlatElevationProfileOverlay.EMPTY;
        }
        Road road = network.getRoadForEdge(edge);
        return resolveFlatProfileOverlay(network, road);
    }

    private FlatElevationProfileOverlay resolveFlatProfileOverlay(RoadNetwork network, Road road) {
        if (road == null || RoadVerticalStrategy.fromRoad(road) != RoadVerticalStrategy.FLAT) {
            return FlatElevationProfileOverlay.EMPTY;
        }
        return verticalAlignmentEditor.flatElevationProfileOverlay(
            network, road, ctx.networkManager().getConfig());
    }

    private void renderAdvancedTerrainSection(RoadNetwork network) {
        ImGui.separator();
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.generate.advanced_terrain"))) {
            return;
        }
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.generate.advanced_terrain_hint"));
        networkToolsPanel.render(network);
    }

    private void renderSlopeOverridesSection(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            ChainageDisplayContext chainageDisplay) {
        if (chainageDisplay == null) {
            return;
        }
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.generate.slope_overrides"))) {
            return;
        }
        RoadSegmentEditor.renderSlopeOverrides(ctx, network, road, edge, chainageDisplay);
    }

    private ChainageDisplayContext chainageContextOrNull(RoadNetwork network, Road road) {
        if (!RoadStationing.isStationable(network, road)) {
            return null;
        }
        return new ChainageDisplayContext(
            RoadStationing.canonicalLength(network, road),
            ChainageDisplayMode.FROM_START,
            RoadStationFormat.KILOMETER_PLUS);
    }

    private TerrainSampler requireTerrainOrNull() {
        World world = RoadNetworkGenerator.getClientWorld();
        if (world == null) {
            ctx.status().error(PlotI18n.tr("plugin.road.generate_world_unavailable"));
            return null;
        }
        return MinecraftTerrainSampler.of(world, ctx.host().coordinates());
    }

    private RoadEdge resolveProfileEdge(RoadNetwork network) {
        String focusedEdgeId = profileEditor.getFocusedEdgeId();
        if (focusedEdgeId != null && !focusedEdgeId.isBlank()) {
            RoadEdge focused = network.getEdge(focusedEdgeId);
            if (focused != null) {
                profileEdgeId = focusedEdgeId;
                return focused;
            }
        }
        if (profileEdgeId != null && !profileEdgeId.isBlank()) {
            RoadEdge focused = network.getEdge(profileEdgeId);
            if (focused != null) {
                return focused;
            }
        }
        String edgeId = ctx.networkManager().getPrimarySelectedEdgeId();
        RoadEdge edge = edgeId != null ? network.getEdge(edgeId) : null;
        if (edge == null && !network.getEdges().isEmpty()) {
            edge = network.getEdge(network.getEdges().keySet().iterator().next());
        }
        if (edge != null) {
            profileEdgeId = edge.getId();
        }
        return edge;
    }

    private void renderIntersectionCheckSection(
            RoadNetwork network,
            RoadNetworkValidationReport preflight) {
        if (network.getJunctionCount() <= 0) {
            return;
        }
        ImGui.separator();
        RoadUiSections.section("plugin.road.generate.intersection_check");
        int junctionCount = network.getJunctionCount();
        int issueCount = preflight.intersectionIssueCount();
        if (issueCount > 0) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.WARNING_LIGHT,
                PlotI18n.tr(
                    "plugin.road.generate.intersection_issues",
                    issueCount,
                    junctionCount));
        } else {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.STATUS_INFO,
                PlotI18n.tr("plugin.road.generate.intersection_ok", junctionCount));
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.generate.goto_intersections"))) {
            ctx.requestTab(RoadUiTab.PATH);
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

        if (ctx.previewManager().needsPreviewRecalc() && !ctx.previewManager().hasValidPreview()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.WARNING_LIGHT,
                PlotI18n.tr("plugin.road.preview_stale"));
        }

        if (previewBlocked) {
            ImGui.beginDisabled();
        }
        String calcLabel = ctx.previewManager().needsPreviewRecalc()
            ? PlotI18n.tr("plugin.road.recalculate_preview")
            : PlotI18n.tr("plugin.road.calc_preview");
        if (ImGui.button(calcLabel, half, 0)) {
            ctx.previewManager().startNetworkPreview(network);
        }
        if (previewBlocked) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();
        boolean hasPreview = ctx.previewManager().hasValidPreview();
        boolean previewBusy = ctx.previewManager().isPreviewJobRunning();
        boolean clearPreviewDisabled = !hasPreview || previewBusy;
        if (clearPreviewDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.clear_preview"), half, 0)) {
            ctx.previewManager().clearPreview();
            profileEdgeId = "";
            profileEditor.clearCache();
        }
        if (clearPreviewDisabled) {
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
