package com.plot.plugin.building.ui;

import com.plot.core.model.Shape;
import com.plot.core.persistence.ContentFingerprint;
import com.plot.plugin.building.BuildingBatchEditor;
import com.plot.plugin.building.BuildingFootprintPickSession;
import com.plot.plugin.building.BuildingHeightDistribution;
import com.plot.plugin.building.BuildingListHelper;
import com.plot.plugin.building.BuildingSelectionSet;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.DistrictBuildReport;
import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.model.BuildingProject;
import com.plot.plugin.building.model.BuildingProjectHistory;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;

/** 建筑插件可变 UI/会话状态，不含业务逻辑。 */
public final class BuildingPluginState {
    private BuildingProject project = new BuildingProject();
    private final BuildingProjectHistory projectHistory = new BuildingProjectHistory();
    private final BuildingSelectionSet selection = new BuildingSelectionSet();
    private final BuildingFootprintPickSession pickSession = new BuildingFootprintPickSession();
    private final List<Shape> selectedFootprints = new ArrayList<>();

    private volatile BuildingGenerationResult lastGenerationResult;
    private volatile DistrictGenerationResult lastDistrictResult;
    private volatile DistrictBuildReport lastDistrictBuildReport;

    private String buildingNameEditingId = "";
    private BuildingListHelper.SortMode buildingSortMode = BuildingListHelper.SortMode.INSERTION;
    private final BuildingBatchEditor.FieldMask batchFieldMask = BuildingBatchEditor.FieldMask.allMassing();
    private BuildingHeightDistribution.Mode heightDistMode = BuildingHeightDistribution.Mode.RANDOM;
    private int heightDistMinFloors = 4;
    private int heightDistMaxFloors = 8;
    private long heightDistSeed = 0L;
    private boolean heightDistSeedManual = false;
    private final ImString heightDistSeedBuffer = new ImString(24);

    private final ImBoolean manualElevationRef = new ImBoolean(false);
    private final ImString buildingNameBuffer = new ImString(64);

    private final List<String> pendingDeleteBuildingIds = new ArrayList<>();
    private boolean deleteConfirmPending = false;
    private boolean buildConfirmPending = false;

    private volatile String projectStatus = "";
    private String currentProjectFile = "default.json";
    private final ContentFingerprint.Tracker contentFingerprint = new ContentFingerprint.Tracker();

    public BuildingProject getProject() {
        return project;
    }

    public void setProject(BuildingProject project) {
        this.project = project != null ? project : new BuildingProject();
    }

    public BuildingProjectHistory getProjectHistory() {
        return projectHistory;
    }

    public BuildingSelectionSet getSelection() {
        return selection;
    }

    public BuildingFootprintPickSession getPickSession() {
        return pickSession;
    }

    public List<Shape> getSelectedFootprints() {
        return selectedFootprints;
    }

    public BuildingGenerationResult getLastGenerationResult() {
        return lastGenerationResult;
    }

    public void setLastGenerationResult(BuildingGenerationResult lastGenerationResult) {
        this.lastGenerationResult = lastGenerationResult;
    }

    public DistrictGenerationResult getLastDistrictResult() {
        return lastDistrictResult;
    }

    public void setLastDistrictResult(DistrictGenerationResult lastDistrictResult) {
        this.lastDistrictResult = lastDistrictResult;
    }

    public DistrictBuildReport getLastDistrictBuildReport() {
        return lastDistrictBuildReport;
    }

    public void setLastDistrictBuildReport(DistrictBuildReport lastDistrictBuildReport) {
        this.lastDistrictBuildReport = lastDistrictBuildReport;
    }

    public String getBuildingNameEditingId() {
        return buildingNameEditingId;
    }

    public void setBuildingNameEditingId(String buildingNameEditingId) {
        this.buildingNameEditingId = buildingNameEditingId != null ? buildingNameEditingId : "";
    }

    public BuildingListHelper.SortMode getBuildingSortMode() {
        return buildingSortMode;
    }

    public void setBuildingSortMode(BuildingListHelper.SortMode buildingSortMode) {
        this.buildingSortMode = buildingSortMode != null
            ? buildingSortMode
            : BuildingListHelper.SortMode.INSERTION;
    }

    public BuildingBatchEditor.FieldMask getBatchFieldMask() {
        return batchFieldMask;
    }

    public BuildingHeightDistribution.Mode getHeightDistMode() {
        return heightDistMode;
    }

    public void setHeightDistMode(BuildingHeightDistribution.Mode heightDistMode) {
        this.heightDistMode = heightDistMode != null
            ? heightDistMode
            : BuildingHeightDistribution.Mode.RANDOM;
    }

    public int getHeightDistMinFloors() {
        return heightDistMinFloors;
    }

    public void setHeightDistMinFloors(int heightDistMinFloors) {
        this.heightDistMinFloors = heightDistMinFloors;
    }

    public int getHeightDistMaxFloors() {
        return heightDistMaxFloors;
    }

    public void setHeightDistMaxFloors(int heightDistMaxFloors) {
        this.heightDistMaxFloors = heightDistMaxFloors;
    }

    public long getHeightDistSeed() {
        return heightDistSeed;
    }

    public void setHeightDistSeed(long heightDistSeed) {
        this.heightDistSeed = heightDistSeed;
    }

    public boolean isHeightDistSeedManual() {
        return heightDistSeedManual;
    }

    public void setHeightDistSeedManual(boolean heightDistSeedManual) {
        this.heightDistSeedManual = heightDistSeedManual;
    }

    public ImString getHeightDistSeedBuffer() {
        return heightDistSeedBuffer;
    }

    public ImBoolean getManualElevationRef() {
        return manualElevationRef;
    }

    public ImString getBuildingNameBuffer() {
        return buildingNameBuffer;
    }

    public List<String> getPendingDeleteBuildingIds() {
        return pendingDeleteBuildingIds;
    }

    public boolean isDeleteConfirmPending() {
        return deleteConfirmPending;
    }

    public void setDeleteConfirmPending(boolean deleteConfirmPending) {
        this.deleteConfirmPending = deleteConfirmPending;
    }

    public boolean isBuildConfirmPending() {
        return buildConfirmPending;
    }

    public void setBuildConfirmPending(boolean buildConfirmPending) {
        this.buildConfirmPending = buildConfirmPending;
    }

    public String getProjectStatus() {
        return projectStatus;
    }

    public void setProjectStatus(String projectStatus) {
        this.projectStatus = projectStatus != null ? projectStatus : "";
    }

    public String getCurrentProjectFile() {
        return currentProjectFile;
    }

    public void setCurrentProjectFile(String currentProjectFile) {
        this.currentProjectFile = currentProjectFile != null ? currentProjectFile : "default.json";
    }

    public ContentFingerprint.Tracker getContentFingerprint() {
        return contentFingerprint;
    }
}
