package com.plot.plugin.road.ui;

import com.plot.plugin.road.crossing.RoadIntersectionInventory;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.RoadNetworkValidationReport;
import com.plot.plugin.road.RoadNetworkEngineeringValidator;
import com.plot.plugin.road.validation.RoadElevationBoundsResolver;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;

/**
 * 建造工作区核心：正式预览计算、检查与 Minecraft 落地（不含纵断面/横断面设计）。
 */
public final class RoadGeneratePanel {
    private final RoadUiContext ctx;
    private long cachedValidationKey = Long.MIN_VALUE;
    private RoadNetworkValidationReport cachedValidationReport = new RoadNetworkValidationReport(List.of());
    private long cachedPreflightKey = Long.MIN_VALUE;
    private RoadNetworkValidationReport cachedPreflightReport = new RoadNetworkValidationReport(List.of());

    public RoadGeneratePanel(RoadUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        RoadNetworkValidationReport preflight = preflightReport(network);
        com.plot.api.world.PlacementReadiness buildReadiness =
            ctx.host().projection().checkWorldModificationReadiness();

        renderPreviewStatusCard();
        renderDesignStatusHints(network, preflight);
        ImGui.separator();
        renderNetworkBuildScope(network);
        ImGui.separator();

        renderPreviewActions(network, preflight, buildReadiness);

        RoadGenerationResult lastGenerationResult = ctx.previewManager().getLastGenerationResult();
        if (ctx.previewManager().hasValidPreview() && lastGenerationResult != null) {
            renderCompactPreviewSummary(lastGenerationResult);
            renderBuildAction(lastGenerationResult, buildReadiness, validationReport());
        }

        if (ctx.previewManager().hasValidPreview() && lastGenerationResult != null) {
            renderPreviewDetailsCollapsible(lastGenerationResult);
        }
    }

    private void renderDesignStatusHints(RoadNetwork network, RoadNetworkValidationReport preflight) {
        if (network.getEdges().isEmpty()) {
            return;
        }
        if (ctx.networkManager().hasPendingNetworkEdit()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.WARNING_LIGHT,
                PlotI18n.tr("plugin.road.build_blocked_pending_edit"));
        }
        if (preflight.blocksBuild()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.WARNING_LIGHT,
                PlotI18n.tr("plugin.road.build_blocked_validation"));
            RoadUiWidgets.flowButtons(
                new String[] {
                    PlotI18n.tr("plugin.road.build.goto_route_fix"),
                    PlotI18n.tr("plugin.road.build.goto_cross_section_fix"),
                    PlotI18n.tr("plugin.road.build.goto_profile_fix")
                },
                new Runnable[] {
                    () -> ctx.requestTab(RoadUiTab.ROUTE),
                    () -> ctx.requestTab(RoadUiTab.CROSS_SECTION),
                    () -> ctx.requestTab(RoadUiTab.VERTICAL_PROFILE)
                });
        }
    }

    private void renderPreviewStatusCard() {
        var preview = ctx.previewManager();
        if (preview.isPreviewJobRunning()) {
            ImGui.textColored(
                PluginUiColors.STATUS_INFO,
                PlotI18n.tr("plugin.road.preview_status_computing"));
            var job = preview.previewJob();
            if (job != null && job.totalWorkUnits() > 0) {
                ImGui.sameLine();
                ImGui.textColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr(
                        "plugin.road.preview_progress_detail",
                        job.processedWorkUnits(),
                        job.totalWorkUnits()));
            }
            ImGui.sameLine();
            if (ImGui.smallButton(PlotI18n.tr("plugin.road.cancel_preview") + "##cancel_preview_inline")) {
                ctx.cancelPreviewJob();
            }
            return;
        }
        if (preview.isBuildPreviewCurrent()) {
            RoadNetworkGenerator.NetworkGenerationResult networkResult =
                preview.getLastNetworkGenerationResult();
            if (networkResult != null && networkResult.hasPartialFailure()) {
                ImGui.textColored(
                    PluginUiColors.ERROR_SOFT,
                    PlotI18n.tr("plugin.road.preview_status_partial_failure"));
                return;
            }
            ImGui.textColored(
                PluginUiColors.STATUS_OK,
                PlotI18n.tr("plugin.road.preview_status_ready"));
            return;
        }
        if (preview.needsPreviewRecalc()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.WARNING_LIGHT,
                PlotI18n.tr("plugin.road.preview_status_stale"));
            return;
        }
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.preview_status_none"));
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
            RoadIntersectionInventory.count(network),
            RoadUiFormat.format(RoadEdgeListHelper.computeNetworkWorldLength(
                network, ctx.host().coordinates()))));
    }

    private void renderPreviewActions(
            RoadNetwork network,
            RoadNetworkValidationReport preflight,
            com.plot.api.world.PlacementReadiness buildReadiness) {
        float half = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;
        boolean hasNetwork = !network.getEdges().isEmpty();
        boolean previewBlocked = !hasNetwork
            || preflight.blocksBuild()
            || ctx.previewManager().isPreviewJobRunning()
            || ctx.networkManager().hasPendingNetworkEdit();

        if (!preflight.items().isEmpty() && !ctx.previewManager().hasValidPreview()) {
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
            if (!networkGenerationResult.getFailClosedJunctionIds().isEmpty()) {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.ERROR_SOFT,
                    PlotI18n.tr(
                        "plugin.road.junction_tunnel_protect_conflict",
                        String.join(", ", networkGenerationResult.getFailClosedJunctionIds())));
            }
            for (String detail : networkGenerationResult.partialFailureDetails()) {
                if (detail == null || detail.isBlank()) {
                    continue;
                }
                String line = detail.length() > 180 ? detail.substring(0, 177) + "..." : detail;
                RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, line);
            }
        } else if (!hasPlacements) {
            ImGui.textColored(PluginUiColors.WARNING_LIGHT, PlotI18n.tr("plugin.road.generate_empty_result"));
        }

        boolean buildDisabled = !hasPlacements
            || !buildReadiness.ready()
            || ctx.host().placement().isBusy()
            || ctx.previewManager().isPreviewJobRunning()
            || ctx.networkManager().hasPendingNetworkEdit()
            || !ctx.previewManager().isBuildPreviewCurrent()
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

    private void renderPreviewDetailsCollapsible(RoadGenerationResult lastGenerationResult) {
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.build.preview_details"))) {
            return;
        }
        ImGui.text(PlotI18n.tr("plugin.road.construction_length_result",
            lastGenerationResult.normalRoadLength,
            lastGenerationResult.bridgeLength,
            lastGenerationResult.tunnelLength));

        RoadNetworkValidationReport validationReport = validationReport();
        RoadNetworkValidationPanel.render(validationReport, ctx);
    }

    private RoadNetworkValidationReport preflightReport(RoadNetwork network) {
        var elevationBounds = RoadElevationBoundsResolver.resolveClientWorld(ctx.host().coordinates());
        long terrainRevision = ctx.previewManager() != null
            ? ctx.previewManager().getTerrainRevision()
            : 0L;
        long key = ctx.networkManager().getNetworkRevision() * 31L
            + terrainRevision * 17L
            + elevationBounds.hashCode();
        if (key != cachedPreflightKey) {
            cachedPreflightReport = RoadNetworkEngineeringValidator.analyzePreGeneration(
                network,
                elevationBounds);
            cachedPreflightKey = key;
        }
        return cachedPreflightReport;
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

    public void renderDeferredModals() {
        // 纵向策略确认弹窗已迁至纵断面工作区。
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
            boolean previewCurrent = ctx.previewManager().isBuildPreviewCurrent();
            if (!previewCurrent) {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.WARNING,
                    PlotI18n.tr("plugin.road.build_blocked_stale_preview"));
            }
            boolean canBuild = readiness.ready()
                && !ctx.host().placement().isBusy()
                && !ctx.previewManager().isPreviewJobRunning()
                && previewCurrent
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
