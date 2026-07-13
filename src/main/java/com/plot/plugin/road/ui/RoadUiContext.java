package com.plot.plugin.road.ui;

import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadPersistenceManager;
import com.plot.plugin.road.manager.RoadPreviewManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.manager.RoadToolManager;
import com.plot.plugin.road.RoadEdgeListHelper;
import imgui.type.ImBoolean;
import imgui.type.ImString;

/**
 * 道路 UI 层共享依赖与可变界面状态。
 */
public final class RoadUiContext {
    private final RoadNetworkManager networkManager;
    private final RoadPreviewManager previewManager;
    private final RoadPersistenceManager persistenceManager;
    private final RoadToolManager toolManager;
    private final RoadProjectStatus status;

    private final ImBoolean adoptIncludeSidewalkRef = new ImBoolean(false);
    private final ImString edgeSearchBuffer = new ImString(128);
    private RoadEdgeListHelper.SortMode edgeSortMode = RoadEdgeListHelper.SortMode.INSERTION;
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

    public RoadUiContext(
            RoadNetworkManager networkManager,
            RoadPreviewManager previewManager,
            RoadPersistenceManager persistenceManager,
            RoadToolManager toolManager,
            RoadProjectStatus status) {
        this.networkManager = networkManager;
        this.previewManager = previewManager;
        this.persistenceManager = persistenceManager;
        this.toolManager = toolManager;
        this.status = status;
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

    public void requestDeleteEdge(String edgeId) {
        pendingDeleteEdgeId = edgeId;
        pendingDeleteRoadId = "";
        deleteConfirmPending = true;
    }

    public void requestDeleteRoad(String roadId) {
        pendingDeleteRoadId = roadId;
        pendingDeleteEdgeId = "";
        deleteConfirmPending = true;
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
}
