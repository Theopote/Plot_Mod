package com.plot.plugin.building.ui;

import com.plot.core.model.Shape;
import com.plot.core.persistence.ContentFingerprint;
import com.plot.plugin.building.BuildingBatchEditor;
import com.plot.plugin.building.BuildingBlockCountCache;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 建筑插件可变 UI/会话状态，不含业务逻辑。 */
public final class BuildingPluginState {
    private BuildingProject project = new BuildingProject();
    private final BuildingProjectHistory projectHistory = new BuildingProjectHistory();
    private final BuildingSelectionSet selection = new BuildingSelectionSet();
    private final BuildingBlockCountCache blockCountCache = new BuildingBlockCountCache();
    private final BuildingFootprintPickSession pickSession = new BuildingFootprintPickSession();
    private final List<Shape> selectedFootprints = new ArrayList<>();

    private volatile BuildingGenerationResult lastGenerationResult;
    private volatile DistrictGenerationResult lastDistrictResult;
    private volatile DistrictBuildReport lastDistrictBuildReport;
    private BuildingPreviewIdentity previewIdentity;
    private java.util.Set<String> overlayPreviewedBuildingIds = java.util.Set.of();
    private java.util.Set<String> overlayWarningBuildingIds = java.util.Set.of();
    /** 预览完成后缓存的体量图高度（方块），避免每帧扫描 placement。 */
    private Map<String, Double> massingPreviewHeightBlocks = Map.of();

    private String buildingNameEditingId = "";
    private String buildingNameBeforeRename = "";
    private boolean buildingNameFocusPending = false;
    private int buildingNameIgnoreOutsideClickFrames = 0;
    private BuildingListHelper.SortMode buildingSortMode = BuildingListHelper.SortMode.INSERTION;
    private final BuildingBatchEditor.FieldMask batchFieldMask = BuildingBatchEditor.FieldMask.allMassing();
    private BuildingHeightDistribution.Mode heightDistMode = BuildingHeightDistribution.Mode.RANDOM;
    private int heightDistMinFloors = 4;
    private int heightDistMaxFloors = 8;
    private long heightDistSeed = 0L;
    private boolean heightDistSeedManual = false;
    private final ImString heightDistSeedBuffer = new ImString(24);

    private final ImBoolean manualElevationRef = new ImBoolean(false);
    private final ImBoolean showFootprintOverlay = new ImBoolean(true);
    private final ImString buildingNameBuffer = new ImString(64);

    private final List<String> pendingDeleteBuildingIds = new ArrayList<>();
    private boolean deleteConfirmPending = false;
    private boolean buildConfirmPending = false;
    private boolean generateScopeAll = false;
    private boolean batchScopeAll = false;

    private volatile DistrictPreviewJob districtPreviewJob;
    private boolean districtPreviewBuildConfirmPending;

    private volatile String projectStatus = "";
    private String currentProjectFile = "default.json";
    private final ContentFingerprint.Tracker contentFingerprint = new ContentFingerprint.Tracker();

    /** 加门编辑器 draft，按 building id 跨帧保持（P1-1）。 */
    private final Map<String, DoorEditorDraft> doorEditorDrafts = new HashMap<>();

    public static final class DoorEditorDraft {
        public int wallSegment = 0;
        public float positionRatio = 0.5f;
        public int floor = 0;
    }

    public DoorEditorDraft doorEditorDraftFor(String buildingId) {
        if (buildingId == null || buildingId.isBlank()) {
            return new DoorEditorDraft();
        }
        return doorEditorDrafts.computeIfAbsent(buildingId, id -> new DoorEditorDraft());
    }

    public void clampDoorEditorDraft(String buildingId, int maxWallSegment, int maxFloor) {
        DoorEditorDraft draft = doorEditorDraftFor(buildingId);
        draft.wallSegment = Math.clamp(draft.wallSegment, 0, Math.max(0, maxWallSegment));
        draft.positionRatio = Math.clamp(draft.positionRatio, 0.0f, 1.0f);
        draft.floor = Math.clamp(draft.floor, 0, Math.max(0, maxFloor));
    }

    public BuildingProject getProject() {
        return project;
    }

    public void setProject(BuildingProject project) {
        this.project = project != null ? project : new BuildingProject();
        blockCountCache.retainOnly(this.project.getBuildings().keySet());
    }

    public BuildingBlockCountCache getBlockCountCache() {
        return blockCountCache;
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

    public BuildingPreviewIdentity getPreviewIdentity() {
        return previewIdentity;
    }

    public void setPreviewIdentity(BuildingPreviewIdentity previewIdentity) {
        this.previewIdentity = previewIdentity;
    }

    public java.util.Set<String> getOverlayPreviewedBuildingIds() {
        return overlayPreviewedBuildingIds;
    }

    public void setOverlayPreviewedBuildingIds(java.util.Set<String> overlayPreviewedBuildingIds) {
        this.overlayPreviewedBuildingIds = overlayPreviewedBuildingIds != null
            ? java.util.Set.copyOf(overlayPreviewedBuildingIds)
            : java.util.Set.of();
    }

    public java.util.Set<String> getOverlayWarningBuildingIds() {
        return overlayWarningBuildingIds;
    }

    public void setOverlayWarningBuildingIds(java.util.Set<String> overlayWarningBuildingIds) {
        this.overlayWarningBuildingIds = overlayWarningBuildingIds != null
            ? java.util.Set.copyOf(overlayWarningBuildingIds)
            : java.util.Set.of();
    }

    public Map<String, Double> getMassingPreviewHeightBlocks() {
        return massingPreviewHeightBlocks;
    }

    public void setMassingPreviewHeightBlocks(Map<String, Double> massingPreviewHeightBlocks) {
        this.massingPreviewHeightBlocks = massingPreviewHeightBlocks != null
            ? Map.copyOf(massingPreviewHeightBlocks)
            : Map.of();
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
        if (buildingNameEditingId == null || buildingNameEditingId.isBlank()) {
            endBuildingNameRename();
            return;
        }
        this.buildingNameEditingId = buildingNameEditingId;
    }

    public String getBuildingNameBeforeRename() {
        return buildingNameBeforeRename;
    }

    public void beginBuildingNameRename(String buildingId, String currentName) {
        buildingNameBeforeRename = currentName != null ? currentName : "";
        buildingNameBuffer.set(buildingNameBeforeRename);
        buildingNameEditingId = buildingId != null ? buildingId : "";
        buildingNameFocusPending = !buildingNameEditingId.isEmpty();
        buildingNameIgnoreOutsideClickFrames = 3;
    }

    public void endBuildingNameRename() {
        buildingNameEditingId = "";
        buildingNameBeforeRename = "";
        buildingNameFocusPending = false;
        buildingNameIgnoreOutsideClickFrames = 0;
        buildingNameBuffer.set("");
    }

    public void tickBuildingNameRenameCooldown() {
        if (buildingNameIgnoreOutsideClickFrames > 0) {
            buildingNameIgnoreOutsideClickFrames--;
        }
    }

    public boolean isBuildingNameOutsideClickReady() {
        return buildingNameIgnoreOutsideClickFrames == 0;
    }

    public boolean consumeBuildingNameFocusPending() {
        if (!buildingNameFocusPending) {
            return false;
        }
        buildingNameFocusPending = false;
        return true;
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

    public ImBoolean getShowFootprintOverlay() {
        return showFootprintOverlay;
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

    public boolean isGenerateScopeAll() {
        return generateScopeAll;
    }

    public void setGenerateScopeAll(boolean generateScopeAll) {
        this.generateScopeAll = generateScopeAll;
    }

    public boolean isBatchScopeAll() {
        return batchScopeAll;
    }

    public void setBatchScopeAll(boolean batchScopeAll) {
        this.batchScopeAll = batchScopeAll;
    }

    public DistrictPreviewJob getDistrictPreviewJob() {
        return districtPreviewJob;
    }

    public void setDistrictPreviewJob(DistrictPreviewJob districtPreviewJob) {
        this.districtPreviewJob = districtPreviewJob;
    }

    public boolean isDistrictPreviewBuildConfirmPending() {
        return districtPreviewBuildConfirmPending;
    }

    public void setDistrictPreviewBuildConfirmPending(boolean districtPreviewBuildConfirmPending) {
        this.districtPreviewBuildConfirmPending = districtPreviewBuildConfirmPending;
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
