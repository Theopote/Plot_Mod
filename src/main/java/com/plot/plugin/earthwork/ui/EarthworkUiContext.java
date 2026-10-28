package com.plot.plugin.earthwork.ui;

import com.plot.api.world.ICoordinateService;
import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.core.context.PluginContext;
import com.plot.core.state.DebouncedTasks;
import com.plot.core.model.Shape;
import com.plot.plugin.config.EarthworkConfig;
import com.plot.plugin.earthwork.EarthworkRegionBlockCountCache;
import com.plot.plugin.earthwork.EarthworkRegionPickSession;
import com.plot.plugin.earthwork.EarthworkThreePointPickSession;
import imgui.ImGui;
import com.plot.plugin.earthwork.terrain.TerrainSnapshotCache;
import com.plot.plugin.earthwork.manager.EarthworkBuildManager;
import com.plot.plugin.earthwork.manager.EarthworkPreviewManager;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkProjectHistory;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import imgui.type.ImBoolean;

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
    private final EarthworkHolePickSession holePickSession;
    private final TerrainSnapshotCache terrainSnapshotCache;
    private final EarthworkPreviewManager previewManager;
    private final EarthworkBuildManager buildManager;
    private final EarthworkRegionBlockCountCache regionBlockCountCache = new EarthworkRegionBlockCountCache();
    private final EarthworkCutFillHeatmapCache cutFillHeatmapCache = new EarthworkCutFillHeatmapCache();
    private final List<Shape> selectedRegions = new ArrayList<>();

    private int projectionCaptureFrame = -1;
    private WorldProjectionSnapshot projectionSnapshot = WorldProjectionSnapshot.UNKNOWN;

    private EarthworkConfig config;
    private EarthworkProject project;

    private volatile String selectedRegionId = "";
    private volatile String projectStatus = "";
    private volatile com.plot.plugin.earthwork.model.EarthworkStatusLevel projectStatusLevel =
        com.plot.plugin.earthwork.model.EarthworkStatusLevel.INFO;

    private String pendingDeleteRegionId = "";
    private boolean deleteConfirmPending = false;
    private boolean buildConfirmPending = false;

    private final ImBoolean autoBalanceRef = new ImBoolean(true);
    private final ImBoolean showGridRef = new ImBoolean(true);
    private final ImBoolean showEdgeTreatmentOverlayRef = new ImBoolean(true);
    private final ImBoolean showRegionOverlayRef = new ImBoolean(true);
    private final imgui.type.ImInt workModeIndex = new imgui.type.ImInt(0);
    private final imgui.type.ImInt regionIndex = new imgui.type.ImInt(0);

    public EarthworkUiContext(
            PluginContext host,
            EarthworkConfig config,
            EarthworkProject project,
            EarthworkProjectHistory projectHistory,
            EarthworkRegionPickSession pickSession,
            EarthworkThreePointPickSession threePointPickSession,
            EarthworkHolePickSession holePickSession,
            TerrainSnapshotCache terrainSnapshotCache,
            EarthworkPreviewManager previewManager,
            EarthworkBuildManager buildManager) {
        this.host = Objects.requireNonNull(host, "host");
        this.config = Objects.requireNonNull(config, "config");
        this.project = Objects.requireNonNull(project, "project");
        this.projectHistory = Objects.requireNonNull(projectHistory, "projectHistory");
        this.pickSession = Objects.requireNonNull(pickSession, "pickSession");
        this.threePointPickSession = Objects.requireNonNull(threePointPickSession, "threePointPickSession");
        this.holePickSession = Objects.requireNonNull(holePickSession, "holePickSession");
        this.terrainSnapshotCache = Objects.requireNonNull(terrainSnapshotCache, "terrainSnapshotCache");
        this.previewManager = Objects.requireNonNull(previewManager, "previewManager");
        this.buildManager = Objects.requireNonNull(buildManager, "buildManager");
    }

    public PluginContext host() {
        return host;
    }

    public ICoordinateService coordinates() {
        return host.coordinates();
    }

    /** 每帧捕获一次视图投影，供区域 Tab 占地方块数统计复用。 */
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

    public EarthworkRegionBlockCountCache regionBlockCountCache() {
        return regionBlockCountCache;
    }

    EarthworkCutFillHeatmapCache cutFillHeatmapCache() {
        return cutFillHeatmapCache;
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

    public EarthworkHolePickSession holePickSession() {
        return holePickSession;
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
        String next = selectedRegionId != null ? selectedRegionId : "";
        String previous = this.selectedRegionId;
        this.selectedRegionId = next;
        if (!Objects.equals(previous, next)) {
            EarthworkGenerationResult preview = previewManager.getLastGenerationResult();
            if (preview != null && !next.equals(preview.sourceRegionId)) {
                invalidatePreview();
            }
        }
    }

    public String projectStatus() {
        return projectStatus;
    }

    public void setProjectStatus(String projectStatus) {
        setProjectStatus(projectStatus, com.plot.plugin.earthwork.model.EarthworkStatusLevel.INFO);
    }

    public com.plot.plugin.earthwork.model.EarthworkStatusLevel projectStatusLevel() {
        return projectStatusLevel;
    }

    public void setProjectStatus(String projectStatus,
            com.plot.plugin.earthwork.model.EarthworkStatusLevel level) {
        this.projectStatusLevel = Objects.requireNonNull(level, "level");
        this.projectStatus = projectStatus != null ? projectStatus : "";
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

    public ImBoolean autoBalanceRef() {
        return autoBalanceRef;
    }

    public ImBoolean showGridRef() {
        return showGridRef;
    }

    public ImBoolean showEdgeTreatmentOverlayRef() {
        return showEdgeTreatmentOverlayRef;
    }

    public ImBoolean showRegionOverlayRef() {
        return showRegionOverlayRef;
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
        if (host.placement().isBusy()) {
            setProjectStatus(com.plot.utils.PlotI18n.tr("plugin.earthwork.build_in_progress_wait"),
                com.plot.plugin.earthwork.model.EarthworkStatusLevel.INFO);
            return false;
        }
        if (EarthworkUiPresentation.missingManualHeight(region)) {
            previewManager.markDirty();
            setProjectStatus(com.plot.utils.PlotI18n.tr("plugin.earthwork.ui.enter_height"),
                com.plot.plugin.earthwork.model.EarthworkStatusLevel.WARNING);
            return false;
        }
        return previewManager.calculatePreview(
            project,
            region,
            EarthworkUiLookups.createBuildingFootprintLookup(),
            EarthworkUiLookups.createRoadSurfaceLookup(),
            config.getWorkMode());
    }

    /** 选项拖动/切换时合并到下一次渲染线程计算，避免每帧同步跑完整管线。 */
    public void scheduleRecalculatePreview() {
        previewManager.markDirty();
        long generation = previewManager.getPreviewGeneration();
        String regionId = selectedRegionId;
        DebouncedTasks.publishDebounced("earthwork.preview.recalculate", () -> {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client != null) {
                client.execute(() -> {
                    if (generation == previewManager.getPreviewGeneration()
                        && Objects.equals(regionId, selectedRegionId)) {
                        recalculatePreview();
                    }
                });
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
        pickSession.cancel();
        threePointPickSession.cancel();
        holePickSession.cancel();
        selectedRegions.clear();
        terrainSnapshotCache.clear();
        clearPreview();
        setSelectedRegionId(firstRegionId);
    }
}
