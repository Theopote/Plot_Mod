package com.plot.plugin.road.ui;

import com.plot.core.context.PluginContext;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadPersistenceManager;
import com.plot.plugin.road.manager.RoadPreviewManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.manager.RoadToolManager;
import com.plot.plugin.road.RoadEdgeListHelper;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.util.Objects;

/**
 * 道路 UI 层共享依赖与可变界面状态。
 */
public final class RoadUiContext {
    private final RoadNetworkManager networkManager;
    private final RoadPreviewManager previewManager;
    private final RoadPersistenceManager persistenceManager;
    private final RoadToolManager toolManager;
    private final RoadProjectStatus status;
    private final PluginContext host;

    private final ImBoolean adoptIncludeSidewalkRef = new ImBoolean(false);
    private final ImString edgeSearchBuffer = new ImString(128);
    private RoadEdgeListHelper.SortMode edgeSortMode = RoadEdgeListHelper.SortMode.ROAD_GROUP;
    private boolean coordFilterEnabled = false;
    private final float[] coordMinX = {0f};
    private final float[] coordMaxX = {100f};
    private final float[] coordMinY = {0f};
    private final float[] coordMaxY = {100f};
    private final ImBoolean batchIncludeSidewalkRef = new ImBoolean(true);

    private String pendingDeleteEdgeId = "";
    private String pendingDeleteRoadId = "";
    private boolean deleteConfirmPending = false;
    private boolean buildConfirmPending = false;

    private RoadUiTab pendingTab = null;
    private String pendingProfileEdgeId = "";

    public RoadUiContext(
            RoadNetworkManager networkManager,
            RoadPreviewManager previewManager,
            RoadPersistenceManager persistenceManager,
            RoadToolManager toolManager,
            RoadProjectStatus status,
            PluginContext host) {
        this.networkManager = networkManager;
        this.previewManager = previewManager;
        this.persistenceManager = persistenceManager;
        this.toolManager = toolManager;
        this.status = status;
        this.host = Objects.requireNonNull(host, "host");
    }

    public RoadNetworkManager networkManager() {
        return networkManager;
    }

    public RoadPreviewManager previewManager() {
        return previewManager;
    }

    public RoadPersistenceManager persistenceManager() {
        return persistenceManager;
    }

    public RoadToolManager toolManager() {
        return toolManager;
    }

    public RoadProjectStatus status() {
        return status;
    }

    public PluginContext host() {
        return host;
    }

    public ImBoolean adoptIncludeSidewalkRef() {
        return adoptIncludeSidewalkRef;
    }

    public ImString edgeSearchBuffer() {
        return edgeSearchBuffer;
    }

    public RoadEdgeListHelper.SortMode edgeSortMode() {
        return edgeSortMode;
    }

    public void setEdgeSortMode(RoadEdgeListHelper.SortMode edgeSortMode) {
        this.edgeSortMode = edgeSortMode;
    }

    public boolean coordFilterEnabled() {
        return coordFilterEnabled;
    }

    public void setCoordFilterEnabled(boolean coordFilterEnabled) {
        this.coordFilterEnabled = coordFilterEnabled;
    }

    public float[] coordMinX() {
        return coordMinX;
    }

    public float[] coordMaxX() {
        return coordMaxX;
    }

    public float[] coordMinY() {
        return coordMinY;
    }

    public float[] coordMaxY() {
        return coordMaxY;
    }

    public ImBoolean batchIncludeSidewalkRef() {
        return batchIncludeSidewalkRef;
    }

    public enum RoadListAction {
        DELETE_ENTIRE_ROAD,
        DELETE_SEGMENT,
        SPLIT_ROAD
    }

    private RoadListAction pendingRoadListAction = null;

    public void requestDeleteEdge(String edgeId) {
        requestDeleteSegment(edgeId);
    }

    public void requestDeleteSegment(String edgeId) {
        pendingRoadListAction = RoadListAction.DELETE_SEGMENT;
        pendingDeleteEdgeId = edgeId != null ? edgeId : "";
        pendingDeleteRoadId = "";
        deleteConfirmPending = true;
    }

    public void requestDeleteRoad(String roadId) {
        pendingRoadListAction = RoadListAction.DELETE_ENTIRE_ROAD;
        pendingDeleteRoadId = roadId != null ? roadId : "";
        pendingDeleteEdgeId = "";
        deleteConfirmPending = true;
    }

    public void requestSplitRoad(String roadId, String segmentEdgeId) {
        pendingRoadListAction = RoadListAction.SPLIT_ROAD;
        pendingDeleteRoadId = roadId != null ? roadId : "";
        pendingDeleteEdgeId = segmentEdgeId != null ? segmentEdgeId : "";
        deleteConfirmPending = true;
    }

    public RoadListAction pendingRoadListAction() {
        return pendingRoadListAction;
    }

    public void clearPendingRoadListAction() {
        pendingRoadListAction = null;
    }

    public String pendingDeleteEdgeId() {
        return pendingDeleteEdgeId;
    }

    public String pendingDeleteRoadId() {
        return pendingDeleteRoadId;
    }

    public void clearPendingDeleteEdgeId() {
        pendingDeleteEdgeId = "";
        pendingDeleteRoadId = "";
        pendingRoadListAction = null;
    }

    public boolean deleteConfirmPending() {
        return deleteConfirmPending;
    }

    public void clearDeleteConfirmPending() {
        deleteConfirmPending = false;
    }

    public void requestBuildConfirm() {
        buildConfirmPending = true;
    }

    public boolean buildConfirmPending() {
        return buildConfirmPending;
    }

    public void clearBuildConfirmPending() {
        buildConfirmPending = false;
    }

    public RoadEdgeListHelper.CoordFilter currentCoordFilter() {
        double minX = Math.min(coordMinX[0], coordMaxX[0]);
        double maxX = Math.max(coordMinX[0], coordMaxX[0]);
        double minY = Math.min(coordMinY[0], coordMaxY[0]);
        double maxY = Math.max(coordMinY[0], coordMaxY[0]);
        return new RoadEdgeListHelper.CoordFilter(coordFilterEnabled, minX, maxX, minY, maxY);
    }

    /**
     * 推送历史记录并自动使预览失效。
     * {@link RoadNetworkManager#pushHistory()} 已统一触发预览失效，此方法保留为语义明确的入口。
     */
    public void pushHistoryAndInvalidatePreview() {
        networkManager.pushHistory();
    }

    /**
     * 全局配置（桥/隧阈值、采样、默认横断面等）变更后调用：使预览失效，避免按过期参数落地。
     */
    public void onGenerationConfigChanged() {
        if (previewManager != null) {
            previewManager.invalidatePreview();
        }
    }

    public void requestTab(RoadUiTab tab) {
        pendingTab = tab;
    }

    public RoadUiTab pendingTab() {
        return pendingTab;
    }

    public void clearPendingTab() {
        pendingTab = null;
    }

    /** 跳转到生成 Tab 并聚焦指定边的纵断面。 */
    public void requestViewProfile(String edgeId) {
        pendingTab = RoadUiTab.GENERATE;
        pendingProfileEdgeId = edgeId != null ? edgeId : "";
    }

    public String consumePendingProfileEdgeId() {
        String edgeId = pendingProfileEdgeId;
        pendingProfileEdgeId = "";
        return edgeId;
    }
}
