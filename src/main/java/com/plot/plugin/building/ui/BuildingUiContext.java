package com.plot.plugin.building.ui;

import com.plot.plugin.building.BuildingBatchEditor;
import com.plot.plugin.building.BuildingHeightDistribution;
import com.plot.plugin.building.BuildingListHelper;
import com.plot.plugin.building.BuildingSelectionSet;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.DistrictBuildReport;
import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;
import com.plot.plugin.building.model.BuildingProjectHistory;
import com.plot.core.context.PluginContext;
import com.plot.core.model.Shape;

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

    public BuildingUiContext(
            PluginContext host,
            BuildingPluginState state,
            Object projectLock) {
        this.host = Objects.requireNonNull(host, "host");
        this.state = Objects.requireNonNull(state, "state");
        this.projectLock = Objects.requireNonNull(projectLock, "projectLock");
        this.actions = new BuildingActions(host, state, projectLock);
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

    public void projectPreview() {
        actions.projectPreview();
    }

    public void clearPreview() {
        actions.clearPreview();
    }

    public void invalidatePreview() {
        actions.invalidatePreview();
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

    public void selectAllClosedShapesOnCanvas() {
        actions.selectAllClosedShapesOnCanvas();
    }

    public double computeSelectedFootprintArea() {
        return actions.computeSelectedFootprintArea();
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
