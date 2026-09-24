package com.plot.plugin.road.ui;

import com.plot.core.context.PluginContext;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadPersistenceManager;
import com.plot.plugin.road.manager.RoadPreviewManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.manager.RoadToolManager;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.model.Road;
import com.plot.ui.canvas.CanvasAccess;
import com.plot.ui.utils.ImStringUtf8;
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
    private final ImBoolean showRoadOverlay = new ImBoolean(true);
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
    private Runnable pathsAdoptedListener;
    private boolean overlayForegroundDirty;

    private final RoadListRenameController roadListRename = new RoadListRenameController(this);
    private final ImString roadNameBuffer = new ImString(128);
    private String roadNameEditingId = "";
    private String roadNameBeforeRename = "";
    private boolean roadNameFocusPending;
    private int roadNameIgnoreOutsideClickFrames;

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

    public RoadListRenameController roadListRename() {
        return roadListRename;
    }

    public String roadNameEditingId() {
        return roadNameEditingId;
    }

    public ImString roadNameBuffer() {
        return roadNameBuffer;
    }

    public void beginRoadNameRename(Road road) {
        if (road == null) {
            return;
        }
        if (!roadNameEditingId.isBlank() && !roadNameEditingId.equals(road.getId())) {
            Road previous = networkManager.getNetwork().getRoad(roadNameEditingId);
            if (previous != null) {
                cancelRoadNameRename(previous);
            } else {
                endRoadNameRename();
            }
        }
        roadNameBeforeRename = road.getName() != null ? road.getName() : "";
        roadNameBuffer.set(roadNameBeforeRename);
        roadNameEditingId = road.getId();
        roadNameFocusPending = true;
        roadNameIgnoreOutsideClickFrames = 3;
        networkManager.selectRoad(road.getId(), false);
        requestOverlayRefresh();
    }

    public void commitRoadNameRename(Road road) {
        if (road == null || !road.getId().equals(roadNameEditingId)) {
            endRoadNameRename();
            return;
        }
        String committed = RoadListRenameController.normalizeDraftName(
            ImStringUtf8.read(roadNameBuffer));
        if (!java.util.Objects.equals(road.getName(), committed)) {
            networkManager.pushHistory();
            road.setName(committed);
            requestOverlayRefresh();
        }
        endRoadNameRename();
    }

    public void cancelRoadNameRename(Road road) {
        if (road != null && road.getId().equals(roadNameEditingId)) {
            roadNameBuffer.set(roadNameBeforeRename);
        }
        endRoadNameRename();
    }

    public void endRoadNameRename() {
        roadNameEditingId = "";
        roadNameBeforeRename = "";
        roadNameFocusPending = false;
        roadNameIgnoreOutsideClickFrames = 0;
        roadNameBuffer.set("");
    }

    public void tickRoadNameRenameCooldown() {
        if (roadNameIgnoreOutsideClickFrames > 0) {
            roadNameIgnoreOutsideClickFrames--;
        }
    }

    public boolean isRoadNameOutsideClickReady() {
        return roadNameIgnoreOutsideClickFrames == 0;
    }

    public boolean consumeRoadNameFocusPending() {
        if (!roadNameFocusPending) {
            return false;
        }
        roadNameFocusPending = false;
        return true;
    }

    public ImBoolean adoptIncludeSidewalkRef() {
        return adoptIncludeSidewalkRef;
    }

    public ImBoolean showRoadOverlay() {
        return showRoadOverlay;
    }

    public boolean isRoadOverlayVisible() {
        return showRoadOverlay.get()
            || (toolManager != null && toolManager.getPathPickSession().isActive());
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
     * 推送历史记录并自动使预览失效（快照 + 一次 revision）。
     * 分步 ImGui 编辑请用 {@link #beginNetworkEdit()} / {@link #finishNetworkEdit()}。
     */
    public void pushHistoryAndInvalidatePreview() {
        networkManager.pushHistory();
    }

    /** {@link RoadNetworkManager#beginNetworkEdit()} */
    public void beginNetworkEdit() {
        networkManager.beginNetworkEdit();
    }

    /** {@link RoadNetworkManager#finishNetworkEdit()} */
    public void finishNetworkEdit() {
        networkManager.finishNetworkEdit();
    }

    /** {@link RoadNetworkManager#mutateNetwork(Runnable)} */
    public void editNetwork(Runnable mutation) {
        networkManager.mutateNetwork(mutation);
    }

    public boolean isPreviewJobRunning() {
        return previewManager != null && previewManager.isPreviewJobRunning();
    }

    public void cancelPreviewJob() {
        if (previewManager != null) {
            previewManager.cancelPreviewJob();
        }
    }

    /** 切 Tab 时静默取消进行中的预览 job（不弹状态消息）。 */
    public void cancelPreviewJobSilently() {
        if (previewManager != null) {
            previewManager.cancelPreviewJobSilently();
        }
    }

    /** 清除待确认弹窗、Tab 跳转等瞬时 UI 状态。 */
    public void clearTransientUiState() {
        roadListRename.cancelActive();
        clearPendingTab();
        pendingProfileEdgeId = "";
        clearDeleteConfirmPending();
        clearBuildConfirmPending();
        pendingDeleteEdgeId = "";
        pendingDeleteRoadId = "";
    }

    /**
     * 全局配置（桥/隧阈值、采样、默认横断面等）变更后调用：使预览失效，避免按过期参数落地。
     */
    public void onGenerationConfigChanged() {
        if (previewManager != null) {
            previewManager.invalidatePreview();
        }
        requestOverlayRefresh();
    }

    /** 横断面/默认参数变更后请求画布叠加层在当帧 UI 之后重绘。 */
    public void requestOverlayRefresh() {
        overlayForegroundDirty = true;
        if (CanvasAccess.isPresent()) {
            CanvasAccess.get().markToolPreviewDirty();
        }
    }

    /** 道路横断面等会改变 overlay 几何的编辑：推入撤销并标记当帧前景补绘。 */
    public void pushRoadEditHistory() {
        networkManager.pushHistory();
        requestOverlayRefresh();
    }

    public boolean consumeOverlayForegroundDirty() {
        if (!overlayForegroundDirty) {
            return false;
        }
        overlayForegroundDirty = false;
        return true;
    }

    /** 跳转到编辑 Tab 并选中指定逻辑道路。 */
    public void requestEditRoad(String roadId) {
        if (roadId != null && !roadId.isBlank()) {
            networkManager.selectRoad(roadId, false);
        }
        requestTab(RoadUiTab.EDIT);
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

    /** 跳转到建造 Tab 并聚焦指定边的纵断面。 */
    public void requestViewProfile(String edgeId) {
        pendingTab = RoadUiTab.GENERATE;
        pendingProfileEdgeId = edgeId != null ? edgeId : "";
    }

    public String consumePendingProfileEdgeId() {
        String edgeId = pendingProfileEdgeId;
        pendingProfileEdgeId = "";
        return edgeId;
    }

    public void setPathsAdoptedListener(Runnable listener) {
        this.pathsAdoptedListener = listener;
    }

    /** 路径已自动/手动认领为道路后的 UI 后续（选中、切 Tab 等）。 */
    public void notifyPathsAdopted() {
        if (pathsAdoptedListener != null) {
            pathsAdoptedListener.run();
        }
    }
}
