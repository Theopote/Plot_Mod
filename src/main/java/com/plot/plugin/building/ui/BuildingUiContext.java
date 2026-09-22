package com.plot.plugin.building.ui;

import com.plot.plugin.building.BuildingBatchEditor;
import com.plot.plugin.building.BuildingFootprintSelectionAnalysis;
import com.plot.plugin.building.BuildingHeightDistribution;
import com.plot.plugin.building.BuildingListHelper;
import com.plot.plugin.building.BuildingSelectionSet;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.DistrictBuildReport;
import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;
import com.plot.plugin.building.model.BuildingProjectHistory;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.core.context.PluginContext;
import com.plot.core.model.Shape;
import imgui.ImGui;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 建筑 UI 共享依赖与状态访问；业务动作委托 {@link BuildingActions}。
 */
public final class BuildingUiContext {
    private final PluginContext host;
    private final BuildingPluginState state;
    private final Object projectLock;
    private final BuildingActions actions;
    private final BuildingFootprintRenameController buildingRename;
    private int projectionCaptureFrame = -1;
    private WorldProjectionSnapshot projectionSnapshot = WorldProjectionSnapshot.UNKNOWN;

    public BuildingUiContext(
            PluginContext host,
            BuildingPluginState state,
            Object projectLock) {
        this.host = Objects.requireNonNull(host, "host");
        this.state = Objects.requireNonNull(state, "state");
        this.projectLock = Objects.requireNonNull(projectLock, "projectLock");
        this.actions = new BuildingActions(host, state, projectLock);
        this.buildingRename = new BuildingFootprintRenameController(this);
    }

    public BuildingFootprintRenameController buildingRename() {
        return buildingRename;
    }

    public void setBuildingGenerator(com.plot.plugin.building.BuildingGenerator buildingGenerator) {
        actions.setBuildingGenerator(buildingGenerator);
    }

    public BuildingActions actions() {
        return actions;
    }

    public PluginContext host() {
        return host;
    }

    public ICoordinateService coordinates() {
        return host.coordinates();
    }

    /** 每帧捕获一次视图投影，供轮廓 Tab 方块数统计复用。 */
    public WorldProjectionSnapshot currentProjection() {
        int frame = ImGui.getFrameCount();
        if (frame != projectionCaptureFrame) {
            projectionCaptureFrame = frame;
            try {
                projectionSnapshot = host.coordinates().captureProjection();
            } catch (RuntimeException ignored) {
                projectionSnapshot = WorldProjectionSnapshot.UNKNOWN;
            }
        }
        return projectionSnapshot;
    }

    public BuildingPluginState state() {
        return state;
    }

    public Object projectLock() {
        return projectLock;
    }

    public com.plot.plugin.building.BuildingGenerator buildingGenerator() {
        return actions.buildingGenerator();
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

    public com.plot.plugin.building.BuildingFootprintPickSession pickSession() {
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

    public void beginBuildingNameRename(BuildingFootprint building) {
        if (building == null) {
            return;
        }
        state.beginBuildingNameRename(building.getId(), building.getName());
        selection().select(building.getId(), false);
    }

    public boolean consumeBuildingNameFocusPending() {
        return state.consumeBuildingNameFocusPending();
    }

    public void tickBuildingNameRenameCooldown() {
        state.tickBuildingNameRenameCooldown();
    }

    public boolean isBuildingNameOutsideClickReady() {
        return state.isBuildingNameOutsideClickReady();
    }

    public void commitBuildingNameRename(BuildingFootprint building) {
        if (building == null || !building.getId().equals(state.getBuildingNameEditingId())) {
            state.endBuildingNameRename();
            return;
        }
        String trimmed = state.getBuildingNameBuffer().get().trim();
        if (!trimmed.isEmpty() && !trimmed.equals(building.getName())) {
            projectHistory().push(project());
            building.setName(trimmed);
        }
        state.endBuildingNameRename();
    }

    public void cancelBuildingNameRename(BuildingFootprint building) {
        if (building != null && building.getId().equals(state.getBuildingNameEditingId())) {
            state.getBuildingNameBuffer().set(state.getBuildingNameBeforeRename());
        }
        state.endBuildingNameRename();
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
        long seed = BuildingHeightDistribution.defaultSeed(state.getProject(), targets);
        state.setHeightDistSeed(seed);
        return seed;
    }

    public BuildingPluginState.DoorEditorDraft doorEditorDraft(BuildingFootprint building) {
        return state.doorEditorDraftFor(building.getId());
    }

    public void clampDoorEditorDraft(BuildingFootprint building) {
        int segmentCount = building.getOuterPoints().size();
        state.clampDoorEditorDraft(
            building.getId(),
            Math.max(0, segmentCount - 1),
            Math.max(0, building.getFloors() - 1));
    }

    public imgui.type.ImBoolean manualElevationRef() {
        return state.getManualElevationRef();
    }

    public imgui.type.ImBoolean showFootprintOverlay() {
        return state.getShowFootprintOverlay();
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

    public boolean generateScopeAll() {
        return state.isGenerateScopeAll();
    }

    public void setGenerateScopeAll(boolean all) {
        state.setGenerateScopeAll(all);
    }

    public boolean batchScopeAll() {
        return state.isBatchScopeAll();
    }

    public void setBatchScopeAll(boolean all) {
        state.setBatchScopeAll(all);
    }

    public List<BuildingFootprint> resolveGenerateTargets() {
        if (state.isGenerateScopeAll()) {
            return new java.util.ArrayList<>(project().getBuildings().values());
        }
        return selection().resolve(project());
    }

    public List<BuildingFootprint> resolveBatchTargets() {
        if (state.isBatchScopeAll()) {
            return new java.util.ArrayList<>(project().getBuildings().values());
        }
        return selection().resolve(project());
    }

    public String currentProjectFile() {
        return state.getCurrentProjectFile();
    }

    public void setCurrentProjectFile(String file) {
        state.setCurrentProjectFile(file);
    }

    public void syncSelectedBuildingAfterHistory() {
        actions.syncSelectedBuildingAfterHistory();
    }

    public void resetAfterProjectLoad() {
        actions.resetAfterProjectLoad();
    }

    public boolean calculatePreview(BuildingFootprint building) {
        return actions.calculatePreview(building);
    }

    public boolean calculateDistrictPreview(List<BuildingFootprint> buildings, boolean autoProjectGhosts) {
        return actions.calculateDistrictPreview(buildings, autoProjectGhosts);
    }

    public boolean calculateDistrictPreview(
            List<BuildingFootprint> buildings,
            boolean autoProjectGhosts,
            boolean buildConfirmOnComplete) {
        return actions.calculateDistrictPreview(buildings, autoProjectGhosts, buildConfirmOnComplete);
    }

    public void tickDistrictPreviewJob() {
        actions.tickDistrictPreviewJob();
    }

    public boolean isDistrictPreviewBusy() {
        return actions.isDistrictPreviewBusy();
    }

    public void cancelDistrictPreviewJob() {
        actions.cancelDistrictPreviewJob();
    }

    public void projectPreview() {
        actions.projectPreview();
    }

    public void clearPreview() {
        actions.clearPreview();
    }

    public void invalidatePreview() {
        actions.invalidatePreview();
    }

    public BuildingPreviewIdentity.Validity previewValidity(List<BuildingFootprint> targets) {
        return actions.previewValidity(targets);
    }

    public boolean hasPreviewResult() {
        return actions.hasPreviewResult();
    }

    public void requestBuildFromCurrentPreview(List<BuildingFootprint> targets) {
        actions.requestBuildFromCurrentPreview(targets);
    }

    public void buildInWorld() {
        actions.buildInWorld();
    }

    public void applyBuildResultStatus(
            com.plot.core.command.commands.BuildingGenerateCommand.ExecutionResult result,
            DistrictGenerationResult district) {
        actions.applyBuildResultStatus(result, district);
    }

    public void locateBuilding(BuildingFootprint building) {
        actions.locateBuilding(building);
    }

    public void startPickSession() {
        actions.startPickSession();
    }

    public void handlePickSessionTick() {
        actions.handlePickSessionTick();
    }

    public void updateSelectedFootprints() {
        actions.updateSelectedFootprints();
    }

    public BuildingFootprintSelectionAnalysis canvasSelectionAnalysis() {
        return actions.canvasSelectionAnalysis();
    }

    public void refreshCanvasFootprintSelection() {
        actions.refreshCanvasFootprintSelection();
    }

    public void selectAllClosedShapesOnCanvas() {
        actions.selectAllClosedShapesOnCanvas();
    }

    public int computeSelectedFootprintBlockCount() {
        return actions.computeSelectedFootprintBlockCount();
    }

    public void adoptSelectedFootprints() {
        actions.adoptSelectedFootprints();
    }

    public net.minecraft.world.World getClientWorld() {
        return actions.getClientWorld();
    }

    public void onProjectLoaded(String filePath, Path projectsDir) {
        actions.onProjectLoaded(filePath, projectsDir);
    }

    public void onProjectSaved(String filePath, Path projectsDir) {
        actions.onProjectSaved(filePath, projectsDir);
    }

    public void persistProject(Path projectsDir) {
        actions.persistProject(projectsDir);
    }

    public boolean loadProjectFile(Path file) {
        return actions.loadProjectFile(file);
    }

    public void loadProjectForCurrentProject(Path projectsDir, String defaultProjectFile) {
        actions.loadProjectForCurrentProject(projectsDir, defaultProjectFile);
    }

    public boolean saveProjectFile(Path file) {
        return actions.saveProjectFile(file);
    }
}
