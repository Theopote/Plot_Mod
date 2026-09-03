package com.plot.plugin.earthwork.ui;

import com.plot.core.context.PluginContext;
import com.plot.core.state.DebouncedTasks;
import com.plot.core.model.Shape;
import com.plot.plugin.config.EarthworkConfig;
import com.plot.plugin.earthwork.EarthworkRegionPickSession;
import com.plot.plugin.earthwork.EarthworkRegionListHelper;
import com.plot.plugin.earthwork.EarthworkThreePointPickSession;
import com.plot.plugin.earthwork.terrain.TerrainSnapshotCache;
import com.plot.plugin.earthwork.manager.EarthworkBuildManager;
import com.plot.plugin.earthwork.manager.EarthworkPreviewManager;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkProjectHistory;
import com.plot.plugin.earthwork.model.GradingRegion;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 土方 UI 层共享依赖与可变界面状态。
 */
public final class EarthworkUiContext {
    private final PluginContext host;
    private final EarthworkProjectHistory projectHistory;
    private final EarthworkRegionPickSession pickSession;
    private final EarthworkThreePointPickSession threePointPickSession;
    private final TerrainSnapshotCache terrainSnapshotCache;
    private final EarthworkPreviewManager previewManager;
    private final EarthworkBuildManager buildManager;
    private final List<Shape> selectedRegions = new ArrayList<>();

    private EarthworkConfig config;
    private EarthworkProject project;

    private volatile String selectedRegionId = "";
    private volatile String projectStatus = "";

    private String regionNameEditingRegionId = "";
    private String pendingDeleteRegionId = "";
    private boolean deleteConfirmPending = false;
    private boolean buildConfirmPending = false;

    private EarthworkRegionListHelper.SortMode regionSortMode =
        EarthworkRegionListHelper.SortMode.INSERTION;

    private final ImBoolean autoBalanceRef = new ImBoolean(true);
    private final ImBoolean showGridRef = new ImBoolean(true);
    private final ImBoolean showEdgeTreatmentOverlayRef = new ImBoolean(true);
    private final ImString regionNameBuffer = new ImString(64);
    private final imgui.type.ImInt workModeIndex = new imgui.type.ImInt(0);
    private final imgui.type.ImInt regionIndex = new imgui.type.ImInt(0);

    public EarthworkUiContext(
            PluginContext host,
            EarthworkConfig config,
            EarthworkProject project,
            EarthworkProjectHistory projectHistory,
            EarthworkRegionPickSession pickSession,
            EarthworkThreePointPickSession threePointPickSession,
            TerrainSnapshotCache terrainSnapshotCache,
            EarthworkPreviewManager previewManager,
            EarthworkBuildManager buildManager) {
        this.host = Objects.requireNonNull(host, "host");
        this.config = Objects.requireNonNull(config, "config");
        this.project = Objects.requireNonNull(project, "project");
        this.projectHistory = Objects.requireNonNull(projectHistory, "projectHistory");
        this.pickSession = Objects.requireNonNull(pickSession, "pickSession");
        this.threePointPickSession = Objects.requireNonNull(threePointPickSession, "threePointPickSession");
        this.terrainSnapshotCache = Objects.requireNonNull(terrainSnapshotCache, "terrainSnapshotCache");
        this.previewManager = Objects.requireNonNull(previewManager, "previewManager");
        this.buildManager = Objects.requireNonNull(buildManager, "buildManager");
    }

    public PluginContext host() {
        return host;
    }

    public EarthworkConfig config() {
        return config;
    }

    public void setConfig(EarthworkConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public EarthworkProject project() {
        return project;
    }

    public void setProject(EarthworkProject project) {
        this.project = Objects.requireNonNull(project, "project");
    }

    public EarthworkProjectHistory projectHistory() {
        return projectHistory;
    }

    public EarthworkRegionPickSession pickSession() {
        return pickSession;
    }

    public EarthworkThreePointPickSession threePointPickSession() {
        return threePointPickSession;
    }

    public TerrainSnapshotCache terrainSnapshotCache() {
        return terrainSnapshotCache;
    }

    public EarthworkPreviewManager previewManager() {
        return previewManager;
    }

    public EarthworkBuildManager buildManager() {
        return buildManager;
    }

    public List<Shape> selectedRegions() {
        return selectedRegions;
    }

    public String selectedRegionId() {
        return selectedRegionId;
    }

    public void setSelectedRegionId(String selectedRegionId) {
        this.selectedRegionId = selectedRegionId != null ? selectedRegionId : "";
    }

    public String projectStatus() {
        return projectStatus;
    }

    public void setProjectStatus(String projectStatus) {
        this.projectStatus = projectStatus != null ? projectStatus : "";
    }

    public String regionNameEditingRegionId() {
        return regionNameEditingRegionId;
    }

    public void setRegionNameEditingRegionId(String regionNameEditingRegionId) {
        this.regionNameEditingRegionId = regionNameEditingRegionId != null ? regionNameEditingRegionId : "";
    }

    public String pendingDeleteRegionId() {
        return pendingDeleteRegionId;
    }

    public void setPendingDeleteRegionId(String pendingDeleteRegionId) {
        this.pendingDeleteRegionId = pendingDeleteRegionId != null ? pendingDeleteRegionId : "";
    }

    public boolean deleteConfirmPending() {
        return deleteConfirmPending;
    }

    public void setDeleteConfirmPending(boolean deleteConfirmPending) {
        this.deleteConfirmPending = deleteConfirmPending;
    }

    public boolean buildConfirmPending() {
        return buildConfirmPending;
    }

    public void setBuildConfirmPending(boolean buildConfirmPending) {
        this.buildConfirmPending = buildConfirmPending;
    }

    public EarthworkRegionListHelper.SortMode regionSortMode() {
        return regionSortMode;
    }

    public void setRegionSortMode(EarthworkRegionListHelper.SortMode regionSortMode) {
        this.regionSortMode = regionSortMode != null
            ? regionSortMode
            : EarthworkRegionListHelper.SortMode.INSERTION;
    }

    public ImBoolean autoBalanceRef() {
        return autoBalanceRef;
    }

    public ImBoolean showGridRef() {
        return showGridRef;
    }

    public ImBoolean showEdgeTreatmentOverlayRef() {
        return showEdgeTreatmentOverlayRef;
    }

    public ImString regionNameBuffer() {
        return regionNameBuffer;
    }

    public imgui.type.ImInt workModeIndex() {
        return workModeIndex;
    }

    public imgui.type.ImInt regionIndex() {
        return regionIndex;
    }

    public boolean recalculatePreview() {
        GradingRegion region = project.getRegion(selectedRegionId);
        if (region == null) {
            return false;
        }
        return previewManager.calculatePreview(
            project,
            region,
            EarthworkUiLookups.createBuildingFootprintLookup(),
            EarthworkUiLookups.createRoadSurfaceLookup());
    }

    /** 选项拖动/切换时合并到下一次渲染线程计算，避免每帧同步跑完整管线。 */
    public void scheduleRecalculatePreview() {
        DebouncedTasks.publishDebounced("earthwork.preview.recalculate", () -> {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client != null) {
                client.execute(this::recalculatePreview);
            }
        }, 280);
    }

    public void clearPreview() {
        previewManager.clearPreview();
    }

    public void invalidatePreview() {
        previewManager.invalidatePreview(project);
    }

    public void resetAfterProjectLoad(String firstRegionId) {
        regionNameEditingRegionId = "";
        pickSession.cancel();
        threePointPickSession.cancel();
        selectedRegions.clear();
        terrainSnapshotCache.clear();
        clearPreview();
        setSelectedRegionId(firstRegionId);
    }
}
