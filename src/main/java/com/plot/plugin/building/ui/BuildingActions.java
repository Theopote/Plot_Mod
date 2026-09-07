package com.plot.plugin.building.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.command.commands.BuildingGenerateCommand;
import com.plot.core.context.PluginContext;
import com.plot.core.model.Project;
import com.plot.core.model.Shape;
import com.plot.core.persistence.ProjectPathResolver;
import com.plot.core.tool.BaseTool;
import com.plot.core.tool.ToolManager;
import com.plot.plugin.building.BuildingFootprintPickSession;
import com.plot.plugin.building.BuildingFootprintValidator;
import com.plot.plugin.building.BuildingGenerator;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.BuildingBatchEditor;
import com.plot.plugin.building.BuildingHeightDistribution;
import com.plot.plugin.building.preset.BuildingPresetApplier;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.DistrictBuildReport;
import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;
import com.plot.plugin.building.model.persistence.BuildingProjectPersistence;
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

/**
 * 建筑插件应用层动作：认领、预览、生成、持久化。
 * <p>
 * Panel 应 {@code render → invoke} 本类，而非直接串联 Project / Generator / Minecraft。
 */
public final class BuildingActions {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/BuildingActions");

    private final PluginContext host;
    private final BuildingPluginState state;
    private final Object projectLock;
    private BuildingGenerator buildingGenerator;

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
        if (buildings.size() > 1) {
            startDistrictPreviewJob(buildings, autoProjectGhosts, buildConfirmOnComplete);
            return false;
        }
        return calculateDistrictPreviewSync(buildings, autoProjectGhosts, buildConfirmOnComplete);
    }

    private boolean calculateDistrictPreviewSync(
            List<BuildingFootprint> buildings,
            boolean autoProjectGhosts,
            boolean buildConfirmOnComplete) {
        World world = getClientWorld();
        com.plot.api.world.IGhostBlockService ghostBlockManager = host.ghosts();
        if (ghostBlockManager != null) {
            ghostBlockManager.clearAllGhostBlocks();
        }

        DistrictGenerationResult district;
        try {
            district = buildingGenerator.generateDistrict(buildings, world);
        } catch (Exception e) {
            LOGGER.error("片区预览生成失败: {}", e.getMessage(), e);
            state.setLastDistrictResult(null);
            state.setLastGenerationResult(null);
            state.setProjectStatus(PlotI18n.tr("plugin.building.generate_empty_result"));
            return false;
        }

        return applyDistrictPreviewResult(district, autoProjectGhosts, buildConfirmOnComplete);
    }

    private void startDistrictPreviewJob(
            List<BuildingFootprint> buildings,
            boolean autoProjectGhosts,
            boolean buildConfirmOnComplete) {
        cancelDistrictPreviewJob();
        com.plot.api.world.IGhostBlockService ghostBlockManager = host.ghosts();
        if (ghostBlockManager != null) {
            ghostBlockManager.clearAllGhostBlocks();
        }
        state.setLastDistrictResult(null);
        state.setLastGenerationResult(null);
        state.setDistrictPreviewBuildConfirmPending(buildConfirmOnComplete);
        DistrictPreviewJob job = new DistrictPreviewJob(
            buildings,
            autoProjectGhosts,
            buildConfirmOnComplete,
            this);
        state.setDistrictPreviewJob(job);
        updateDistrictPreviewProgress(job);
    }

    public void tickDistrictPreviewJob() {
        DistrictPreviewJob job = state.getDistrictPreviewJob();
        if (job != null && job.isRunning()) {
            job.tick();
        }
    }

    public boolean isDistrictPreviewBusy() {
        DistrictPreviewJob job = state.getDistrictPreviewJob();
        return job != null && job.isRunning();
    }

    public void cancelDistrictPreviewJob() {
        DistrictPreviewJob job = state.getDistrictPreviewJob();
        if (job != null) {
            job.cancel();
        }
        state.setDistrictPreviewJob(null);
        state.setDistrictPreviewBuildConfirmPending(false);
    }

    void updateDistrictPreviewProgress(DistrictPreviewJob job) {
        if (job == null) {
            return;
        }
        state.setProjectStatus(PlotI18n.tr(
            "plugin.building.district_preview_progress",
            job.processedCount(),
            job.totalCount()));
    }

    void failDistrictPreviewJob(DistrictPreviewJob job) {
        if (job != null) {
            job.cancel();
        }
        state.setDistrictPreviewJob(null);
        state.setDistrictPreviewBuildConfirmPending(false);
        state.setLastDistrictResult(null);
        state.setLastGenerationResult(null);
        state.setProjectStatus(PlotI18n.tr("plugin.building.generate_world_unavailable"));
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
        boolean ready = applyDistrictPreviewResult(district, autoProjectGhosts, buildConfirmOnComplete);
        if (buildConfirmOnComplete && ready) {
            state.setBuildConfirmPending(true);
        }
    }

    private boolean applyDistrictPreviewResult(
            DistrictGenerationResult district,
            boolean autoProjectGhosts,
            boolean buildConfirmOnComplete) {
        state.setLastDistrictResult(district);
        state.setLastGenerationResult(district.toMergedResult());
        if (!district.hasPlacements()) {
            state.setProjectStatus(PlotI18n.tr("plugin.building.generate_empty_result"));
            return false;
        }

        if (autoProjectGhosts) {
            projectPreview();
        }

        if (district.buildingsAttempted() > 1) {
            if (district.buildingsSkipped() > 0) {
                state.setProjectStatus(PlotI18n.tr(
                    "plugin.building.district_preview_partial",
                    district.buildingsGenerated(),
                    district.buildingsAttempted(),
                    district.totalBlocks()));
            } else {
                state.setProjectStatus(PlotI18n.tr(
                    "plugin.building.district_preview_ready",
                    district.buildingsGenerated(),
                    district.totalBlocks()));
            }
        } else {
            BuildingGenerationResult merged = state.getLastGenerationResult();
            if (merged != null && !merged.warnings.isEmpty()) {
                state.setProjectStatus(PlotI18n.tr("plugin.building.generate_preview_ready")
                    + " — "
                    + PlotI18n.tr(merged.warnings.getFirst()));
            } else {
                state.setProjectStatus(PlotI18n.tr("plugin.building.generate_preview_ready"));
            }
        }
        return true;
    }

    public void projectPreview() {
        BuildingGenerationResult lastGenerationResult = state.getLastGenerationResult();
        if (lastGenerationResult == null) {
            return;
        }
        com.plot.api.world.IGhostBlockService ghostBlockManager = host.ghosts();
        if (ghostBlockManager == null) {
            return;
        }
        ghostBlockManager.clearAllGhostBlocks();
        java.util.LinkedHashMap<net.minecraft.util.math.BlockPos, String> ghosts =
            new java.util.LinkedHashMap<>(lastGenerationResult.placementRecords.size());
        for (BlockRecord record : lastGenerationResult.placementRecords.values()) {
            ghosts.put(record.pos, record.newBlockId);
        }
        ghostBlockManager.addGhostBlocks(ghosts);
    }

    public void clearPreview() {
        cancelDistrictPreviewJob();
        com.plot.api.world.IGhostBlockService ghostBlockManager = host.ghosts();
        if (ghostBlockManager != null) {
            ghostBlockManager.clearAllGhostBlocks();
        }
        state.setLastGenerationResult(null);
        state.setLastDistrictResult(null);
    }

    public void invalidatePreview() {
        if (state.getLastGenerationResult() != null) {
            clearPreview();
            state.setProjectStatus(PlotI18n.tr("plugin.building.preview_invalidated"));
        }
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

    public void previewEntireDistrict() {
        state.getSelection().selectAll(state.getProject().getBuildings().keySet());
        calculateDistrictPreview(new ArrayList<>(state.getProject().getBuildings().values()), true, false);
    }

    public void prepareGenerateEntireDistrict() {
        state.getSelection().selectAll(state.getProject().getBuildings().keySet());
        calculateDistrictPreview(new ArrayList<>(state.getProject().getBuildings().values()), true, true);
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

    public void applyPresetToBuilding(String presetId, BuildingFootprint building) {
        if (presetId == null || presetId.isBlank() || building == null) {
            return;
        }
        state.getProjectHistory().push(state.getProject());
        BuildingPresetApplier.apply(presetId, building);
        invalidatePreview();
        state.setProjectStatus(PlotI18n.tr(
            "plugin.building.preset_applied",
            PlotI18n.tr("preset.building." + presetId)));
    }

    public void applyPresetToSelected(String presetId, List<BuildingFootprint> targets) {
        if (presetId == null || presetId.isBlank() || targets == null || targets.isEmpty()) {
            return;
        }
        state.getProjectHistory().push(state.getProject());
        BuildingBatchEditor.ApplyResult result = BuildingBatchEditor.applyPreset(presetId, targets);
        invalidatePreview();
        state.setProjectStatus(PlotI18n.tr(
            "plugin.building.preset_applied_batch",
            PlotI18n.tr("preset.building." + presetId),
            result.updated()));
    }

    public void buildInWorld() {
        final BuildingGenerationResult resultSnapshot;
        final DistrictGenerationResult districtSnapshot;
        synchronized (projectLock) {
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
                state.setProjectStatus(PlotI18n.tr("plugin.building.pick_success", state.getSelectedFootprints().size()));
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
        state.getSelectedFootprints().clear();
        state.getSelectedFootprints().addAll(
            BuildingGeometryUtils.findAdoptableFootprints(host.appState().getSelectedShapes()));
    }

    public void selectAllClosedShapesOnCanvas() {
        List<Shape> adoptable = BuildingGeometryUtils.findAdoptableFootprints(host.appState().getShapes());
        host.appState().setSelectedShapes(new ArrayList<>(adoptable));
        updateSelectedFootprints();
        state.setProjectStatus(adoptable.isEmpty()
            ? PlotI18n.tr("plugin.building.pick_no_valid")
            : PlotI18n.tr("plugin.building.select_all_closed_success", adoptable.size()));
    }

    public double computeSelectedFootprintArea() {
        double area = 0.0;
        for (Shape shape : state.getSelectedFootprints()) {
            List<Vec2d> points = BuildingGeometryUtils.extractFootprintPoints(shape);
            area += Math.abs(BuildingFootprint.signedArea(points));
        }
        return area;
    }

    public void adoptSelectedFootprints() {
        if (state.getSelectedFootprints().isEmpty()) {
            state.setProjectStatus(PlotI18n.tr("plugin.building.adopt_no_selection"));
            return;
        }

        state.getProjectHistory().push(state.getProject());
        int adopted = 0;
        int skipped = 0;
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
        } else if (adopted > 1) {
            state.setProjectStatus(PlotI18n.tr("plugin.building.adopt_success_batch", adopted));
        } else {
            state.setProjectStatus(PlotI18n.tr("plugin.building.adopt_success"));
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
            state.setProjectStatus(PlotI18n.tr("plugin.building.project.loaded", filePath));
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
            BuildingProject loaded = BuildingProjectPersistence.load(file);
            state.setProject(loaded);
            state.getProjectHistory().clear();
            state.getSelection().clear();
            if (!state.getProject().getBuildings().isEmpty()) {
                state.getSelection().select(state.getProject().getBuildings().keySet().iterator().next(), false);
            }
            resetAfterProjectLoad();
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
            state.setProjectStatus(PlotI18n.tr("plugin.building.project.default_loaded"));
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
