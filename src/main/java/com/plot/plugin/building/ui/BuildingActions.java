package com.plot.plugin.building.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.GhostBlockOwners;
import com.plot.core.command.BlockRecord;
import com.plot.core.command.commands.BuildingGenerateCommand;
import com.plot.core.context.PluginContext;
import com.plot.core.model.Project;
import com.plot.core.model.Shape;
import com.plot.core.persistence.ProjectPathResolver;
import com.plot.core.tool.BaseTool;
import com.plot.core.tool.ToolManager;
import com.plot.plugin.building.BuildingBlockCountCache;
import com.plot.plugin.building.BuildingFootprintPickSession;
import com.plot.plugin.building.BuildingFootprintSelectionAnalysis;
import com.plot.plugin.building.BuildingFootprintValidator;
import com.plot.plugin.building.BuildingGenerator;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.BuildingBatchEditor;
import com.plot.plugin.building.BuildingHeightDistribution;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.DistrictBuildReport;
import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.persistence.BuildingProjectLoadResult;
import com.plot.plugin.building.model.persistence.BuildingProjectPersistence;
import com.plot.ui.canvas.Canvas;
import com.plot.utils.PlotI18n;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 建筑插件应用层动作：认领、预览、生成、持久化。
 * <p>
 * Panel 应 {@code render → invoke} 本类，而非直接串联 Project / Generator / Minecraft。
 */
public final class BuildingActions {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/BuildingActions");
    /** 每帧上传的 ghost 方块数，避免预览完成后单帧卡死。 */
    private static final int GHOST_BLOCKS_PER_TICK = 6_000;

    private final PluginContext host;
    private final BuildingPluginState state;
    private final Object projectLock;
    private BuildingGenerator buildingGenerator;
    private List<Map.Entry<BlockPos, String>> pendingGhostUpload;
    private int pendingGhostUploadIndex;
    private int pendingGhostUploadTotal;
    private boolean pendingGhostClear = true;

    public BuildingActions(
            PluginContext host,
            BuildingPluginState state,
            Object projectLock) {
        this.host = Objects.requireNonNull(host, "host");
        this.state = Objects.requireNonNull(state, "state");
        this.projectLock = Objects.requireNonNull(projectLock, "projectLock");
    }

    public void setBuildingGenerator(BuildingGenerator buildingGenerator) {
        this.buildingGenerator = buildingGenerator;
    }

    public BuildingGenerator buildingGenerator() {
        return buildingGenerator;
    }

    public void syncSelectedBuildingAfterHistory() {
        state.getSelection().retainExisting(state.getProject());
        state.setBuildingNameEditingId("");
    }

    public void resetAfterProjectLoad() {
        state.setBuildingNameEditingId("");
        state.getPickSession().cancel();
        state.getSelectedFootprints().clear();
        cancelDistrictPreviewJob();
        clearPreview();
        state.setLastDistrictBuildReport(null);
    }

    public boolean calculatePreview(BuildingFootprint building) {
        return calculateDistrictPreview(List.of(building), false, false);
    }

    public boolean calculateDistrictPreview(
            List<BuildingFootprint> buildings,
            boolean autoProjectGhosts) {
        return calculateDistrictPreview(buildings, autoProjectGhosts, false);
    }

    /**
     * @param buildConfirmOnComplete 分帧 job 完成后是否弹出落地确认（仅多栋有效）
     * @return 同步路径是否已有可落地预览；分帧路径恒为 false，完成时由 job 回调
     */
    public boolean calculateDistrictPreview(
            List<BuildingFootprint> buildings,
            boolean autoProjectGhosts,
            boolean buildConfirmOnComplete) {
        World world = getClientWorld();
        if (world == null || buildingGenerator == null) {
            state.setProjectStatus(PlotI18n.tr("plugin.building.generate_world_unavailable"));
            return false;
        }
        if (buildings == null || buildings.isEmpty()) {
            state.setProjectStatus(PlotI18n.tr("plugin.building.select_building_hint"));
            return false;
        }
        startDistrictPreviewJob(buildings, autoProjectGhosts, buildConfirmOnComplete);
        return false;
    }

    private void startDistrictPreviewJob(
            List<BuildingFootprint> buildings,
            boolean autoProjectGhosts,
            boolean buildConfirmOnComplete) {
        cancelDistrictPreviewJob();
        clearBuildingGhosts();
        state.setLastDistrictResult(null);
        state.setLastGenerationResult(null);
        BuildingMassingPreviewHeights.clearCache(state);
        state.setDistrictPreviewBuildConfirmPending(buildConfirmOnComplete);
        DistrictPreviewJob job = new DistrictPreviewJob(
            buildings,
            autoProjectGhosts,
            buildConfirmOnComplete,
            this);
        state.setDistrictPreviewJob(job);
    }

    public void tickDistrictPreviewJob() {
        DistrictPreviewJob job = state.getDistrictPreviewJob();
        if (job != null && job.isRunning()) {
            job.tick();
        }
    }

    public void tickGhostProjection() {
        if (pendingGhostUpload == null) {
            return;
        }
        com.plot.api.world.IGhostBlockService ghostBlockManager = host.ghosts();
        if (ghostBlockManager == null) {
            cancelGhostProjection();
            return;
        }
        if (pendingGhostClear) {
            ghostBlockManager.clearGhostBlocks(GhostBlockOwners.BUILDING);
            pendingGhostClear = false;
        }
        int end = Math.min(pendingGhostUploadIndex + GHOST_BLOCKS_PER_TICK, pendingGhostUpload.size());
        if (end > pendingGhostUploadIndex) {
            Map<BlockPos, String> batch = new LinkedHashMap<>(end - pendingGhostUploadIndex);
            for (int i = pendingGhostUploadIndex; i < end; i++) {
                Map.Entry<BlockPos, String> entry = pendingGhostUpload.get(i);
                batch.put(entry.getKey(), entry.getValue());
            }
            ghostBlockManager.addGhostBlocks(GhostBlockOwners.BUILDING, batch);
            pendingGhostUploadIndex = end;
        }
        if (pendingGhostUploadIndex >= pendingGhostUpload.size()) {
            pendingGhostUpload = null;
            pendingGhostUploadIndex = 0;
            pendingGhostUploadTotal = 0;
        } else {
            state.setProjectStatus(PlotI18n.tr(
                "plugin.building.generate.ghost_uploading",
                pendingGhostUploadIndex,
                pendingGhostUploadTotal));
        }
    }

    public int ghostProjectionProcessed() {
        return pendingGhostUploadIndex;
    }

    public int ghostProjectionTotal() {
        return pendingGhostUploadTotal;
    }

    public boolean isDistrictPreviewBusy() {
        DistrictPreviewJob job = state.getDistrictPreviewJob();
        return job != null && job.isRunning();
    }

    public boolean isGhostProjectionBusy() {
        return pendingGhostUpload != null;
    }

    public void cancelDistrictPreviewJob() {
        DistrictPreviewJob job = state.getDistrictPreviewJob();
        if (job != null) {
            job.cancel();
        }
        state.setDistrictPreviewJob(null);
        state.setDistrictPreviewBuildConfirmPending(false);
    }

    void failDistrictPreviewJob(DistrictPreviewJob job) {
        if (job != null) {
            job.cancel();
        }
        state.setDistrictPreviewJob(null);
        state.setDistrictPreviewBuildConfirmPending(false);
        state.setLastDistrictResult(null);
        state.setLastGenerationResult(null);
        clearPreviewDiagnostics();
        state.setProjectStatus(PlotI18n.tr("plugin.building.generate_world_unavailable"));
    }

    public BuildingPreviewIdentity.Validity previewValidity(List<BuildingFootprint> targets) {
        BuildingPreviewIdentity identity = state.getPreviewIdentity();
        boolean hasResult = hasPreviewResult();
        if (identity == null) {
            return hasResult
                ? BuildingPreviewIdentity.Validity.STALE
                : BuildingPreviewIdentity.Validity.NONE;
        }
        return identity.validityAgainst(targets, hasResult);
    }

    public boolean hasPreviewResult() {
        DistrictGenerationResult district = state.getLastDistrictResult();
        if (district != null && district.buildingsAttempted() > 0) {
            return district.hasPlacements();
        }
        BuildingGenerationResult generation = state.getLastGenerationResult();
        return generation != null && !generation.placementRecords.isEmpty();
    }

    public void requestBuildFromCurrentPreview(List<BuildingFootprint> targets) {
        if (previewValidity(targets) != BuildingPreviewIdentity.Validity.VALID) {
            state.setProjectStatus(PlotI18n.tr("plugin.building.generate.preview_stale"));
            return;
        }
        state.setBuildConfirmPending(true);
    }

    void completeDistrictPreviewJob(
            DistrictPreviewJob job,
            DistrictGenerationResult district,
            boolean autoProjectGhosts,
            boolean buildConfirmOnComplete) {
        if (state.getDistrictPreviewJob() != job) {
            return;
        }
        state.setDistrictPreviewJob(null);
        state.setDistrictPreviewBuildConfirmPending(false);
        boolean ready = applyDistrictPreviewResult(
            job.buildings(),
            district,
            autoProjectGhosts,
            buildConfirmOnComplete);
        if (buildConfirmOnComplete && ready) {
            state.setBuildConfirmPending(true);
        }
    }

    private boolean applyDistrictPreviewResult(
            List<BuildingFootprint> previewTargets,
            DistrictGenerationResult district,
            boolean autoProjectGhosts,
            boolean buildConfirmOnComplete) {
        state.setLastDistrictResult(district);
        state.setLastGenerationResult(district.toMergedResult());
        if (!district.hasPlacements()) {
            clearPreviewDiagnostics();
            state.setProjectStatus(PlotI18n.tr("plugin.building.generate_empty_result"));
            return false;
        }

        state.setPreviewIdentity(BuildingPreviewIdentity.capture(previewTargets));
        BuildingMassingPreviewHeights.rebuildCache(
            state,
            district,
            state.getLastGenerationResult(),
            previewTargets);
        updateOverlayDiagnostics(previewTargets, district);

        if (autoProjectGhosts) {
            projectPreview();
        } else {
            clearBuildingGhosts();
        }

        return true;
    }

    public void projectPreview() {
        BuildingGenerationResult lastGenerationResult = state.getLastGenerationResult();
        if (lastGenerationResult == null || lastGenerationResult.placementRecords.isEmpty()) {
            return;
        }
        if (host.ghosts() == null) {
            return;
        }
        cancelGhostProjection();
        pendingGhostUpload = new ArrayList<>(lastGenerationResult.placementRecords.size());
        for (BlockRecord record : lastGenerationResult.placementRecords.values()) {
            pendingGhostUpload.add(Map.entry(record.pos, record.newBlockId));
        }
        pendingGhostUploadIndex = 0;
        pendingGhostUploadTotal = pendingGhostUpload.size();
        pendingGhostClear = true;
        if (pendingGhostUploadTotal > 0) {
            state.setProjectStatus(PlotI18n.tr(
                "plugin.building.generate.ghost_uploading",
                0,
                pendingGhostUploadTotal));
        }
    }

    public void clearPreview() {
        cancelDistrictPreviewJob();
        clearBuildingGhosts();
        state.setLastGenerationResult(null);
        state.setLastDistrictResult(null);
        clearPreviewDiagnostics();
    }

    private void clearBuildingGhosts() {
        cancelGhostProjection();
        com.plot.api.world.IGhostBlockService ghostBlockManager = host.ghosts();
        if (ghostBlockManager != null) {
            ghostBlockManager.clearGhostBlocks(GhostBlockOwners.BUILDING);
        }
    }

    private void cancelGhostProjection() {
        pendingGhostUpload = null;
        pendingGhostUploadIndex = 0;
        pendingGhostUploadTotal = 0;
        pendingGhostClear = true;
    }

    private void clearPreviewDiagnostics() {
        state.setPreviewIdentity(null);
        state.setOverlayPreviewedBuildingIds(java.util.Set.of());
        state.setOverlayWarningBuildingIds(java.util.Set.of());
        BuildingMassingPreviewHeights.clearCache(state);
    }

    private void updateOverlayDiagnostics(
            List<BuildingFootprint> previewTargets,
            DistrictGenerationResult district) {
        boolean districtMode = district.buildingsAttempted() > 1;
        String singleId = !districtMode && previewTargets.size() == 1
            ? previewTargets.getFirst().getId()
            : "";
        List<BuildingGenerationIssues.Issue> issues = BuildingGenerationIssues.collect(
            state.getProject(),
            district,
            state.getLastGenerationResult(),
            state.getPreviewIdentity(),
            districtMode);
        state.setOverlayPreviewedBuildingIds(
            BuildingGenerationIssues.previewedBuildingIds(district, singleId));
        state.setOverlayWarningBuildingIds(BuildingGenerationIssues.warningBuildingIds(issues));
    }

    public void invalidatePreview() {
        if (!hasPreviewResult()) {
            return;
        }
        markPreviewStale();
        state.setProjectStatus(PlotI18n.tr("plugin.building.preview_invalidated"));
    }

    private void markPreviewStale() {
        state.setPreviewIdentity(null);
        state.setOverlayPreviewedBuildingIds(java.util.Set.of());
        state.setOverlayWarningBuildingIds(java.util.Set.of());
        clearBuildingGhosts();
    }

    private List<BuildingFootprint> resolveGenerateTargets() {
        if (state.isGenerateScopeAll()) {
            return new ArrayList<>(state.getProject().getBuildings().values());
        }
        return state.getSelection().resolve(state.getProject());
    }

    public void applyHeightDistribution(List<BuildingFootprint> targets) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        state.getProjectHistory().push(state.getProject());
        long seed = state.getHeightDistMode() == BuildingHeightDistribution.Mode.RANDOM
            ? resolveHeightDistSeed(targets)
            : 0L;
        BuildingHeightDistribution.Settings settings = BuildingHeightDistribution.Settings.of(
            state.getHeightDistMode(),
            state.getHeightDistMinFloors(),
            state.getHeightDistMaxFloors(),
            seed);
        BuildingHeightDistribution.ApplyResult result =
            BuildingHeightDistribution.apply(targets, settings);
        invalidatePreview();
        state.setProjectStatus(PlotI18n.tr(
            "plugin.building.height_distribution_applied",
            result.updated(),
            PlotI18n.tr("plugin.building.height_mode." + state.getHeightDistMode().name().toLowerCase())));
    }

    private long resolveHeightDistSeed(List<BuildingFootprint> targets) {
        if (state.isHeightDistSeedManual()) {
            return state.getHeightDistSeed();
        }
        long seed = BuildingHeightDistribution.defaultSeed(state.getProject(), targets);
        state.setHeightDistSeed(seed);
        return seed;
    }

    public void applyMassingToSelected(BuildingFootprint primary, List<BuildingFootprint> targets) {
        if (primary == null || targets == null || targets.isEmpty()) {
            return;
        }
        state.getProjectHistory().push(state.getProject());
        BuildingBatchEditor.ApplyResult result =
            BuildingBatchEditor.apply(primary, targets, state.getBatchFieldMask());
        invalidatePreview();
        state.setProjectStatus(PlotI18n.tr("plugin.building.batch_apply_success", result.updated()));
    }

    public void buildInWorld() {
        final BuildingGenerationResult resultSnapshot;
        final DistrictGenerationResult districtSnapshot;
        synchronized (projectLock) {
            List<BuildingFootprint> targets = resolveGenerateTargets();
            if (previewValidity(targets) != BuildingPreviewIdentity.Validity.VALID) {
                state.setProjectStatus(PlotI18n.tr("plugin.building.generate.build_stale"));
                return;
            }
            BuildingGenerationResult lastGenerationResult = state.getLastGenerationResult();
            if (lastGenerationResult == null || lastGenerationResult.placementRecords.isEmpty()) {
                state.setProjectStatus(PlotI18n.tr("plugin.building.build_no_blocks"));
                return;
            }
            resultSnapshot = lastGenerationResult;
            districtSnapshot = state.getLastDistrictResult();
        }

        com.plot.api.world.PlacementReadiness readiness =
            host.projection().checkWorldModificationReadiness();
        if (!readiness.ready()) {
            state.setProjectStatus(readiness.message());
            return;
        }

        if (host.placement().isBusy()) {
            state.setProjectStatus(PlotI18n.tr("plugin.building.build_in_progress_wait"));
            return;
        }

        List<BlockRecord> records = new ArrayList<>(resultSnapshot.placementRecords.values());
        BuildingGenerateCommand command = new BuildingGenerateCommand(records, host.projection(), host.placement());
        if (districtSnapshot != null && districtSnapshot.buildingsAttempted() > 1) {
            state.setProjectStatus(PlotI18n.tr(
                "plugin.building.district_build_in_progress",
                districtSnapshot.buildingsGenerated(),
                records.size()));
        } else {
            state.setProjectStatus(PlotI18n.tr("plugin.building.build_in_progress", records.size()));
        }
        command.executeScheduled(() -> {
            BuildingGenerateCommand.ExecutionResult result = command.getLastExecutionResult();
            if (districtSnapshot != null && districtSnapshot.buildingsAttempted() > 1) {
                state.setLastDistrictBuildReport(DistrictBuildReport.from(districtSnapshot, result));
            }
            if (result != null && result.cancelled()) {
                if (result.success() > 0) {
                    host.commands().pushExecuted(command);
                }
                applyBuildResultStatus(result, districtSnapshot);
                clearPreview();
                return;
            }
            host.commands().pushExecuted(command);
            applyBuildResultStatus(result, districtSnapshot);
            clearPreview();
        });
    }

    public void applyBuildResultStatus(
            BuildingGenerateCommand.ExecutionResult result,
            DistrictGenerationResult district) {
        if (result == null || result.total() == 0) {
            state.setProjectStatus(PlotI18n.tr("plugin.building.build_no_blocks"));
            return;
        }
        if (district != null && district.buildingsAttempted() > 1) {
            if (result.cancelled()) {
                state.setProjectStatus(PlotI18n.tr(
                    "plugin.building.district_build_cancelled_status",
                    result.success(),
                    result.total(),
                    district.buildingsGenerated(),
                    district.buildingsSkipped()));
                return;
            }
            if (result.isFullSuccess()) {
                if (district.buildingsSkipped() > 0) {
                    state.setProjectStatus(PlotI18n.tr(
                        "plugin.building.district_build_success_partial",
                        district.buildingsGenerated(),
                        district.buildingsAttempted(),
                        result.success(),
                        district.buildingsSkipped()));
                } else {
                    state.setProjectStatus(PlotI18n.tr(
                        "plugin.building.district_build_success",
                        district.buildingsGenerated(),
                        result.success()));
                }
                return;
            }
            if (result.isTotalFailure()) {
                state.setProjectStatus(PlotI18n.tr(
                    "plugin.building.district_build_failed",
                    district.buildingsGenerated(),
                    result.total()));
                return;
            }
            state.setProjectStatus(PlotI18n.tr(
                "plugin.building.district_build_partial",
                district.buildingsGenerated(),
                result.success(),
                result.total(),
                result.failed(),
                district.buildingsSkipped()));
            return;
        }
        if (result.cancelled()) {
            state.setProjectStatus(PlotI18n.tr("plugin.building.build_cancelled", result.success(), result.total()));
            return;
        }
        if (result.isFullSuccess()) {
            state.setProjectStatus(PlotI18n.tr("plugin.building.build_success", result.success()));
            return;
        }
        if (result.isTotalFailure()) {
            state.setProjectStatus(PlotI18n.tr("plugin.building.build_failed", result.total()));
            return;
        }
        state.setProjectStatus(PlotI18n.tr(
            "plugin.building.build_partial",
            result.success(),
            result.total(),
            result.failed()));
    }

    public void locateBuilding(BuildingFootprint building) {
        Vec2d centroid = BuildingGeometryUtils.computeCentroid(building.getOuterPoints());
        Canvas canvas = com.plot.ui.canvas.CanvasAccess.get();
        if (canvas != null && canvas.getCamera() != null) {
            canvas.getCamera().setOffset(centroid);
            state.getSelection().select(building.getId(), false);
            state.setProjectStatus(PlotI18n.tr("plugin.building.locate_success", building.getName()));
        }
    }

    public void locateBuildingById(String buildingId) {
        BuildingFootprint building = state.getProject().getBuilding(buildingId);
        if (building != null) {
            locateBuilding(building);
        }
    }

    public void selectBuildingById(String buildingId, boolean append) {
        if (buildingId == null || buildingId.isBlank()) {
            return;
        }
        state.getSelection().select(buildingId, append);
    }

    public void selectBuildingPair(String buildingIdA, String buildingIdB) {
        state.getSelection().clear();
        if (buildingIdA != null && !buildingIdA.isBlank()) {
            state.getSelection().select(buildingIdA, false);
        }
        if (buildingIdB != null && !buildingIdB.isBlank()) {
            state.getSelection().select(buildingIdB, true);
        }
    }

    public com.plot.plugin.building.overlay.BuildingOverlayDiagnostics overlayDiagnostics(
            List<BuildingFootprint> generateTargets) {
        if (previewValidity(generateTargets) != BuildingPreviewIdentity.Validity.VALID) {
            return com.plot.plugin.building.overlay.BuildingOverlayDiagnostics.EMPTY;
        }
        return com.plot.plugin.building.overlay.BuildingOverlayDiagnostics.of(
            state.getOverlayPreviewedBuildingIds(),
            state.getOverlayWarningBuildingIds());
    }

    public List<BuildingGenerationIssues.Issue> collectPreviewIssues(List<BuildingFootprint> generateTargets) {
        DistrictGenerationResult district = state.getLastDistrictResult();
        BuildingGenerationResult single = state.getLastGenerationResult();
        if (!hasPreviewResult()) {
            return List.of();
        }
        boolean districtMode = district != null && district.buildingsAttempted() > 1;
        return BuildingGenerationIssues.collect(
            state.getProject(),
            district,
            single,
            state.getPreviewIdentity(),
            districtMode);
    }

    public void startPickSession() {
        ToolManager toolManager = host.tools();
        var selectTool = toolManager.getTool("select");
        if (!(selectTool instanceof BaseTool baseTool)) {
            return;
        }
        state.getSelectedFootprints().clear();
        state.getPickSession().begin();
        toolManager.setActiveTool(selectTool);
        host.appState().setCurrentTool(baseTool);
        state.setProjectStatus(PlotI18n.tr("plugin.building.pick_started"));
    }

    public void handlePickSessionTick() {
        BuildingFootprintPickSession.Outcome outcome = state.getPickSession().tick(host.appState());
        switch (outcome.getResult()) {
            case SUCCESS -> {
                state.getSelectedFootprints().clear();
                state.getSelectedFootprints().addAll(outcome.getFootprints());
                adoptSelectedFootprints();
            }
            case NEED_SELECTION -> state.setProjectStatus(PlotI18n.tr("plugin.building.pick_need_selection"));
            case NO_VALID -> state.setProjectStatus(PlotI18n.tr("plugin.building.pick_no_valid"));
            case CANCELLED -> state.setProjectStatus(PlotI18n.tr("plugin.building.pick_cancelled"));
            default -> {
                List<Shape> selected = host.appState().getSelectedShapes();
                state.setProjectStatus(PlotI18n.tr(state.getPickSession().hintKeyForCurrentSelection(selected)));
            }
        }
    }

    public void updateSelectedFootprints() {
        refreshCanvasFootprintSelection();
    }

    public BuildingFootprintSelectionAnalysis canvasSelectionAnalysis() {
        List<Shape> shapes;
        if (state.getPickSession().isActive()) {
            shapes = state.getPickSession().getAccumulatedFootprints();
            if (shapes.isEmpty()) {
                shapes = host.appState().getSelectedShapes();
            }
        } else {
            shapes = host.appState().getSelectedShapes();
        }
        return BuildingFootprintSelectionAnalysis.analyze(shapes, state.getProject());
    }

    public void refreshCanvasFootprintSelection() {
        state.getSelectedFootprints().clear();
        state.getSelectedFootprints().addAll(canvasSelectionAnalysis().adoptable());
    }

    public void selectAllClosedShapesOnCanvas() {
        List<Shape> adoptable = BuildingGeometryUtils.findAdoptableFootprints(host.appState().getShapes());
        host.appState().setSelectedShapes(new ArrayList<>(adoptable));
        updateSelectedFootprints();
        state.setProjectStatus(adoptable.isEmpty()
            ? PlotI18n.tr("plugin.building.pick_no_valid")
            : PlotI18n.tr("plugin.building.select_all_closed_success", adoptable.size()));
    }

    public int computeSelectedFootprintBlockCount() {
        com.plot.api.world.WorldProjectionSnapshot projection;
        try {
            projection = host.coordinates().captureProjection();
        } catch (RuntimeException ignored) {
            projection = com.plot.api.world.WorldProjectionSnapshot.UNKNOWN;
        }
        int count = 0;
        for (Shape shape : state.getSelectedFootprints()) {
            List<Vec2d> points = BuildingGeometryUtils.extractFootprintPoints(shape);
            count += BuildingBlockCountCache.blockCount(points, projection);
        }
        return count;
    }

    public void adoptSelectedFootprints() {
        if (state.getSelectedFootprints().isEmpty()) {
            state.setProjectStatus(PlotI18n.tr("plugin.building.adopt_no_selection"));
            return;
        }

        state.getProjectHistory().push(state.getProject());
        int adopted = 0;
        int skipped = 0;
        int repaired = 0;
        List<String> adoptedIds = new ArrayList<>();
        List<String> rejectHints = new ArrayList<>();
        for (Shape shape : state.getSelectedFootprints()) {
            List<Vec2d> points = BuildingGeometryUtils.extractFootprintPoints(shape);
            BuildingFootprintValidator.Result validation = BuildingFootprintValidator.validate(points);
            if (!validation.valid()) {
                skipped++;
                if (rejectHints.size() < 5 && validation.reason() != null) {
                    rejectHints.add(PlotI18n.tr(validation.reason().i18nKey()));
                }
                continue;
            }
            if (validation.repaired()) {
                repaired++;
            }
            boolean rectangular = BuildingGeometryUtils.detectRectangular(validation.cleanedPoints());
            BuildingFootprint footprint = new BuildingFootprint(validation.cleanedPoints(), rectangular);
            footprint.setName(PlotI18n.tr("plugin.building.default_name", adopted + 1));
            state.getProject().addBuilding(footprint);
            adoptedIds.add(footprint.getId());
            adopted++;
        }

        state.getSelectedFootprints().clear();
        if (adopted > 0) {
            state.getSelection().selectAll(adoptedIds);
            clearPreview();
        }
        if (adopted == 0) {
            state.setProjectStatus(skipped > 0
                ? PlotI18n.tr("plugin.building.adopt_all_invalid", skipped)
                : PlotI18n.tr("plugin.building.adopt_no_selection"));
        } else if (skipped > 0) {
            state.setProjectStatus(PlotI18n.tr("plugin.building.adopt_success_batch_partial", adopted, skipped));
            if (!rejectHints.isEmpty()) {
                state.setProjectStatus(state.getProjectStatus() + " — " + String.join("; ", rejectHints));
            }
            appendRepairHint(state, repaired);
        } else if (adopted > 1) {
            state.setProjectStatus(PlotI18n.tr("plugin.building.adopt_success_batch", adopted));
            appendRepairHint(state, repaired);
        } else {
            state.setProjectStatus(PlotI18n.tr("plugin.building.adopt_success"));
            appendRepairHint(state, repaired);
        }
    }

    private static void appendRepairHint(BuildingPluginState state, int repaired) {
        if (repaired > 0) {
            state.setProjectStatus(state.getProjectStatus()
                + " " + PlotI18n.tr("plugin.building.adopt_repaired_hint", repaired));
        }
    }

    public void onProjectLoaded(String filePath, Path projectsDir) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        String targetFile = ProjectPathResolver.sidecarFileName(filePath);
        Path file = projectsDir.resolve(targetFile);
        if (loadProjectFile(file)) {
            state.setCurrentProjectFile(targetFile);
        }
    }

    public void onProjectSaved(String filePath, Path projectsDir) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        state.setCurrentProjectFile(ProjectPathResolver.sidecarFileName(filePath));
        if (saveProjectFile(projectsDir.resolve(state.getCurrentProjectFile()))) {
            state.setProjectStatus(PlotI18n.tr("plugin.building.project.saved", filePath));
        }
    }

    public void persistProject(Path projectsDir) {
        saveProjectFile(projectsDir.resolve(state.getCurrentProjectFile()));
    }

    public boolean loadProjectFile(Path file) {
        try {
            BuildingProjectLoadResult loaded = BuildingProjectPersistence.loadWithDiagnostics(file);
            state.setProject(loaded.project());
            state.getProjectHistory().clear();
            state.getSelection().clear();
            if (!state.getProject().getBuildings().isEmpty()) {
                state.getSelection().select(state.getProject().getBuildings().keySet().iterator().next(), false);
            }
            resetAfterProjectLoad();
            if (loaded.hasSkippedBuildings()) {
                state.setProjectStatus(PlotI18n.tr(
                    "plugin.building.project.loaded_with_skips",
                    file.getFileName(),
                    loaded.skippedBuildingCount()));
            }
            return true;
        } catch (IOException e) {
            LOGGER.error("加载建筑项目失败: {}", e.getMessage(), e);
            state.setProjectStatus(PlotI18n.tr("plugin.building.project.load_failed", file.getFileName()));
            return false;
        }
    }

    public void loadProjectForCurrentProject(Path projectsDir, String defaultProjectFile) {
        Project current = host.appState().getCurrentProject();
        if (current != null && current.getFilePath() != null && !current.getFilePath().isBlank()) {
            onProjectLoaded(current.getFilePath(), projectsDir);
            return;
        }
        Path file = projectsDir.resolve(defaultProjectFile);
        if (loadProjectFile(file)) {
            state.setCurrentProjectFile(defaultProjectFile);
        }
    }

    public boolean saveProjectFile(Path file) {
        if (file == null || state.getProject() == null) {
            return false;
        }
        try {
            String json = BuildingProjectPersistence.serialize(state.getProject());
            if (state.getContentFingerprint().isUnchanged(json, file)) {
                LOGGER.debug("建筑项目内容未变，跳过重复保存: {}", file.getFileName());
                return true;
            }
            BuildingProjectPersistence.save(state.getProject(), file);
            state.getContentFingerprint().markSaved(json, file);
            return true;
        } catch (IOException e) {
            LOGGER.error("保存建筑项目失败: {}", e.getMessage(), e);
            state.setProjectStatus(PlotI18n.tr("plugin.building.project.save_failed", file.getFileName()));
            return false;
        }
    }

    public World getClientWorld() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null ? client.world : null;
    }
}
