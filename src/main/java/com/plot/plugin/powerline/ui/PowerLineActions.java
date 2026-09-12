package com.plot.plugin.powerline.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.command.commands.PowerLineGenerateCommand;
import com.plot.core.context.PluginContext;
import com.plot.core.model.Project;
import com.plot.core.model.Shape;
import com.plot.core.persistence.ProjectPathResolver;
import com.plot.core.tool.BaseTool;
import com.plot.core.tool.ToolManager;
import com.plot.plugin.powerline.engineering.validation.PowerLineBuildPolicy;
import com.plot.plugin.powerline.engineering.validation.PowerLineValidationReport;
import com.plot.plugin.powerline.engineering.optimization.OptimizationResult;
import com.plot.api.world.PluginProjectionContext;
import com.plot.api.world.WorldProjectionUnavailableException;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.PowerLinePathSelectionAnalysis;
import com.plot.plugin.powerline.manager.PowerLinePreviewManager;
import com.plot.plugin.powerline.PowerLineGenerator;
import com.plot.plugin.powerline.PowerLinePathUtils;
import com.plot.plugin.powerline.PowerPoleLayoutUtils;
import com.plot.plugin.powerline.path.ClosedLoopLayoutException;
import com.plot.plugin.powerline.path.PowerLinePathLayout;
import com.plot.plugin.powerline.path.PowerLineSourceSync;
import com.plot.plugin.powerline.path.SourceSyncStatus;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.parametric.TowerParametricBuildPolicy;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerLineProject;
import com.plot.core.terrain.MinecraftTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.ui.canvas.Canvas;
import com.plot.utils.PlotI18n;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 电力线路插件应用层动作。 */
public final class PowerLineActions {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/PowerLineActions");

    private final PluginContext host;
    private final PowerLinePluginState state;
    private final Object projectLock;
    private final PowerLinePreviewManager previewManager;
    private final SingleTowerPlacementActions singleTowerPlacement;
    private final PlacedSingleTowerActions placedSingleTowerActions;
    private PowerLineGenerator generator;

    public PowerLineActions(PluginContext host, PowerLinePluginState state, Object projectLock) {
        this.host = Objects.requireNonNull(host, "host");
        this.state = Objects.requireNonNull(state, "state");
        this.projectLock = Objects.requireNonNull(projectLock, "projectLock");
        this.previewManager = new PowerLinePreviewManager(host, state);
        this.singleTowerPlacement = new SingleTowerPlacementActions(host, state, projectLock, previewManager);
        this.placedSingleTowerActions = new PlacedSingleTowerActions(host, state);
    }

    public PowerLinePreviewManager previewManager() {
        return previewManager;
    }

    public SingleTowerPlacementActions singleTowerPlacement() {
        return singleTowerPlacement;
    }

    public PlacedSingleTowerActions placedSingleTowerActions() {
        return placedSingleTowerActions;
    }

    public void setGenerator(PowerLineGenerator generator) {
        this.generator = generator;
    }

    public void updateSelectedPaths() {
        state.setPathSelection(PowerLinePathUtils.analyzeSelection(
            host.appState().getSelectedShapes()));
    }

    public void adoptSelectedPaths() {
        PowerLinePathSelectionAnalysis selection = PowerLinePathUtils.analyzeSelection(
            host.appState().getSelectedShapes());
        state.setPathSelection(selection);

        if (!selection.hasCanvasSelection()) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.adopt_no_selection"),
                ProjectStatusSeverity.WARNING);
            return;
        }
        if (!selection.canAdopt()) {
            state.setProjectStatus(
                selection.rejectedCurves().isEmpty()
                    ? PlotI18n.tr("plugin.powerline.adopt_no_selection")
                    : PlotI18n.tr("plugin.powerline.adopt_reject_curve"),
                ProjectStatusSeverity.WARNING);
            return;
        }

        state.getProjectHistory().push(state.getProject());
        int adopted = 0;
        int skipped = selection.skippedCount();
        List<String> adoptedIds = new ArrayList<>();
        boolean curveRejected = !selection.rejectedCurves().isEmpty();

        for (Shape shape : selection.adoptable()) {
            try {
                PowerLineFootprint line = PowerLinePathLayout.adopt(shape, host.coordinates());
                line.setName(PlotI18n.tr("plugin.powerline.default_name", adopted + 1));
                com.plot.plugin.powerline.style.PowerLineStyleEditor.selectPreset(
                    line,
                    com.plot.plugin.powerline.style.PowerLineStylePresetCatalog.classicWood());
                state.getProject().addLine(line);
                adoptedIds.add(line.getId());
                adopted++;
            } catch (IllegalArgumentException | ClosedLoopLayoutException e) {
                skipped++;
            }
        }

        state.setPathSelection(PowerLinePathSelectionAnalysis.EMPTY);
        if (adopted > 0) {
            state.getSelection().selectAll(adoptedIds);
            invalidatePreview();
        }
        if (adopted == 0) {
            state.setProjectStatus(
                curveRejected
                    ? PlotI18n.tr("plugin.powerline.adopt_reject_curve")
                    : PlotI18n.tr("plugin.powerline.adopt_no_selection"),
                ProjectStatusSeverity.WARNING);
        } else if (skipped > 0) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.adopt_success_partial", adopted, skipped),
                ProjectStatusSeverity.WARNING);
        } else if (adopted > 1) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.adopt_success_batch", adopted),
                ProjectStatusSeverity.SUCCESS);
        } else {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.adopt_success"),
                ProjectStatusSeverity.SUCCESS);
        }
    }

    public boolean relayoutLineFromSource(PowerLineFootprint line) {
        if (line == null || !PowerLineSourceSync.hasLinkedSource(line)) {
            return false;
        }
        Shape liveShape = PowerLineSourceSync.findShape(
            host.appState().getShapes(),
            line.getSourceShapeId());
        if (liveShape == null) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.source_missing"),
                ProjectStatusSeverity.WARNING);
            return false;
        }
        SourceSyncStatus syncStatus = PowerLineSourceSync.resolveStatus(line, liveShape);
        if (syncStatus == SourceSyncStatus.OK) {
            return true;
        }
        if (syncStatus != SourceSyncStatus.STALE) {
            state.setProjectStatus(
                relayoutStatusMessage(syncStatus),
                ProjectStatusSeverity.WARNING);
            return false;
        }
        state.getProjectHistory().push(state.getProject());
        try {
            PowerLineSourceSync.relayout(line, liveShape, host.coordinates());
            invalidatePreview();
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.relayout_success"),
                ProjectStatusSeverity.SUCCESS);
            return true;
        } catch (ClosedLoopLayoutException e) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.adopt_reject_closed_loop"),
                ProjectStatusSeverity.WARNING);
            return false;
        } catch (IllegalArgumentException e) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.source_unsupported"),
                ProjectStatusSeverity.WARNING);
            return false;
        }
    }

    public boolean calculatePreview(PowerLineFootprint line) {
        if (!calculatePreviewCore(line)) {
            return false;
        }
        syncPreviewAnalysis(line);
        return state.getLastGenerationResult() != null;
    }

    /**
     * 地形自动调整：修改线路参数后重新生成预览。仅在确实有地形问题时 push 撤销快照。
     *
     * @return 调整后是否仍有有效预览
     */
    public boolean autoAdjustTerrain(PowerLineFootprint line) {
        if (line == null || !line.isTerrainAvoidanceEnabled()) {
            return false;
        }
        if (getClientWorld() == null || generator == null) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.generate_world_unavailable"),
                ProjectStatusSeverity.ERROR);
            return false;
        }
        if (!hasValidPreview(line) && !calculatePreviewCore(line)) {
            return false;
        }
        PowerLineGenerationResult peek = state.getLastGenerationResult();
        if (peek == null) {
            return false;
        }
        World world = getClientWorld();
        TerrainSampler terrain = MinecraftTerrainSampler.of(world, host.coordinates());
        PowerLineValidationReport peekReport = com.plot.plugin.powerline.engineering.TerrainAvoidance
            .analyzeCollisions(peek.toGeometryModel(), terrain);
        storeTerrainReport(line, peekReport);
        if (!com.plot.plugin.powerline.engineering.TerrainAvoidance.hasTerrainIssues(peekReport)) {
            applyTerrainFixStatus(0, 0);
            syncPreviewAnalysis(line);
            return true;
        }
        state.getProjectHistory().push(state.getProject());
        runTerrainAvoidance(line);
        syncPreviewAnalysis(line);
        return state.getLastGenerationResult() != null;
    }

    private boolean calculatePreviewCore(PowerLineFootprint line) {
        if (singleTowerPlacement.isActive()) {
            singleTowerPlacement.cancelPlacementSilent();
        }
        World world = getClientWorld();
        if (world == null || generator == null) {
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.generate_world_unavailable"), ProjectStatusSeverity.ERROR);
            return false;
        }
        if (line == null) {
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.select_line_hint"), ProjectStatusSeverity.WARNING);
            return false;
        }

        if (PluginProjectionContext.tryCapture(host.coordinates()).isEmpty()) {
            state.setLastGenerationResult(null);
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.projection_unavailable"),
                ProjectStatusSeverity.ERROR);
            return false;
        }

        TerrainSampler terrain = MinecraftTerrainSampler.of(world, host.coordinates());
        PowerLineGenerationResult result;
        try {
            result = generator.generate(line, terrain, designResolver());
        } catch (WorldProjectionUnavailableException e) {
            state.setLastGenerationResult(null);
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.projection_unavailable"),
                ProjectStatusSeverity.ERROR);
            return false;
        } catch (Exception e) {
            LOGGER.error("电力线路预览生成失败: {}", e.getMessage(), e);
            state.setLastGenerationResult(null);
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.generate_empty_result"), ProjectStatusSeverity.WARNING);
            return false;
        }

        if (result.blockCount() == 0) {
            state.setLastGenerationResult(null);
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.generate_empty_result"), ProjectStatusSeverity.WARNING);
            return false;
        }

        state.setLastGenerationResult(result);
        state.setPreviewKey(PowerLinePreviewKey.capture(line, state.getDesignProject(), host.coordinates()));
        previewManager.showLinePreview(result);
        state.setProjectStatus(PlotI18n.tr(
            "plugin.powerline.preview_ready",
            result.poleCount,
            String.format("%.1f", result.wireLength),
            result.warnings.size()),
            result.warnings.isEmpty() ? ProjectStatusSeverity.SUCCESS : ProjectStatusSeverity.WARNING);
        return true;
    }

    public PowerLineValidationReport analyzeTerrainCollisions(PowerLineFootprint line) {
        if (line == null || !line.isTerrainAvoidanceEnabled()) {
            clearTerrainReport();
            return null;
        }
        World world = getClientWorld();
        if (world == null) {
            return null;
        }
        if (!hasValidPreview(line) && !calculatePreviewCore(line)) {
            return null;
        }
        PowerLineValidationReport report = computeTerrainReport(line, world);
        storeTerrainReport(line, report);
        return report;
    }

    public PowerLineValidationReport cachedTerrainReport(PowerLineFootprint line) {
        return validatedReport(
            line,
            state.getValidationState().getLastTerrainReport(),
            state.getValidationState().getTerrainReportKey());
    }

    public PowerLineValidationReport cachedEngineeringReport(PowerLineFootprint line) {
        return validatedReport(
            line,
            state.getValidationState().getLastEngineeringReport(),
            state.getValidationState().getEngineeringReportKey());
    }

    private void syncPreviewAnalysis(PowerLineFootprint line) {
        if (!hasValidPreview(line)) {
            clearAnalysisReports();
            return;
        }
        World world = getClientWorld();
        if (world == null) {
            clearAnalysisReports();
            return;
        }
        if (line.isTerrainAvoidanceEnabled()) {
            storeTerrainReport(line, computeTerrainReport(line, world));
        } else {
            clearTerrainReport();
        }
        if (line.isLineChecksEnabled()) {
            storeEngineeringReport(line, computeEngineeringReport(line, world));
        } else {
            clearEngineeringReport();
        }
    }

    private PowerLineValidationReport computeTerrainReport(PowerLineFootprint line, World world) {
        PowerLineGenerationResult result = state.getLastGenerationResult();
        if (result == null) {
            return null;
        }
        TerrainSampler terrain = MinecraftTerrainSampler.of(world, host.coordinates());
        return com.plot.plugin.powerline.engineering.TerrainAvoidance
            .analyzeCollisions(result.toGeometryModel(), terrain);
    }

    private PowerLineValidationReport computeEngineeringReport(PowerLineFootprint line, World world) {
        PowerLineGenerationResult result = state.getLastGenerationResult();
        if (result == null) {
            return null;
        }
        TerrainSampler terrain = MinecraftTerrainSampler.of(world, host.coordinates());
        return com.plot.plugin.powerline.engineering.validation.PowerLineValidator
            .validate(result.toGeometryModel(), terrain, line);
    }

    private void storeTerrainReport(PowerLineFootprint line, PowerLineValidationReport report) {
        state.getValidationState().setLastTerrainReport(report);
        state.getValidationState().setTerrainReportKey(
            PowerLineAnalysisKey.capture(state.getPreviewKey(), line));
    }

    private void storeEngineeringReport(PowerLineFootprint line, PowerLineValidationReport report) {
        state.getValidationState().setLastEngineeringReport(report);
        state.getValidationState().setEngineeringReportKey(
            PowerLineAnalysisKey.capture(state.getPreviewKey(), line));
    }

    private PowerLineValidationReport validatedReport(
            PowerLineFootprint line,
            PowerLineValidationReport report,
            PowerLineAnalysisKey analysisKey) {
        if (report == null || analysisKey == null || !hasValidPreview(line)) {
            return null;
        }
        PowerLinePreviewKey previewKey = state.getPreviewKey();
        if (previewKey == null
                || !analysisKey.matches(line, state.getDesignProject(), previewKey)) {
            return null;
        }
        return report;
    }

    public void clearAnalysisReports() {
        state.getValidationState().clearAnalysisReports();
    }

    private void clearTerrainReport() {
        state.getValidationState().setLastTerrainReport(null);
        state.getValidationState().setTerrainReportKey(null);
    }

    private void clearEngineeringReport() {
        state.getValidationState().setLastEngineeringReport(null);
        state.getValidationState().setEngineeringReportKey(null);
    }

    private void runTerrainAvoidance(PowerLineFootprint line) {
        if (line == null || !line.isTerrainAvoidanceEnabled()) {
            return;
        }
        World world = getClientWorld();
        if (world == null) {
            return;
        }
        TerrainSampler terrain = MinecraftTerrainSampler.of(world, host.coordinates());
        int fixesApplied = 0;
        for (int attempt = 0; attempt < 4; attempt++) {
            PowerLineGenerationResult result = state.getLastGenerationResult();
            if (result == null) {
                break;
            }
            PowerLineValidationReport report = com.plot.plugin.powerline.engineering.TerrainAvoidance
                .analyzeCollisions(result.toGeometryModel(), terrain);
            storeTerrainReport(line, report);
            if (!com.plot.plugin.powerline.engineering.TerrainAvoidance.hasTerrainIssues(report)) {
                applyTerrainFixStatus(fixesApplied, 0);
                return;
            }
            if (!com.plot.plugin.powerline.engineering.TerrainAvoidance.applyOneFix(
                    line, report, result, designResolver(), host.coordinates())) {
                applyTerrainFixStatus(
                    fixesApplied,
                    com.plot.plugin.powerline.engineering.TerrainAvoidance.countTerrainIssues(report));
                return;
            }
            fixesApplied++;
            if (!calculatePreviewCore(line)) {
                return;
            }
        }
        PowerLineGenerationResult finalResult = state.getLastGenerationResult();
        if (finalResult == null) {
            return;
        }
        PowerLineValidationReport finalReport = com.plot.plugin.powerline.engineering.TerrainAvoidance
            .analyzeCollisions(finalResult.toGeometryModel(), terrain);
        storeTerrainReport(line, finalReport);
        applyTerrainFixStatus(
            fixesApplied,
            com.plot.plugin.powerline.engineering.TerrainAvoidance.countTerrainIssues(finalReport));
    }

    private void applyTerrainFixStatus(int fixesApplied, int remainingIssues) {
        String message = com.plot.plugin.powerline.engineering.TerrainAvoidance
            .resolveStatusMessage(fixesApplied, remainingIssues);
        if (message != null) {
            ProjectStatusSeverity severity = remainingIssues > 0
                ? (fixesApplied > 0 ? ProjectStatusSeverity.WARNING : ProjectStatusSeverity.WARNING)
                : (fixesApplied > 0 ? ProjectStatusSeverity.SUCCESS : ProjectStatusSeverity.INFO);
            state.setProjectStatus(message, severity);
        }
    }

    public void clearPreview() {
        previewManager.clearLineCachedPreview();
    }

    /**
     * 生成参数、线路选择或杆塔设计变更后调用，丢弃过期预览。
     */
    public void invalidatePreview() {
        if (previewManager.isSingleTowerInteractive()) {
            if (state.getLastGenerationResult() != null || state.getPreviewKey() != null) {
                previewManager.clearLineCachedPreview();
                state.setProjectStatus(
                    PlotI18n.tr("plugin.powerline.preview_invalidated"),
                    ProjectStatusSeverity.INFO);
            }
            return;
        }
        if (state.getLastGenerationResult() != null || state.getPreviewKey() != null) {
            previewManager.clearLineCachedPreview();
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.preview_invalidated"),
                ProjectStatusSeverity.INFO);
        }
    }

    public boolean isPreviewValidFor(PowerLineFootprint line) {
        PowerLinePreviewKey key = state.getPreviewKey();
        PowerLineGenerationResult result = state.getLastGenerationResult();
        if (key == null || result == null || line == null) {
            return false;
        }
        if (result.footprint == null || !line.getId().equals(result.footprint.getId())) {
            return false;
        }
        return key.matches(line, state.getDesignProject(), host.coordinates());
    }

    public boolean hasValidPreview(PowerLineFootprint line) {
        return isPreviewValidFor(line) && state.getLastGenerationResult() != null;
    }

    /**
     * 若缓存预览与当前线路/参数不一致则静默清除（生成页每帧调用）。
     */
    public void syncPreviewValidity(PowerLineFootprint line) {
        if (state.getLastGenerationResult() == null) {
            return;
        }
        if (!isPreviewValidFor(line)) {
            clearPreview();
        }
    }

    public void selectLine(String lineId, boolean multiToggle) {
        String previousPrimary = state.getSelection().primaryId();
        state.getSelection().select(lineId, multiToggle);
        if (!state.getSelection().primaryId().equals(previousPrimary)) {
            invalidatePreview();
        }
    }

    public void selectAll(java.util.Collection<String> ids) {
        String previousPrimary = state.getSelection().primaryId();
        state.getSelection().selectAll(ids);
        if (!state.getSelection().primaryId().equals(previousPrimary)) {
            invalidatePreview();
        }
    }

    public void clearSelection() {
        if (!state.getSelection().isEmpty()) {
            state.getSelection().clear();
            invalidatePreview();
        }
    }

    public void buildInWorld() {
        PowerLineFootprint line = state.getSelection().primary(state.getProject());
        if (!ensurePreviewReadyForBuild(line)) {
            return;
        }
        if (PowerLineBuildPolicy.hasBlockingIssues(
                line,
                cachedEngineeringReport(line),
                cachedTerrainReport(line))) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.build_blocked_validation"),
                ProjectStatusSeverity.ERROR);
            return;
        }
        if (hasParametricBuildBlocking(line)) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.build_blocked_parametric"),
                ProjectStatusSeverity.ERROR);
            return;
        }

        PowerLineGenerationResult resultSnapshot;
        synchronized (projectLock) {
            PowerLineGenerationResult last = state.getLastGenerationResult();
            if (last == null || last.placementRecords.isEmpty()) {
                state.setProjectStatus(PlotI18n.tr("plugin.powerline.build_no_blocks"), ProjectStatusSeverity.WARNING);
                return;
            }
            resultSnapshot = last;
        }

        com.plot.api.world.PlacementReadiness readiness = host.projection().checkWorldModificationReadiness();
        if (!readiness.ready()) {
            state.setProjectStatus(readiness.message(), ProjectStatusSeverity.ERROR);
            return;
        }
        if (host.placement().isBusy()) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.build_in_progress_wait"),
                ProjectStatusSeverity.WARNING);
            return;
        }

        List<BlockRecord> records = new ArrayList<>(resultSnapshot.placementRecords.values());
        PowerLineGenerateCommand command = new PowerLineGenerateCommand(records, host.projection(), host.placement());
        state.setProjectStatus(
            PlotI18n.tr("plugin.powerline.build_in_progress", records.size()),
            ProjectStatusSeverity.INFO);
        command.executeScheduled(() -> PowerLineUiExecutor.runOnClientThread(() -> {
            PowerLineGenerateCommand.ExecutionResult result = command.getLastExecutionResult();
            if (command.hasAppliedRecords()) {
                host.commands().pushExecuted(command);
            }
            if (result != null && result.isFullSuccess()) {
                state.setProjectStatus(PlotI18n.tr("plugin.powerline.build_success", result.success()), ProjectStatusSeverity.SUCCESS);
            } else if (result != null && result.success() > 0) {
                state.setProjectStatus(PlotI18n.tr(
                    "plugin.powerline.build_partial",
                    result.success(),
                    result.total()), ProjectStatusSeverity.WARNING);
            } else if (result != null && result.cancelled()) {
                state.setProjectStatus(PlotI18n.tr(
                    "plugin.powerline.build_cancelled",
                    result.success(),
                    result.total()), ProjectStatusSeverity.WARNING);
            }
            clearPreview();
        }));
    }

    private boolean ensurePreviewReadyForBuild(PowerLineFootprint line) {
        syncPreviewValidity(line);
        if (line == null) {
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.select_line_hint"), ProjectStatusSeverity.WARNING);
            return false;
        }
        if (!hasValidPreview(line)) {
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.preview_stale"), ProjectStatusSeverity.WARNING);
            return false;
        }
        return true;
    }

    public boolean requestBuildConfirm(PowerLineFootprint line) {
        return ensurePreviewReadyForBuild(line);
    }

    public void locateLine(PowerLineFootprint line) {
        if (line == null || line.getPathPoints().isEmpty()) {
            return;
        }
        Vec2d centroid = computeCentroid(line.getPathPoints());
        Canvas canvas = com.plot.ui.canvas.CanvasAccess.get();
        if (canvas != null && canvas.getCamera() != null) {
            canvas.getCamera().setOffset(centroid);
            selectLine(line.getId(), false);
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.locate_success", line.getName()), ProjectStatusSeverity.SUCCESS);
        }
    }

    private static Vec2d computeCentroid(List<Vec2d> points) {
        double x = 0.0;
        double y = 0.0;
        for (Vec2d point : points) {
            x += point.x;
            y += point.y;
        }
        return new Vec2d(x / points.size(), y / points.size());
    }

    public boolean hasMinSpacingWarning(PowerLineFootprint line) {
        return closestMandatorySpacingViolation(line).isPresent();
    }

    /**
     * 相邻必需杆塔间距小于美观建议时，返回最短那段距离（格）。
     * 转角等硬规则杆位不会被删除，仅用于提示。
     */
    public java.util.OptionalDouble closestMandatorySpacingViolation(PowerLineFootprint line) {
        if (line == null) {
            return java.util.OptionalDouble.empty();
        }
        List<Vec2d> mandatory = collectMandatoryPoles(line);
        double closest = Double.MAX_VALUE;
        for (int i = 1; i < mandatory.size(); i++) {
            double span = host.coordinates().projectedDistance(mandatory.get(i - 1), mandatory.get(i));
            if (span < line.getMinPoleSpacing()) {
                closest = Math.min(closest, span);
            }
        }
        return closest < Double.MAX_VALUE
            ? java.util.OptionalDouble.of(closest)
            : java.util.OptionalDouble.empty();
    }

    private List<Vec2d> collectMandatoryPoles(PowerLineFootprint line) {
        List<Vec2d> path = line.getPathPoints();
        List<Vec2d> mandatory = new ArrayList<>();
        mandatory.add(path.getFirst());
        for (int i = 1; i < path.size() - 1; i++) {
            if (PowerPoleLayoutUtils.isCorner(path, i, line.getCornerAngleThreshold())) {
                mandatory.add(path.get(i));
            }
        }
        mandatory.add(path.getLast());
        return mandatory;
    }

    public void deleteLines(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        state.getProjectHistory().push(state.getProject());
        for (String id : ids) {
            state.getProject().removeLine(id);
            state.getSelection().retainExisting(state.getProject());
        }
        invalidatePreview();
        state.setProjectStatus(
            PlotI18n.tr("plugin.powerline.deleted", ids.size()),
            ProjectStatusSeverity.SUCCESS);
    }

    public PoleDesignResolver designResolver() {
        return new PoleDesignResolver(state.getDesignProject());
    }

    public boolean hasParametricBuildBlocking(PowerLineFootprint line) {
        net.minecraft.world.World world = getClientWorld();
        TerrainSampler terrain = world != null
            ? MinecraftTerrainSampler.of(world, host.coordinates())
            : null;
        return TowerParametricBuildPolicy.hasBlockingIssues(
            line,
            designResolver(),
            terrain,
            host.coordinates());
    }

    public void onProjectLoaded(String filePath, Path projectsDir, Path designProjectsDir) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        String targetFile = ProjectPathResolver.sidecarFileName(filePath);
        Path file = projectsDir.resolve(targetFile);
        boolean loaded = loadProjectFile(file);
        boolean designsLoaded = loadDesignProjectFile(designProjectsDir.resolve(targetFile));
        if (loaded) {
            state.setCurrentProjectFile(targetFile);
            if (designsLoaded) {
                state.setProjectStatus(
                    PlotI18n.tr("plugin.powerline.project.loaded", filePath),
                    ProjectStatusSeverity.SUCCESS);
            } else {
                state.setProjectStatus(
                    PlotI18n.tr("plugin.powerline.project.loaded_designs_failed", filePath),
                    ProjectStatusSeverity.WARNING);
            }
        }
    }

    public void onProjectSaved(String filePath, Path projectsDir, Path designProjectsDir) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        state.setCurrentProjectFile(ProjectPathResolver.sidecarFileName(filePath));
        boolean saved = saveProjectFile(projectsDir.resolve(state.getCurrentProjectFile()));
        saveDesignProjectFile(designProjectsDir.resolve(state.getCurrentProjectFile()));
        if (saved) {
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.project.saved", filePath), ProjectStatusSeverity.SUCCESS);
        }
    }

    public void persistProject(Path projectsDir, Path designProjectsDir) {
        saveProjectFile(projectsDir.resolve(state.getCurrentProjectFile()));
        saveDesignProjectFile(designProjectsDir.resolve(state.getCurrentProjectFile()));
    }

    public boolean loadProjectFile(Path file) {
        try {
            PowerLineProject loaded = PowerLineProject.loadFrom(file);
            state.setProject(loaded);
            state.getProjectHistory().clear();
            state.getSelection().clear();
            if (!state.getProject().getLines().isEmpty()) {
                state.getSelection().select(state.getProject().getLines().keySet().iterator().next(), false);
            }
            resetAfterProjectLoad();
            return true;
        } catch (IOException e) {
            LOGGER.error("加载电力线路项目失败: {}", e.getMessage(), e);
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.project.load_failed", file.getFileName()), ProjectStatusSeverity.ERROR);
            return false;
        }
    }

    public void loadProjectForCurrentProject(Path projectsDir, Path designProjectsDir, String defaultProjectFile) {
        Project current = host.appState().getCurrentProject();
        if (current != null && current.getFilePath() != null && !current.getFilePath().isBlank()) {
            onProjectLoaded(current.getFilePath(), projectsDir, designProjectsDir);
            return;
        }
        if (loadProjectFile(projectsDir.resolve(defaultProjectFile))) {
            state.setCurrentProjectFile(defaultProjectFile);
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.project.default_loaded"),
                ProjectStatusSeverity.SUCCESS);
        }
        loadDesignProjectFile(designProjectsDir.resolve(defaultProjectFile));
    }

    private void resetAfterProjectLoad() {
        state.setLineNameEditingId("");
        state.setPathSelection(PowerLinePathSelectionAnalysis.EMPTY);
        state.setPoleDesignerOpen(false);
        state.setPoleDesignerEditingId("");
        state.getDesignDraftHistory().clear();
        state.clearPlacedSingleTowerSelection();
        state.setPendingDeletePlacedSingleTowerId("");
        state.setPlacedSingleTowerDeleteConfirmPending(false);
        invalidatePreview();
    }

    private boolean loadDesignProjectFile(Path file) {
        try {
            state.setDesignProject(PowerLineDesignProject.loadFrom(file));
            state.getDesignContentFingerprint().reset();
            return true;
        } catch (IOException e) {
            LOGGER.error("加载杆塔设计工程失败: {}", e.getMessage(), e);
            state.setDesignProject(new PowerLineDesignProject());
            return false;
        }
    }

    private boolean saveDesignProjectFile(Path file) {
        if (file == null || state.getDesignProject() == null) {
            return false;
        }
        try {
            String json = state.getDesignProject().toJson();
            if (state.getDesignContentFingerprint().isUnchanged(json, file)) {
                return true;
            }
            state.getDesignProject().saveTo(file);
            state.getDesignContentFingerprint().markSaved(json, file);
            return true;
        } catch (IOException e) {
            LOGGER.error("保存杆塔设计工程失败: {}", e.getMessage(), e);
            return false;
        }
    }

    public void savePoleDesign(PoleDesign design) {
        if (design == null) {
            return;
        }
        state.getDesignProject().addDesign(design.copy());
        if (isDesignReferencedByAnyLine(design.getId())) {
            invalidatePreview();
        }
        state.setProjectStatus(PlotI18n.tr("plugin.powerline.design.saved", design.getName()), ProjectStatusSeverity.SUCCESS);
    }

    private boolean isDesignReferencedByAnyLine(String designId) {
        if (designId == null || designId.isBlank()) {
            return false;
        }
        for (PowerLineFootprint line : state.getProject().getLines().values()) {
            if (designId.equals(line.getPoleDesignId())) {
                return true;
            }
        }
        return false;
    }

    private boolean saveProjectFile(Path file) {
        if (file == null || state.getProject() == null) {
            return false;
        }
        try {
            String json = state.getProject().toJson();
            if (state.getContentFingerprint().isUnchanged(json, file)) {
                return true;
            }
            state.getProject().saveTo(file);
            state.getContentFingerprint().markSaved(json, file);
            return true;
        } catch (IOException e) {
            LOGGER.error("保存电力线路项目失败: {}", e.getMessage(), e);
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.project.save_failed", file.getFileName()), ProjectStatusSeverity.ERROR);
            return false;
        }
    }

    public void activatePathPickTool() {
        ToolManager toolManager = host.tools();
        var selectTool = toolManager.getTool("select");
        if (!(selectTool instanceof BaseTool baseTool)) {
            return;
        }
        state.setPathSelection(PowerLinePathSelectionAnalysis.EMPTY);
        toolManager.setActiveTool(selectTool);
        host.appState().setCurrentTool(baseTool);
        state.setProjectStatus(
            PlotI18n.tr("plugin.powerline.pick_started"),
            ProjectStatusSeverity.INFO);
    }

    public PowerLineValidationReport analyzeEngineering(PowerLineFootprint line) {
        if (line == null) {
            return null;
        }
        if (!line.isLineChecksEnabled()) {
            clearEngineeringReport();
            return null;
        }
        World world = getClientWorld();
        if (world == null) {
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.generate_world_unavailable"), ProjectStatusSeverity.ERROR);
            return null;
        }
        if (!hasValidPreview(line) && !calculatePreviewCore(line)) {
            return null;
        }
        PowerLineValidationReport report = computeEngineeringReport(line, world);
        storeEngineeringReport(line, report);
        return report;
    }

    public OptimizationResult proposeAutoTowerSelection(PowerLineFootprint line) {
        if (line == null) {
            return null;
        }
        if (!hasValidPreview(line) && !calculatePreview(line)) {
            return null;
        }
        PowerLineGenerationResult result = state.getLastGenerationResult();
        if (result == null) {
            return null;
        }
        OptimizationResult optimization = com.plot.plugin.powerline.engineering.optimization.AutoTowerOptimizationProposer
            .propose(result, line, designResolver());
        state.getValidationState().setPendingOptimization(optimization);
        return optimization;
    }

    public OptimizationResult proposeClearanceFix(PowerLineFootprint line) {
        PowerLineValidationReport report = cachedEngineeringReport(line);
        if (report == null) {
            report = analyzeEngineering(line);
        }
        if (report == null || line == null) {
            return null;
        }
        PowerLineGenerationResult result = state.getLastGenerationResult();
        com.plot.plugin.powerline.engineering.optimization.LineOptimizationEngine.PowerLineGeometrySites sites;
        if (result != null) {
            java.util.List<String> resolvedIds = new java.util.ArrayList<>();
            for (var placement : result.polePlacements) {
                resolvedIds.add(placement.resolvedDesignId());
            }
            sites = new com.plot.plugin.powerline.engineering.optimization.LineOptimizationEngine.PowerLineGeometrySites(
                result.poleSites,
                resolvedIds);
        } else {
            sites = new com.plot.plugin.powerline.engineering.optimization.LineOptimizationEngine.PowerLineGeometrySites(
                com.plot.plugin.powerline.PowerPoleLayoutUtils.computePoleSites(line, host.coordinates()));
        }
        OptimizationResult optimization = com.plot.plugin.powerline.engineering.optimization.LineOptimizationEngine
            .propose(
                report,
                sites,
                line,
                com.plot.plugin.powerline.engineering.validation.ValidationLimits.DEFAULT_MIN_GROUND_CLEARANCE,
                com.plot.plugin.powerline.engineering.validation.ValidationLimits.TOWER_PREFERRED_HEIGHT_MARGIN,
                designResolver());
        state.getValidationState().setPendingOptimization(optimization);
        return optimization;
    }

    public OptimizationResult proposeOptimization(PowerLineFootprint line) {
        return proposeClearanceFix(line);
    }

    public void applyPendingOptimization(PowerLineFootprint line) {
        OptimizationResult optimization = state.getValidationState().getPendingOptimization();
        if (optimization == null || line == null) {
            state.getValidationState().clearOptimization();
            return;
        }
        if (optimization.getActions().isEmpty()) {
            state.getValidationState().clearOptimization();
            return;
        }
        state.getProjectHistory().push(state.getProject());
        if (state.getValidationState().isPendingEnableAutomaticTowers()) {
            line.setAutomaticTowerSelectionEnabled(true);
        }
        for (com.plot.plugin.powerline.engineering.optimization.OptimizationAction action : optimization.getActions()) {
            switch (action.getType()) {
                case SELECT_TALLER_TOWER -> com.plot.plugin.powerline.PowerLineOverrideUtils.setDesignOverride(
                    line,
                    action.getStationing(),
                    action.getProposedDesignId());
                case INSERT_POLE -> line.addLayoutConstraint(
                    new com.plot.plugin.powerline.model.PoleLayoutConstraint(
                        action.getStationing(),
                        layoutConstraintReason(action)));
                default -> { }
            }
        }
        state.getValidationState().clearOptimization();
        invalidatePreview();
        state.setProjectStatus(
            PlotI18n.tr("plugin.powerline.engineering.applied"),
            ProjectStatusSeverity.SUCCESS);
    }

    private World getClientWorld() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null ? client.world : null;
    }

    private static String relayoutStatusMessage(SourceSyncStatus status) {
        return switch (status) {
            case MISSING -> PlotI18n.tr("plugin.powerline.source_missing");
            case UNSUPPORTED -> PlotI18n.tr("plugin.powerline.source_unsupported");
            case DEGENERATE -> PlotI18n.tr("plugin.powerline.source_degenerate");
            default -> PlotI18n.tr("plugin.powerline.source_stale");
        };
    }

    private static String layoutConstraintReason(
            com.plot.plugin.powerline.engineering.optimization.OptimizationAction action) {
        if (action.getMessageKey() != null && !action.getMessageKey().isBlank()) {
            return action.getMessageKey();
        }
        if (action.getMessage() != null && !action.getMessage().isBlank()) {
            return action.getMessage();
        }
        return "plugin.powerline.route.auto_pole.reason.engineering";
    }
}
