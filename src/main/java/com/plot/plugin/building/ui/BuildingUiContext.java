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
import com.plot.plugin.building.BuildingBatchEditor;
import com.plot.plugin.building.BuildingFootprintPickSession;
import com.plot.plugin.building.BuildingFootprintValidator;
import com.plot.plugin.building.BuildingGenerator;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.BuildingHeightDistribution;
import com.plot.plugin.building.BuildingListHelper;
import com.plot.plugin.building.BuildingSelectionSet;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.DistrictBuildReport;
import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;
import com.plot.plugin.building.model.BuildingProjectHistory;
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
 * 建筑 UI 层共享依赖、可变状态访问与预览/落地/认领等业务编排。
 */
public final class BuildingUiContext {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/BuildingUiContext");

    private final PluginContext host;
    private final BuildingPluginState state;
    private final Object projectLock;
    private BuildingGenerator buildingGenerator;

    public BuildingUiContext(
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

    public PluginContext host() {
        return host;
    }

    public BuildingPluginState state() {
        return state;
    }

    public Object projectLock() {
        return projectLock;
    }

    public BuildingGenerator buildingGenerator() {
        return buildingGenerator;
    }

    public BuildingProject project() {
        return state.getProject();
    }

    public void setProject(BuildingProject project) {
        state.setProject(project);
    }

    public BuildingProjectHistory projectHistory() {
        return state.getProjectHistory();
    }

    public BuildingSelectionSet selection() {
        return state.getSelection();
    }

    public BuildingFootprintPickSession pickSession() {
        return state.getPickSession();
    }

    public List<Shape> selectedFootprints() {
        return state.getSelectedFootprints();
    }

    public BuildingGenerationResult lastGenerationResult() {
        return state.getLastGenerationResult();
    }

    public DistrictGenerationResult lastDistrictResult() {
        return state.getLastDistrictResult();
    }

    public DistrictBuildReport lastDistrictBuildReport() {
        return state.getLastDistrictBuildReport();
    }

    public String projectStatus() {
        return state.getProjectStatus();
    }

    public void setProjectStatus(String projectStatus) {
        state.setProjectStatus(projectStatus);
    }

    public String buildingNameEditingId() {
        return state.getBuildingNameEditingId();
    }

    public void setBuildingNameEditingId(String buildingNameEditingId) {
        state.setBuildingNameEditingId(buildingNameEditingId);
    }

    public BuildingListHelper.SortMode buildingSortMode() {
        return state.getBuildingSortMode();
    }

    public void setBuildingSortMode(BuildingListHelper.SortMode mode) {
        state.setBuildingSortMode(mode);
    }

    public BuildingBatchEditor.FieldMask batchFieldMask() {
        return state.getBatchFieldMask();
    }

    public BuildingHeightDistribution.Mode heightDistMode() {
        return state.getHeightDistMode();
    }

    public void setHeightDistMode(BuildingHeightDistribution.Mode mode) {
        state.setHeightDistMode(mode);
    }

    public int heightDistMinFloors() {
        return state.getHeightDistMinFloors();
    }

    public void setHeightDistMinFloors(int floors) {
        state.setHeightDistMinFloors(floors);
    }

    public int heightDistMaxFloors() {
        return state.getHeightDistMaxFloors();
    }

    public void setHeightDistMaxFloors(int floors) {
        state.setHeightDistMaxFloors(floors);
    }

    public long heightDistSeed() {
        return state.getHeightDistSeed();
    }

    public void setHeightDistSeed(long seed) {
        state.setHeightDistSeed(seed);
    }

    public boolean heightDistSeedManual() {
        return state.isHeightDistSeedManual();
    }

    public void setHeightDistSeedManual(boolean manual) {
        state.setHeightDistSeedManual(manual);
    }

    public imgui.type.ImString heightDistSeedBuffer() {
        return state.getHeightDistSeedBuffer();
    }

    public long resolveHeightDistSeed(List<BuildingFootprint> targets) {
        if (state.isHeightDistSeedManual()) {
            return state.getHeightDistSeed();
        }
        long seed = BuildingHeightDistribution.defaultSeed(project(), targets);
        state.setHeightDistSeed(seed);
        return seed;
    }

    public imgui.type.ImBoolean manualElevationRef() {
        return state.getManualElevationRef();
    }

    public imgui.type.ImString buildingNameBuffer() {
        return state.getBuildingNameBuffer();
    }

    public List<String> pendingDeleteBuildingIds() {
        return state.getPendingDeleteBuildingIds();
    }

    public boolean deleteConfirmPending() {
        return state.isDeleteConfirmPending();
    }

    public void setDeleteConfirmPending(boolean pending) {
        state.setDeleteConfirmPending(pending);
    }

    public boolean buildConfirmPending() {
        return state.isBuildConfirmPending();
    }

    public void setBuildConfirmPending(boolean pending) {
        state.setBuildConfirmPending(pending);
    }

    public String currentProjectFile() {
        return state.getCurrentProjectFile();
    }

    public void setCurrentProjectFile(String file) {
        state.setCurrentProjectFile(file);
    }

    public void syncSelectedBuildingAfterHistory() {
        selection().retainExisting(project());
        setBuildingNameEditingId("");
    }

    public void resetAfterProjectLoad() {
        setBuildingNameEditingId("");
        pickSession().cancel();
        selectedFootprints().clear();
        clearPreview();
        state.setLastDistrictBuildReport(null);
    }

    public boolean calculatePreview(BuildingFootprint building) {
        return calculateDistrictPreview(List.of(building), false);
    }

    /**
     * @param autoProjectGhosts 片区预览成功后自动投影虚影，便于一眼看到整片体量
     */
    public boolean calculateDistrictPreview(
            List<BuildingFootprint> buildings,
            boolean autoProjectGhosts) {
        World world = getClientWorld();
        if (world == null || buildingGenerator == null) {
            setProjectStatus(PlotI18n.tr("plugin.building.generate_world_unavailable"));
            return false;
        }
        if (buildings == null || buildings.isEmpty()) {
            setProjectStatus(PlotI18n.tr("plugin.building.select_building_hint"));
            return false;
        }

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
            setProjectStatus(PlotI18n.tr("plugin.building.generate_empty_result"));
            return false;
        }

        state.setLastDistrictResult(district);
        state.setLastGenerationResult(district.toMergedResult());
        if (!district.hasPlacements()) {
            setProjectStatus(PlotI18n.tr("plugin.building.generate_empty_result"));
            return false;
        }

        if (autoProjectGhosts) {
            projectPreview();
        }

        if (district.buildingsAttempted() > 1) {
            if (district.buildingsSkipped() > 0) {
                setProjectStatus(PlotI18n.tr(
                    "plugin.building.district_preview_partial",
                    district.buildingsGenerated(),
                    district.buildingsAttempted(),
                    district.totalBlocks()));
            } else {
                setProjectStatus(PlotI18n.tr(
                    "plugin.building.district_preview_ready",
                    district.buildingsGenerated(),
                    district.totalBlocks()));
            }
        } else {
            BuildingGenerationResult merged = state.getLastGenerationResult();
            if (merged != null && !merged.warnings.isEmpty()) {
                setProjectStatus(PlotI18n.tr("plugin.building.generate_preview_ready")
                    + " — "
                    + PlotI18n.tr(merged.warnings.getFirst()));
            } else {
                setProjectStatus(PlotI18n.tr("plugin.building.generate_preview_ready"));
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
        com.plot.api.world.IGhostBlockService ghostBlockManager = host.ghosts();
        if (ghostBlockManager != null) {
            ghostBlockManager.clearAllGhostBlocks();
        }
        state.setLastGenerationResult(null);
        state.setLastDistrictResult(null);
    }

    /** 参数/工程变更后使预览失效，避免按过期几何落地。 */
    public void invalidatePreview() {
        if (state.getLastGenerationResult() != null) {
            clearPreview();
            setProjectStatus(PlotI18n.tr("plugin.building.preview_invalidated"));
        }
    }

    public void buildInWorld() {
        final BuildingGenerationResult resultSnapshot;
        final DistrictGenerationResult districtSnapshot;
        synchronized (projectLock) {
            BuildingGenerationResult lastGenerationResult = state.getLastGenerationResult();
            if (lastGenerationResult == null || lastGenerationResult.placementRecords.isEmpty()) {
                setProjectStatus(PlotI18n.tr("plugin.building.build_no_blocks"));
                return;
            }
            resultSnapshot = lastGenerationResult;
            districtSnapshot = state.getLastDistrictResult();
        }

        com.plot.api.world.PlacementReadiness readiness =
            host.projection().checkWorldModificationReadiness();
        if (!readiness.ready()) {
            setProjectStatus(readiness.message());
            return;
        }

        if (host.placement().isBusy()) {
            setProjectStatus(PlotI18n.tr("plugin.building.build_in_progress_wait"));
            return;
        }

        List<BlockRecord> records = new ArrayList<>(resultSnapshot.placementRecords.values());
        BuildingGenerateCommand command = new BuildingGenerateCommand(records, host.projection(), host.placement());
        if (districtSnapshot != null && districtSnapshot.buildingsAttempted() > 1) {
            setProjectStatus(PlotI18n.tr(
                "plugin.building.district_build_in_progress",
                districtSnapshot.buildingsGenerated(),
                records.size()));
        } else {
            setProjectStatus(PlotI18n.tr("plugin.building.build_in_progress", records.size()));
        }
        command.executeScheduled(() -> {
            BuildingGenerateCommand.ExecutionResult result = command.getLastExecutionResult();
            if (districtSnapshot != null && districtSnapshot.buildingsAttempted() > 1) {
                state.setLastDistrictBuildReport(DistrictBuildReport.from(districtSnapshot, result));
            }
            // 取消时若已写入部分方块，仍入历史以便撤销半成品
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
            setProjectStatus(PlotI18n.tr("plugin.building.build_no_blocks"));
            return;
        }
        if (district != null && district.buildingsAttempted() > 1) {
            if (result.cancelled()) {
                setProjectStatus(PlotI18n.tr(
                    "plugin.building.district_build_cancelled_status",
                    result.success(),
                    result.total(),
                    district.buildingsGenerated(),
                    district.buildingsSkipped()));
                return;
            }
            if (result.isFullSuccess()) {
                if (district.buildingsSkipped() > 0) {
                    setProjectStatus(PlotI18n.tr(
                        "plugin.building.district_build_success_partial",
                        district.buildingsGenerated(),
                        district.buildingsAttempted(),
                        result.success(),
                        district.buildingsSkipped()));
                } else {
                    setProjectStatus(PlotI18n.tr(
                        "plugin.building.district_build_success",
                        district.buildingsGenerated(),
                        result.success()));
                }
                return;
            }
            if (result.isTotalFailure()) {
                setProjectStatus(PlotI18n.tr(
                    "plugin.building.district_build_failed",
                    district.buildingsGenerated(),
                    result.total()));
                return;
            }
            setProjectStatus(PlotI18n.tr(
                "plugin.building.district_build_partial",
                district.buildingsGenerated(),
                result.success(),
                result.total(),
                result.failed(),
                district.buildingsSkipped()));
            return;
        }
        if (result.cancelled()) {
            setProjectStatus(PlotI18n.tr("plugin.building.build_cancelled", result.success(), result.total()));
            return;
        }
        if (result.isFullSuccess()) {
            setProjectStatus(PlotI18n.tr("plugin.building.build_success", result.success()));
            return;
        }
        if (result.isTotalFailure()) {
            setProjectStatus(PlotI18n.tr("plugin.building.build_failed", result.total()));
            return;
        }
        setProjectStatus(PlotI18n.tr(
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
            selection().select(building.getId(), false);
            setProjectStatus(PlotI18n.tr("plugin.building.locate_success", building.getName()));
        }
    }

    public void startPickSession() {
        ToolManager toolManager = host.tools();
        var selectTool = toolManager.getTool("select");
        if (!(selectTool instanceof BaseTool baseTool)) {
            return;
        }
        selectedFootprints().clear();
        pickSession().begin();
        toolManager.setActiveTool(selectTool);
        host.appState().setCurrentTool(baseTool);
        setProjectStatus(PlotI18n.tr("plugin.building.pick_started"));
    }

    public void handlePickSessionTick() {
        BuildingFootprintPickSession.Outcome outcome = pickSession().tick(host.appState());
        switch (outcome.getResult()) {
            case SUCCESS -> {
                selectedFootprints().clear();
                selectedFootprints().addAll(outcome.getFootprints());
                setProjectStatus(PlotI18n.tr("plugin.building.pick_success", selectedFootprints().size()));
            }
            case NEED_SELECTION -> setProjectStatus(PlotI18n.tr("plugin.building.pick_need_selection"));
            case NO_VALID -> setProjectStatus(PlotI18n.tr("plugin.building.pick_no_valid"));
            case CANCELLED -> setProjectStatus(PlotI18n.tr("plugin.building.pick_cancelled"));
            default -> {
                List<Shape> selected = host.appState().getSelectedShapes();
                setProjectStatus(PlotI18n.tr(pickSession().hintKeyForCurrentSelection(selected)));
            }
        }
    }

    public void updateSelectedFootprints() {
        selectedFootprints().clear();
        selectedFootprints().addAll(
            BuildingGeometryUtils.findAdoptableFootprints(host.appState().getSelectedShapes()));
    }

    public void selectAllClosedShapesOnCanvas() {
        List<Shape> adoptable = BuildingGeometryUtils.findAdoptableFootprints(host.appState().getShapes());
        host.appState().setSelectedShapes(new ArrayList<>(adoptable));
        updateSelectedFootprints();
        setProjectStatus(adoptable.isEmpty()
            ? PlotI18n.tr("plugin.building.pick_no_valid")
            : PlotI18n.tr("plugin.building.select_all_closed_success", adoptable.size()));
    }

    public double computeSelectedFootprintArea() {
        double area = 0.0;
        for (Shape shape : selectedFootprints()) {
            List<Vec2d> points = BuildingGeometryUtils.extractFootprintPoints(shape);
            area += Math.abs(BuildingFootprint.signedArea(points));
        }
        return area;
    }

    public void adoptSelectedFootprints() {
        if (selectedFootprints().isEmpty()) {
            setProjectStatus(PlotI18n.tr("plugin.building.adopt_no_selection"));
            return;
        }

        projectHistory().push(project());
        int adopted = 0;
        int skipped = 0;
        List<String> adoptedIds = new ArrayList<>();
        List<String> rejectHints = new ArrayList<>();
        for (Shape shape : selectedFootprints()) {
            List<Vec2d> points = BuildingGeometryUtils.extractFootprintPoints(shape);
            BuildingFootprintValidator.Result validation = BuildingFootprintValidator.validate(points);
            if (!validation.valid()) {
                skipped++;
                if (rejectHints.size() < 5 && validation.reason() != null) {
                    rejectHints.add(PlotI18n.tr(validation.reason().i18nKey()));
                }
                continue;
            }
            boolean rectangular = BuildingGeometryUtils.isSlopedRoofEligible(validation.cleanedPoints());
            BuildingFootprint footprint = new BuildingFootprint(validation.cleanedPoints(), rectangular);
            footprint.setName(PlotI18n.tr("plugin.building.default_name", adopted + 1));
            project().addBuilding(footprint);
            adoptedIds.add(footprint.getId());
            adopted++;
        }

        selectedFootprints().clear();
        if (adopted > 0) {
            selection().selectAll(adoptedIds);
            clearPreview();
        }
        if (adopted == 0) {
            setProjectStatus(skipped > 0
                ? PlotI18n.tr("plugin.building.adopt_all_invalid", skipped)
                : PlotI18n.tr("plugin.building.adopt_no_selection"));
        } else if (skipped > 0) {
            setProjectStatus(PlotI18n.tr("plugin.building.adopt_success_batch_partial", adopted, skipped));
            if (!rejectHints.isEmpty()) {
                setProjectStatus(projectStatus() + " — " + String.join("; ", rejectHints));
            }
        } else if (adopted > 1) {
            setProjectStatus(PlotI18n.tr("plugin.building.adopt_success_batch", adopted));
        } else {
            setProjectStatus(PlotI18n.tr("plugin.building.adopt_success"));
        }
    }

    public World getClientWorld() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null ? client.world : null;
    }

    public void onProjectLoaded(String filePath, Path projectsDir) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        String targetFile = ProjectPathResolver.sidecarFileName(filePath);
        Path file = projectsDir.resolve(targetFile);
        if (loadProjectFile(file)) {
            setCurrentProjectFile(targetFile);
            setProjectStatus(PlotI18n.tr("plugin.building.project.loaded", filePath));
        }
    }

    public void onProjectSaved(String filePath, Path projectsDir) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        setCurrentProjectFile(ProjectPathResolver.sidecarFileName(filePath));
        if (saveProjectFile(projectsDir.resolve(currentProjectFile()))) {
            setProjectStatus(PlotI18n.tr("plugin.building.project.saved", filePath));
        }
    }

    public void persistProject(Path projectsDir) {
        saveProjectFile(projectsDir.resolve(currentProjectFile()));
    }

    public boolean loadProjectFile(Path file) {
        try {
            BuildingProject loaded = BuildingProject.loadFrom(file);
            setProject(loaded);
            projectHistory().clear();
            selection().clear();
            if (!project().getBuildings().isEmpty()) {
                selection().select(project().getBuildings().keySet().iterator().next(), false);
            }
            resetAfterProjectLoad();
            return true;
        } catch (IOException e) {
            LOGGER.error("加载建筑项目失败: {}", e.getMessage(), e);
            setProjectStatus(PlotI18n.tr("plugin.building.project.load_failed", file.getFileName()));
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
            setCurrentProjectFile(defaultProjectFile);
            setProjectStatus(PlotI18n.tr("plugin.building.project.default_loaded"));
        }
    }

    public boolean saveProjectFile(Path file) {
        if (file == null || project() == null) {
            return false;
        }
        try {
            String json = project().toJson();
            if (state.getContentFingerprint().isUnchanged(json, file)) {
                LOGGER.debug("建筑项目内容未变，跳过重复保存: {}", file.getFileName());
                return true;
            }
            project().saveTo(file);
            state.getContentFingerprint().markSaved(json, file);
            return true;
        } catch (IOException e) {
            LOGGER.error("保存建筑项目失败: {}", e.getMessage(), e);
            setProjectStatus(PlotI18n.tr("plugin.building.project.save_failed", file.getFileName()));
            return false;
        }
    }
}
