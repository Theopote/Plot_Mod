package com.plot.plugin.road.ui;
import com.plot.plugin.ui.PluginUiColors;

import com.plot.core.terrain.MinecraftTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.manager.RoadChangeKind;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.RoadNetworkValidationReport;
import com.plot.plugin.road.RoadNetworkEngineeringValidator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.profile.RoadProfileRoadList;
import com.plot.plugin.road.station.ChainageDisplayContext;
import com.plot.plugin.road.station.ChainageDisplayMode;
import com.plot.plugin.road.station.RoadStationFormat;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.FlatElevationProfileOverlay;
import com.plot.plugin.road.vertical.RoadVerticalStrategy;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
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
    private String profileEdgeId = "";
    private long cachedValidationKey = Long.MIN_VALUE;
    private RoadNetworkValidationReport cachedValidationReport = new RoadNetworkValidationReport(List.of());

    public RoadGeneratePanel(RoadUiContext ctx) {
        this.ctx = ctx;
        profileEditor.setOnAlignmentCommitted(verticalAlignmentEditor::invalidateDraftSync);
        profileEditor.setFlatOverlayResolver(road -> resolveFlatProfileOverlay(ctx.networkManager().getNetwork(), road));
    }

    /** 从概览等入口跳转时，聚焦指定边的纵断面区块。 */
    public void openProfileForEdge(String edgeId) {
        if (edgeId == null || edgeId.isBlank()) {
            return;
        }
        profileEdgeId = edgeId;
        ctx.networkManager().setPrimarySelectedEdge(edgeId);
        RoadEdge edge = ctx.networkManager().getNetwork().getEdge(edgeId);
        if (edge != null && edge.getRoadId() != null) {
            profileEditor.openEditorForRoad(ctx, edge.getRoadId());
        } else {
            profileEditor.openEditorForEdge(ctx, ctx.networkManager().getNetwork(), edgeId);
        }
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
        ImGui.separator();

        renderPreviewActions(network, preflight, buildReadiness);

        if (!network.getEdges().isEmpty()) {
            renderProfileWorkspace(network);
            renderIntersectionCheckSection(network, preflight);
        }

        RoadGenerationResult lastGenerationResult = ctx.previewManager().getLastGenerationResult();
        if (ctx.previewManager().hasValidPreview() && lastGenerationResult != null) {
            renderCompactPreviewSummary(lastGenerationResult);
            renderBuildAction(lastGenerationResult, buildReadiness, validationReport());
            renderPreviewDetailsCollapsible(network, lastGenerationResult);
        }
    }

    void renderProfileEditorWindow(RoadNetwork network) {
        profileEditor.setFlatOverlayResolver(road -> resolveFlatProfileOverlay(network, road));
        profileEditor.renderEditorWindow(ctx, network);
    }

    private void renderGenerateWorkflowHint() {
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.generate.workflow_hint"));
    }

    private void renderProfileWorkspace(RoadNetwork network) {
        ImGui.separator();
        RoadUiSections.section("plugin.road.generate.profile_section");
        RoadProfileOverviewSection.render(
            ctx,
            network,
            profileEditor,
            road -> resolveFlatProfileOverlay(network, road));

        Road road = resolveActiveProfileRoad(network);
        if (road == null) {
            return;
        }
        ImGui.spacing();
        RoadUiSections.section("plugin.road.profile_active_road_detail");
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            RoadProfileRoadList.formatProfileRoadSummary(network, road));
        ImGui.spacing();
        RoadVerticalStrategy strategy = RoadVerticalStrategy.fromRoad(road);
        if (strategy == RoadVerticalStrategy.TERRAIN_ADAPTIVE
                && RoadGenerationSettingsPanel.showsTerrainAdaptiveControls(ctx)) {
            RoadStyleProductControls.renderRoadMaxSlopePresets(
                ctx, road, ctx.networkManager()::pushHistory);
            TerrainFollowPresetControls.render(ctx, road, ctx.networkManager()::pushHistory);
        }
        ChainageDisplayContext chainageDisplay = chainageContextOrNull(network, road);
        verticalAlignmentEditor.render(
            ctx,
            network,
            road,
            chainageDisplay,
            ctx.networkManager().getConfig(),
            this::requireTerrainOrNull,
            () -> ctx.networkManager().pushHistory(RoadChangeKind.VERTICAL_PROFILE));
    }

    private FlatElevationProfileOverlay resolveFlatProfileOverlay(RoadNetwork network) {
        Road road = resolveActiveProfileRoad(network);
        return resolveFlatProfileOverlay(network, road);
    }

    private FlatElevationProfileOverlay resolveFlatProfileOverlay(RoadNetwork network, Road road) {
        if (road == null || RoadVerticalStrategy.fromRoad(road) != RoadVerticalStrategy.FLAT) {
            return FlatElevationProfileOverlay.EMPTY;
        }
        return verticalAlignmentEditor.flatElevationProfileOverlay(
            network, road, ctx.networkManager().getConfig());
    }

    private ChainageDisplayContext chainageContextOrNull(RoadNetwork network, Road road) {
        if (!RoadStationing.isStationable(network, road)) {
            return null;
        }
        return new ChainageDisplayContext(
            RoadStationing.canonicalLength(network, road),
            ChainageDisplayMode.FROM_START,
            RoadStationFormat.DISTANCE_METERS);
    }

    private TerrainSampler requireTerrainOrNull() {
        World world = RoadNetworkGenerator.getClientWorld();
        if (world == null) {
            ctx.status().error(PlotI18n.tr("plugin.road.generate_world_unavailable"));
            return null;
        }
        return MinecraftTerrainSampler.of(world, ctx.host().coordinates());
    }

    private Road resolveActiveProfileRoad(RoadNetwork network) {
        return RoadProfileRoadList.resolveActiveRoad(network, ctx);
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

    public void renderBuildConfirmPopup() {
        if (!RoadUiWidgets.beginDeferredPopupModal(
                "##road_build_confirm",
                ctx.buildConfirmPending(),
                ctx::clearBuildConfirmPending)) {
            return;
        }
        try {
        RoadGenerationResult lastGenerationResult = ctx.previewManager().getLastGenerationResult();
        RoadNetworkValidationReport validationReport = validationReport();
        int blockCount = lastGenerationResult != null ? lastGenerationResult.placementRecords.size() : 0;
        ImGui.text(String.format(PlotI18n.tr("plugin.road.build_confirm"), blockCount));

            if (lastGenerationResult != null) {
                int totalBlocks = lastGenerationResult.placementRecords.size();
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr(
                        "plugin.road.build_confirm_summary",
                        totalBlocks,
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
        } finally {
            ImGui.endPopup();
        }
    }
}
