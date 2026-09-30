package com.plot.plugin.road.manager;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.model.Shape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.*;
import com.plot.plugin.road.alignment.HorizontalAlignmentCenterlineMaterializer;
import com.plot.plugin.road.centerline.CenterlineEditResult;
import com.plot.plugin.road.centerline.CenterlineEditStatus;
import com.plot.plugin.road.centerline.RoadCenterlineEditor;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNetworkHistory;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.RoadSegmentOrdering;
import com.plot.plugin.road.model.RoadTopologyInvariantValidator;
import com.plot.plugin.road.model.RoadTopologyRoadSplitter;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.FlatRoadJunctionConflictResolver;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import com.plot.plugin.road.vertical.VerticalAlignmentGradeSmoother;
import com.plot.plugin.road.vertical.VerticalProfileDesignRules;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.core.terrain.TerrainSampler;
import com.plot.utils.PlotI18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 道路网络数据、选择状态与可撤销变更。
 *
 * <p>持有 live {@link RoadNetwork} 的<strong>单写者</strong>：变更应仅在 client / UI 线程通过本类进行。
 * 持久化、预览生成等若需与编辑并发，应对 {@link RoadNetwork#snapshot()} 副本操作，而非共享 live 实例。
 *
 * <p><strong>变更事务协议</strong>（一次用户编辑 = 一次 undo 快照 + 一次 {@link #getNetworkRevision()} + 一次预览失效）：
 * <ul>
 *   <li>{@link #mutateNetwork(Runnable)} — Manager 内原子编辑</li>
 *   <li>{@link #pushUndoSnapshot()} → 修改 → {@link #commitNetworkChange()} — 分步编辑（如 ImGui 拖拽）</li>
 *   <li>{@link #pushHistory()} — 快照并立即提交；ImGui {@code isItemActivated} 推历史时使用</li>
 * </ul>
 */
public final class RoadNetworkManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadNetwork");

    private final RoadSystemConfig config;
    private final RoadNetworkHistory history = new RoadNetworkHistory();
    private final RoadNetworkBuilder networkBuilder;
    private final RoadProjectStatus status;

    private RoadNetwork network = new RoadNetwork();
    private long networkRevision = 0L;
    private final LinkedHashSet<String> selectedEdgeIds = new LinkedHashSet<>();
    private String selectedNodeId = "";
    private String selectedCrossingId = "";
    private String lastSelectedEdgeId = "";
    /** 路网变更时回调（预览失效等），由插件装配。 */
    private Consumer<RoadChangeKind> onNetworkChanged;
    private RoadChangeKind pendingChangeKind = RoadChangeKind.GENERAL;

    /** 批量草稿对应的选择签名；不能复用 lastSelectedEdgeId。 */
    private String lastBatchSelectionKey = "";
    private int batchEditWidth = 5;
    private int batchEditLaneCount = 1;
    private MaterialMix batchEditMaterial = MaterialMix.single(com.plot.plugin.road.RoadMaterialUtils.DEFAULT_ROAD_BLOCK);
    private String batchEditSidewalkMaterial = com.plot.plugin.road.RoadMaterialUtils.DEFAULT_ROAD_BLOCK;
    private boolean batchIncludeShoulder = false;
    private int batchEditShoulderWidth = 1;
    private boolean batchIncludeSidewalk = true;
    private int batchEditSidewalkWidth = 1;
    private boolean batchIncludeDrainage = false;
    private boolean batchIncludeBikeLane = false;
    private int batchEditBikeLaneWidth = 1;
    private boolean batchIncludeMedian = false;
    private int batchEditMedianWidth = 1;
    private int batchStreetlightSpacing = 0;
    private boolean batchLaneDividers = false;
    private CenterLineStyle batchCenterLineStyle = CenterLineStyle.NONE;
    private String batchMarkingMaterial = ResolvedCrossSection.DEFAULT_MARKING_MATERIAL;
    private float batchEditMaxSlope = 10f;
    private boolean batchIncludeSlopeBatter = false;
    private float batchFillSlopeRatio = 0f;
    private float batchCutSlopeRatio = 0f;
    private String batchFillSlopeMaterial = com.plot.plugin.road.RoadMaterialUtils.DEFAULT_ROAD_BLOCK;
    private String batchCutSlopeMaterial = "";
    private boolean adoptIntersectionRepairPending = false;
    private final RoadLoopSeamPickSession loopSeamPickSession = new RoadLoopSeamPickSession();
    private boolean loopSeamRemapConfirmPending = false;
    private com.plot.plugin.road.model.RoadLoopSeam pendingLoopSeamReplacement;
    private double pendingLoopSeamShift = 0.0;

    public RoadNetworkManager(RoadSystemConfig config, RoadProjectStatus status) {
        this(config, status, new RoadNetworkBuilder());
    }

    RoadNetworkManager(
            RoadSystemConfig config,
            RoadProjectStatus status,
            RoadNetworkBuilder networkBuilder) {
        this.config = config;
        this.status = status;
        this.networkBuilder = networkBuilder;
    }

    /**
     * 注册路网变更监听（预览失效等）。重复设置会覆盖。
     */
    public void setOnNetworkChanged(Consumer<RoadChangeKind> onNetworkChanged) {
        this.onNetworkChanged = onNetworkChanged;
    }

    public RoadNetwork getNetwork() {
        return network;
    }

    /** 路网拓扑/属性变更计数，供 UI 缓存失效。 */
    public long getNetworkRevision() {
        return networkRevision;
    }

    /** 最近一次认领因求交 pass 上限未完全处理时为 true，直至 reconcile 成功或再次认领。 */
    public boolean isAdoptIntersectionRepairPending() {
        return adoptIntersectionRepairPending;
    }

    public void setNetwork(RoadNetwork network) {
        this.network = network != null ? network : new RoadNetwork();
        this.network.assertInvariants();
        notifyNetworkChanged();
    }

    public RoadNetworkHistory getHistory() {
        return history;
    }

    public RoadNetworkBuilder getNetworkBuilder() {
        return networkBuilder;
    }

    public RoadSystemConfig getConfig() {
        return config;
    }

    public LinkedHashSet<String> getSelectedEdgeIds() {
        return selectedEdgeIds;
    }

    /** 面向 UI 的逻辑道路选择；隐藏一条道路被拓扑切成许多边的实现细节。 */
    public LinkedHashSet<String> getSelectedRoadIds() {
        LinkedHashSet<String> roadIds = new LinkedHashSet<>();
        for (String edgeId : selectedEdgeIds) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null && edge.getRoadId() != null && !edge.getRoadId().isBlank()) {
                roadIds.add(edge.getRoadId());
            }
        }
        return roadIds;
    }

    public String getSelectedNodeId() {
        return selectedNodeId;
    }

    public RoadNode getSelectedNode() {
        if (selectedNodeId == null || selectedNodeId.isBlank()) {
            return null;
        }
        return network.getNode(selectedNodeId);
    }

    public void setSelectedNodeId(String selectedNodeId) {
        if (selectedNodeId == null || selectedNodeId.isBlank()) {
            this.selectedNodeId = "";
            return;
        }
        if (network.getNode(selectedNodeId) == null) {
            return;
        }
        this.selectedNodeId = selectedNodeId;
        selectedCrossingId = "";
        selectedEdgeIds.clear();
        lastSelectedEdgeId = "";
    }

    public void clearNodeSelection() {
        selectedNodeId = "";
    }

    public String getSelectedCrossingId() {
        return selectedCrossingId;
    }

    public com.plot.plugin.road.crossing.RoadCrossing getSelectedCrossing() {
        if (selectedCrossingId == null || selectedCrossingId.isBlank()) {
            return null;
        }
        return network.getCrossing(selectedCrossingId);
    }

    public void setSelectedCrossingId(String crossingId) {
        if (crossingId == null || crossingId.isBlank()) {
            selectedCrossingId = "";
            return;
        }
        if (network.getCrossing(crossingId) == null) {
            return;
        }
        selectedCrossingId = crossingId;
        selectedNodeId = "";
        selectedEdgeIds.clear();
        lastSelectedEdgeId = "";
    }

    public void clearCrossingSelection() {
        selectedCrossingId = "";
    }

    public void handleCrossingSelect(String crossingId) {
        setSelectedCrossingId(crossingId);
    }

    /**
     * 选中交叉点并高亮关联道路，不清除当前交叉点/节点选择语义。
     */
    public void focusIntersection(
            com.plot.plugin.road.overlay.IntersectionOverlaySource source,
            String id) {
        if (source == null || id == null || id.isBlank()) {
            return;
        }
        switch (source) {
            case CROSSING -> setSelectedCrossingId(id);
            case LEGACY_NODE -> {
                RoadNode node = network.getNode(id);
                if (node == null) {
                    return;
                }
                selectedNodeId = id;
                selectedCrossingId = "";
                selectedEdgeIds.clear();
                lastSelectedEdgeId = "";
            }
        }
        highlightAssociatedRoads(resolveRoadIdsForIntersection(source, id));
    }

    private List<String> resolveRoadIdsForIntersection(
            com.plot.plugin.road.overlay.IntersectionOverlaySource source,
            String id) {
        if (source == com.plot.plugin.road.overlay.IntersectionOverlaySource.CROSSING) {
            com.plot.plugin.road.crossing.RoadCrossing crossing = network.getCrossing(id);
            if (crossing == null) {
                return List.of();
            }
            return List.of(crossing.roadAId(), crossing.roadBId());
        }
        return new ArrayList<>(network.getDistinctRoadIdsAtNode(id));
    }

    private void highlightAssociatedRoads(List<String> roadIds) {
        for (String roadId : roadIds) {
            if (roadId == null || roadId.isBlank()) {
                continue;
            }
            Road road = network.getRoad(roadId);
            if (road == null || road.getOrderedSegmentIds().isEmpty()) {
                continue;
            }
            String edgeId = road.getOrderedSegmentIds().getFirst();
            if (network.getEdge(edgeId) == null) {
                continue;
            }
            if (!selectedEdgeIds.contains(edgeId)) {
                selectedEdgeIds.add(edgeId);
            }
            lastSelectedEdgeId = edgeId;
        }
        ensureSelectionValid();
    }

    public String getLastSelectedEdgeId() {
        return lastSelectedEdgeId;
    }

    public boolean canUndo() {
        return history.canUndo();
    }

    public boolean canRedo() {
        return history.canRedo();
    }

    /**
     * 推入撤销快照（不 bump {@link #getNetworkRevision()}）。
     * 与 {@link #commitNetworkChange()} 配对：一次用户编辑 = 一次快照 + 一次 revision。
     */
    public void pushUndoSnapshot() {
        history.push(network);
    }

    /**
     * 一次用户编辑完成：bump revision 并按变更类型通知预览层。
     */
    public void commitNetworkChange() {
        commitNetworkChange(pendingChangeKind);
    }

    public void commitNetworkChange(RoadChangeKind kind) {
        notifyNetworkChanged(kind != null ? kind : RoadChangeKind.GENERAL);
        pendingChangeKind = RoadChangeKind.GENERAL;
    }

    /** {@link #pushUndoSnapshot()} 的语义别名。 */
    public void beginNetworkEdit() {
        beginNetworkEdit(RoadChangeKind.GENERAL);
    }

    public void beginNetworkEdit(RoadChangeKind kind) {
        pendingChangeKind = kind != null ? kind : RoadChangeKind.GENERAL;
        pushUndoSnapshot();
    }

    /** {@link #commitNetworkChange()} 的语义别名。 */
    public void finishNetworkEdit() {
        commitNetworkChange(pendingChangeKind);
    }

    /**
     * 单次可撤销编辑：快照 → 修改 → 提交。
     * Manager 内原子操作应优先使用此方法。
     */
    public void mutateNetwork(Runnable mutation) {
        Objects.requireNonNull(mutation, "mutation");
        pushUndoSnapshot();
        mutation.run();
        commitNetworkChange();
    }

    /**
     * 单次可撤销编辑；失败或未变更时不提交 revision，并丢弃刚推入的撤销帧。
     */
    public <T> T mutateNetwork(Supplier<T> mutation, java.util.function.Predicate<T> commitWhen) {
        Objects.requireNonNull(mutation, "mutation");
        Objects.requireNonNull(commitWhen, "commitWhen");
        pushUndoSnapshot();
        T result = mutation.get();
        if (commitWhen.test(result)) {
            commitNetworkChange();
        } else {
            abortPendingNetworkEdit();
        }
        return result;
    }

    /** 丢弃未提交的撤销帧，并将 live 网络恢复为 mutation 前快照。 */
    private void abortPendingNetworkEdit() {
        network = history.discardLatestUndoSnapshot(network);
        network.assertInvariants();
        ensureSelectionValid();
    }

    /**
     * 推入撤销快照并立即提交 revision。
     * <p>ImGui 控件在 {@code isItemActivated} 时推历史、同一次交互内持续改值时使用。
     * 新代码优先 {@link #mutateNetwork(Runnable)} 或 {@code pushUndoSnapshot} + {@code commitNetworkChange}。
     */
    public void pushHistory() {
        pushHistory(RoadChangeKind.GENERAL);
    }

    public void pushHistory(RoadChangeKind kind) {
        pushUndoSnapshot();
        commitNetworkChange(kind);
    }

    public void undo() {
        network = history.undo(network);
        network.assertInvariants();
        ensureSelectionValid();
        lastBatchSelectionKey = "";
        notifyNetworkChanged();
    }

    public void redo() {
        network = history.redo(network);
        network.assertInvariants();
        ensureSelectionValid();
        lastBatchSelectionKey = "";
        notifyNetworkChanged();
    }

    public void resetSelection() {
        selectedEdgeIds.clear();
        lastSelectedEdgeId = "";
        selectedNodeId = "";
        selectedCrossingId = "";
        lastBatchSelectionKey = "";
    }

    /**
     * 仅采样并推荐统一标高，不修改路网。
     */
    public RoadUniformElevationUtils.ElevationRecommendation previewUniformElevation(
            TerrainSampler terrain) {
        if (network.getEdges().isEmpty()) {
            status.warning(PlotI18n.tr("plugin.road.no_edges"));
            return null;
        }
        if (terrain == null) {
            status.error(PlotI18n.tr("plugin.road.generate_world_unavailable"));
            return null;
        }
        RoadUniformElevationUtils.ElevationRecommendation recommendation =
            RoadUniformElevationUtils.recommendForNetwork(network, terrain, config);
        if (recommendation.sampleCount() <= 0) {
            status.warning(PlotI18n.tr("plugin.road.uniform_elevation_no_samples"));
            return null;
        }
        String strategy = recommendation.usedMode()
            ? PlotI18n.tr("plugin.road.uniform_elevation_strategy_mode")
            : PlotI18n.tr("plugin.road.uniform_elevation_strategy_average");
        status.info(PlotI18n.tr(
            "plugin.road.uniform_elevation_preview",
            recommendation.elevation(),
            strategy,
            recommendation.sampleCount(),
            String.format("%.1f", recommendation.average())));
        return recommendation;
    }

    /**
     * 全网统一标高平路：沿途经地形采样，取众数（否则平均）作为路面 Y 并应用。
     */
    public RoadUniformElevationUtils.ElevationRecommendation applyUniformFlatElevation(
            TerrainSampler terrain) {
        RoadUniformElevationUtils.ElevationRecommendation recommendation =
            previewUniformElevation(terrain);
        if (recommendation == null) {
            return null;
        }
        String strategy = recommendation.usedMode()
            ? PlotI18n.tr("plugin.road.uniform_elevation_strategy_mode")
            : PlotI18n.tr("plugin.road.uniform_elevation_strategy_average");
        applyUniformFlatElevationAt(
            recommendation.elevation(),
            strategy,
            recommendation.sampleCount(),
            recommendation.average());
        return recommendation;
    }

    /**
     * 全网统一到用户指定标高，最大坡度强制 0。
     */
    public boolean applyCustomUniformFlatElevation(int elevation) {
        if (network.getEdges().isEmpty()) {
            status.warning(PlotI18n.tr("plugin.road.no_edges"));
            return false;
        }
        int clamped = (int) RoadParameterLimits.clampElevation(elevation);
        applyUniformFlatElevationAt(
            clamped,
            PlotI18n.tr("plugin.road.uniform_elevation_strategy_custom"),
            0,
            clamped);
        return true;
    }

    /**
     * 将全部节点手动标高设为 {@code elevation}，全部道路与默认最大坡度设为 0。
     */
    public void applyUniformFlatElevationAt(
            int elevation,
            String strategyLabel,
            int sampleCount,
            double average) {
        mutateNetwork(() -> {
            for (RoadNode node : network.getNodes().values()) {
                node.setManualElevation((double) elevation);
            }
            for (Road road : network.getRoads().values()) {
                road.setMaxSlope(0f);
            }
        });
        // 仅改路网内道路坡度，不写全局默认配置，避免副作用持久化到 config

        if (sampleCount > 0) {
            status.success(PlotI18n.tr(
                "plugin.road.uniform_elevation_applied",
                elevation,
                strategyLabel != null ? strategyLabel : "",
                sampleCount,
                String.format("%.1f", average)));
        } else {
            status.success(PlotI18n.tr(
                "plugin.road.uniform_elevation_applied_custom",
                elevation));
        }
        LOGGER.info(
            "全网统一标高: Y={} ({}), 样本={}, 平均={}",
            elevation,
            strategyLabel,
            sampleCount,
            average);
    }

    private void notifyNetworkChanged() {
        notifyNetworkChanged(RoadChangeKind.GENERAL);
    }

    private void notifyNetworkChanged(RoadChangeKind kind) {
        networkRevision++;
        if (onNetworkChanged != null) {
            onNetworkChanged.accept(kind);
        }
    }

    public RoadNode getSelectedJunctionNode() {
        RoadNode node = getSelectedNode();
        if (node == null || !node.isJunction()) {
            return null;
        }
        return node;
    }

    public void handleNodeSelect(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return;
        }
        RoadNode node = network.getNode(nodeId);
        if (node == null) {
            return;
        }
        selectedNodeId = nodeId;
        selectedCrossingId = "";
        selectedEdgeIds.clear();
        lastSelectedEdgeId = "";
    }

    public void handleEdgeSelect(String edgeId, boolean multiSelect) {
        if (edgeId == null || edgeId.isBlank()) {
            return;
        }
        if (multiSelect) {
            if (selectedEdgeIds.contains(edgeId)) {
                selectedEdgeIds.remove(edgeId);
                if (edgeId.equals(lastSelectedEdgeId)) {
                    lastSelectedEdgeId = selectedEdgeIds.isEmpty() ? "" : selectedEdgeIds.getFirst();
                }
            } else {
                selectedEdgeIds.add(edgeId);
                lastSelectedEdgeId = edgeId;
            }
        } else {
            RoadEdge edge = network.getEdge(edgeId);
            String roadId = edge != null ? edge.getRoadId() : null;
            if (roadId != null && !roadId.isBlank()) {
                selectRoad(roadId, false);
            } else {
                selectedEdgeIds.clear();
                selectedEdgeIds.add(edgeId);
                lastSelectedEdgeId = edgeId;
            }
            selectedNodeId = "";
            selectedCrossingId = "";
        }
        ensureSelectionValid();
    }

    /**
     * 选中一条逻辑道路的全部几何段（同一 {@code roadId}）。
     */
    public void selectRoad(String roadId, boolean multiSelect) {
        if (roadId == null || roadId.isBlank()) {
            return;
        }
        Road road = network.getRoad(roadId);
        if (road == null) {
            return;
        }
        List<String> segmentIds = new ArrayList<>(road.getOrderedSegmentIds());
        segmentIds.removeIf(id -> network.getEdge(id) == null);
        if (segmentIds.isEmpty()) {
            return;
        }
        if (multiSelect) {
            boolean allSelected = selectedEdgeIds.containsAll(segmentIds);
            if (allSelected) {
                segmentIds.forEach(selectedEdgeIds::remove);
            } else {
                selectedEdgeIds.addAll(segmentIds);
                lastSelectedEdgeId = segmentIds.getFirst();
            }
        } else {
            selectedEdgeIds.clear();
            selectedEdgeIds.addAll(segmentIds);
            lastSelectedEdgeId = segmentIds.getFirst();
        }
        if (!multiSelect) {
            selectedNodeId = "";
            selectedCrossingId = "";
        }
        ensureSelectionValid();
    }

    /**
     * 移除已不存在的边选择，允许空选择（不再自动回填第一条边）。
     * 保证 {@code lastSelectedEdgeId} 始终落在当前选择集内（或为空）。
     */
    public void ensureSelectionValid() {
        selectedEdgeIds.removeIf(id -> network.getEdge(id) == null);
        if (lastSelectedEdgeId.isEmpty()
                || !selectedEdgeIds.contains(lastSelectedEdgeId)
                || network.getEdge(lastSelectedEdgeId) == null) {
            lastSelectedEdgeId = selectedEdgeIds.isEmpty() ? "" : selectedEdgeIds.getFirst();
        }
        if (selectedNodeId != null && !selectedNodeId.isBlank() && network.getNode(selectedNodeId) == null) {
            selectedNodeId = "";
        }
        if (selectedCrossingId != null && !selectedCrossingId.isBlank() && network.getCrossing(selectedCrossingId) == null) {
            selectedCrossingId = "";
        }
    }

    public String getPrimarySelectedEdgeId() {
        ensureSelectionValid();
        if (!lastSelectedEdgeId.isEmpty()
                && selectedEdgeIds.contains(lastSelectedEdgeId)
                && network.getEdge(lastSelectedEdgeId) != null) {
            return lastSelectedEdgeId;
        }
        if (!selectedEdgeIds.isEmpty()) {
            return selectedEdgeIds.getFirst();
        }
        return "";
    }

    public void setPrimarySelectedEdge(String edgeId) {
        if (edgeId == null || edgeId.isBlank() || network.getEdge(edgeId) == null) {
            return;
        }
        lastSelectedEdgeId = edgeId;
    }

    public Road getPrimarySelectedRoad() {
        String primaryId = getPrimarySelectedEdgeId();
        if (primaryId.isBlank()) {
            return null;
        }
        RoadEdge edge = network.getEdge(primaryId);
        return edge != null ? network.getRoadForEdge(edge) : null;
    }

    public List<RoadEdge> filteredEdges(
            String searchText,
            RoadEdgeListHelper.SortMode sortMode,
            RoadEdgeListHelper.CoordFilter coordFilter) {
        return RoadEdgeListHelper.filterAndSort(
            network,
            new ArrayList<>(network.getEdges().values()),
            searchText,
            sortMode,
            coordFilter);
    }

    public void selectAllEdges() {
        selectedEdgeIds.clear();
        selectedEdgeIds.addAll(network.getEdges().keySet());
        selectedNodeId = "";
        selectedCrossingId = "";
        lastSelectedEdgeId = selectedEdgeIds.isEmpty() ? "" : selectedEdgeIds.getFirst();
        ensureSelectionValid();
    }

    public void clearEdgeSelection() {
        selectedEdgeIds.clear();
        lastSelectedEdgeId = "";
        // 允许真正清空选择，不强制回填
    }

    public void deleteEdge(String edgeId) {
        if (edgeId == null || edgeId.isEmpty()) {
            return;
        }
        mutateNetwork(() -> {
            network.removeEdge(edgeId);
            selectedEdgeIds.remove(edgeId);
            if (edgeId.equals(lastSelectedEdgeId)) {
                lastSelectedEdgeId = getPrimarySelectedEdgeId();
            }
            ensureSelectionValid();
        });
    }

    public void deleteRoad(String roadId) {
        if (roadId == null || roadId.isBlank()) {
            return;
        }
        mutateNetwork(() -> {
            Road road = network.getRoad(roadId);
            List<String> edgeIds = road != null ? new ArrayList<>(road.getOrderedSegmentIds()) : List.of();
            network.removeRoad(roadId);
            selectedEdgeIds.removeIf(edgeIds::contains);
            if (edgeIds.contains(lastSelectedEdgeId)) {
                lastSelectedEdgeId = "";
            }
            ensureSelectionValid();
        });
    }

    /**
     * 删除单个几何分段（拓扑移除）；若所属 Road 无剩余分段则一并移除 Road。
     */
    public void deleteSegment(String edgeId) {
        deleteEdge(edgeId);
    }

    /**
     * 在指定分段前断开逻辑道路，后续分段划入新 Road。
     *
     * @return 新 Road id；失败时 null
     */
    public String splitRoadBeforeSegment(String roadId, String segmentEdgeId) {
        if (roadId == null || roadId.isBlank() || segmentEdgeId == null || segmentEdgeId.isBlank()) {
            return null;
        }
        Road road = network.getRoad(roadId);
        if (road == null) {
            return null;
        }
        List<String> segmentIds = RoadSegmentOrdering.orderedSegmentIds(network, road);
        int index = segmentIds.indexOf(segmentEdgeId);
        if (index <= 0) {
            return null;
        }
        pushUndoSnapshot();
        String newRoadId = network.splitRoadBeforeSegment(roadId, segmentEdgeId);
        if (newRoadId == null) {
            abortPendingNetworkEdit();
            return null;
        }
        commitNetworkChange();
        return newRoadId;
    }

    public CenterlineEditResult insertPiAtLocalDistance(String edgeId, double localDistance) {
        if (edgeId == null || edgeId.isBlank()) {
            return CenterlineEditResult.failure(CenterlineEditStatus.EDGE_NOT_FOUND);
        }
        return mutateCenterlineGeometry(
            () -> RoadCenterlineEditor.insertPiAtLocalDistance(network, edgeId, localDistance));
    }

    public CenterlineEditResult insertPiAtRoadStation(Road road, String edgeId, double roadStation) {
        return mutateCenterlineGeometry(
            () -> RoadCenterlineEditor.insertPiAtRoadStation(network, road, edgeId, roadStation));
    }

    public CenterlineEditResult splitEdgeAtLocalDistance(String edgeId, double localDistance) {
        if (edgeId == null || edgeId.isBlank()) {
            return CenterlineEditResult.failure(CenterlineEditStatus.EDGE_NOT_FOUND);
        }
        return mutateCenterlineGeometry(() -> {
            CenterlineEditResult result =
                RoadCenterlineEditor.splitAtLocalDistance(network, edgeId, localDistance);
            if (result.isSuccess() && result.secondEdgeId() != null) {
                setPrimarySelectedEdge(result.secondEdgeId());
            }
            return result;
        });
    }

    public CenterlineEditResult filletCenterlineVertex(String edgeId, int vertexIndex, double radius) {
        return mutateCenterlineGeometry(
            () -> RoadCenterlineEditor.filletVertex(network, edgeId, vertexIndex, radius));
    }

    public CenterlineEditResult mergeSegmentsAtNode(String nodeId) {
        return mutateNetwork(
            () -> {
                CenterlineEditResult result = RoadCenterlineEditor.mergeThroughNode(network, nodeId);
                if (result.isSuccess() && result.mergedEdgeId() != null) {
                    reconcileCrossingsInPlace();
                    setPrimarySelectedEdge(result.mergedEdgeId());
                }
                return result;
            },
            result -> result.isSuccess() && result.mergedEdgeId() != null);
    }

    public CenterlineEditResult reverseEdge(String edgeId) {
        return mutateCenterlineGeometry(
            () -> RoadCenterlineEditor.reverseEdge(network, edgeId));
    }

    public CenterlineEditResult reverseRoad(Road road) {
        if (road == null) {
            return CenterlineEditResult.failure(CenterlineEditStatus.ROAD_NOT_FOUND);
        }
        return mutateCenterlineGeometry(
            () -> RoadCenterlineEditor.reverseRoad(network, road));
    }

    private CenterlineEditResult mutateCenterlineGeometry(
            java.util.function.Supplier<CenterlineEditResult> mutation) {
        return mutateNetwork(() -> {
            CenterlineEditResult result = mutation.get();
            if (result.isSuccess()) {
                reconcileCrossingsInPlace();
            }
            return result;
        }, CenterlineEditResult::isSuccess);
    }

    public CenterlineEditResult materializeHorizontalAlignment(Road road) {
        if (road == null) {
            return CenterlineEditResult.failure(CenterlineEditStatus.ROAD_NOT_FOUND);
        }
        return mutateNetwork(
            () -> {
                CenterlineEditResult result =
                    HorizontalAlignmentCenterlineMaterializer.materialize(network, road);
                if (result.isSuccess()) {
                    reconcileCrossingsInPlace();
                }
                return result;
            },
            CenterlineEditResult::isSuccess);
    }

    /** 内联刷新 Crossing 注册表（不推入额外 Undo 帧）。 */
    void reconcileCrossingsInPlace() {
        com.plot.plugin.road.crossing.RoadCrossingReconciler.reconcileCrossings(network);
    }

    /** 校验一键修复：同步可维护道路的分段存储顺序。 */
    public boolean syncRoadSegmentOrder(Road road) {
        if (road == null) {
            return false;
        }
        return mutateNetwork(
            () -> RoadTopologyInvariantValidator.syncStorageOrderIfMaintainable(network, road),
            synced -> synced);
    }

    /** 校验一键修复：平缓单条道路纵坡。 */
    public boolean smoothRoadGrade(Road road) {
        if (road == null) {
            return false;
        }
        return mutateNetwork(
            () -> VerticalAlignmentGradeSmoother.smoothRoad(network, road, config),
            changed -> changed);
    }

    /** 校验一键修复：平缓全网超限纵坡。 */
    public int smoothAllExceedingGrades() {
        Integer count = mutateNetwork(
            () -> VerticalAlignmentGradeSmoother.smoothAllExceeding(network, config),
            changed -> changed > 0);
        return count != null ? count : 0;
    }

    /** 校验一键修复：将过短非平路改为平路纵断面。 */
    public int makeShortRoadsFlat() {
        Integer changed = mutateNetwork(
            () -> {
                int count = 0;
                for (Road candidate : network.getRoads().values()) {
                    var alignment = candidate.getVerticalAlignment();
                    if ((candidate.getVerticalMode() == RoadVerticalMode.FLAT
                            || candidate.getVerticalMode() == RoadVerticalMode.MANUAL_PROFILE)
                            && RoadStationing.isStationable(network, candidate)
                            && VerticalAlignmentGeometry.isEvaluable(alignment)
                            && !VerticalProfileDesignRules.slopeAllowed(
                                RoadStationing.canonicalLength(network, candidate))
                            && !VerticalProfileDesignRules.isFlat(alignment)) {
                        double length = RoadStationing.canonicalLength(network, candidate);
                        double elevation = alignment.getPvis().getFirst().getElevation();
                        candidate.setVerticalAlignment(
                            VerticalProfileDesignRules.flatAlignment(length, elevation));
                        candidate.setVerticalMode(RoadVerticalMode.FLAT);
                        count++;
                    }
                }
                return count;
            },
            c -> c > 0);
        return changed != null ? changed : 0;
    }

    /** 校验一键修复：平路在路口采用路口标高。 */
    public int makeRoadsFlatAtJunctionElevation() {
        if (FlatRoadJunctionConflictResolver.find(network).isEmpty()) {
            return 0;
        }
        Integer changed = mutateNetwork(
            () -> FlatRoadJunctionConflictResolver.makeRoadsFlatAtJunctionElevation(network),
            c -> c > 0);
        return changed != null ? changed : 0;
    }

    /** 校验一键修复：允许冲突平路改为有坡度。 */
    public int allowConflictingRoadsToSlope() {
        if (FlatRoadJunctionConflictResolver.find(network).isEmpty()) {
            return 0;
        }
        Integer changed = mutateNetwork(
            () -> FlatRoadJunctionConflictResolver.allowConflictingRoadsToSlope(network),
            c -> c > 0);
        return changed != null ? changed : 0;
    }

    public void adoptSelectedPaths(List<Shape> selectedPaths) {
        if (selectedPaths.isEmpty()) {
            return;
        }

        adoptIntersectionRepairPending = false;
        int adoptedCount = 0;
        int failedCount = 0;
        int totalJunctions = 0;
        boolean intersectionIncomplete = false;
        boolean historyPushed = false;
        selectedEdgeIds.clear();

        List<List<Vec2d>> adoptionGroups =
            RoadGeometryUtils.groupConnectedPathsForAdoption(selectedPaths);

        int duplicatePathCount = 0;
        for (List<Vec2d> pathPoints : adoptionGroups) {
            if (RoadAdoptDuplicateDetector.overlapsExistingPath(network, pathPoints)) {
                duplicatePathCount++;
            }
        }

        for (List<Vec2d> pathPoints : adoptionGroups) {
            String networkBeforeAdopt = network.toJson();
            try {
                if (!historyPushed) {
                    pushUndoSnapshot();
                    historyPushed = true;
                }
                Shape path = new PolylineShape(pathPoints, false);
                RoadNetworkBuilder.AdoptResult result =
                    networkBuilder.adoptShape(network, path, config);
                adoptedCount++;
                totalJunctions += result.junctionCount();
                if (result.intersectionResult() == IntersectionResult.INCOMPLETE) {
                    intersectionIncomplete = true;
                }
                for (RoadEdge edge : result.edges()) {
                    selectedEdgeIds.add(edge.getId());
                }
                if (!result.edges().isEmpty()) {
                    lastSelectedEdgeId = result.edges().getFirst().getId();
                }
            } catch (IllegalArgumentException | IllegalStateException e) {
                network = RoadNetwork.parseSnapshot(networkBeforeAdopt);
                failedCount++;
                LOGGER.warn("认领单条道路失败: {}", e.getMessage());
            } catch (OutOfMemoryError | StackOverflowError e) {
                network = RoadNetwork.parseSnapshot(networkBeforeAdopt);
                LOGGER.error("严重错误，停止认领: {}", e.getMessage(), e);
                throw e;
            } catch (Exception e) {
                network = RoadNetwork.parseSnapshot(networkBeforeAdopt);
                failedCount++;
                LOGGER.error("认领单条道路时发生未知错误: {}", e.getMessage(), e);
                // 如果失败率过高，停止处理
                if (failedCount > adoptedCount && failedCount > 3) {
                    LOGGER.error("失败率过高（失败{}次，成功{}次），停止认领", failedCount, adoptedCount);
                    break;
                }
            }
        }

        if (adoptedCount == 0) {
            if (historyPushed) {
                undo();
            }
            status.error(PlotI18n.tr("plugin.road.adopt_failed"));
            return;
        }

        RoadTopologyRoadSplitter.RepairResult topologyRepair =
            RoadTopologyRoadSplitter.repairAfterAdopt(network);
        com.plot.plugin.road.crossing.RoadCrossingReconciler.reconcileCrossings(network);
        IntersectionProbeResult crossingProbe =
            com.plot.plugin.road.crossing.RoadCrossingReconciler.probeRegistryCompleteness(network);
        adoptIntersectionRepairPending = intersectionIncomplete || crossingProbe.hasPendingWork();
        commitNetworkChange();

        if (failedCount > 0) {
            status.warning(String.format(
                PlotI18n.tr("plugin.road.adopt_partial_success"),
                adoptedCount,
                failedCount));
        } else if (intersectionIncomplete) {
            status.warning(PlotI18n.tr("plugin.road.adopt_intersection_incomplete"));
        } else if (topologyRepair.newRoadsCreated() > 0) {
            status.success(String.format(
                PlotI18n.tr("plugin.road.adopt_success_topology_repaired"),
                adoptedCount,
                topologyRepair.sourceRoadsRepaired(),
                topologyRepair.newRoadsCreated()));
        } else if (adoptedCount > 1) {
            status.success(String.format(
                PlotI18n.tr("plugin.road.adopt_success_batch"),
                adoptedCount,
                totalJunctions));
        } else if (totalJunctions > 0) {
            status.success(String.format(
                PlotI18n.tr("plugin.road.adopt_success_junction"),
                totalJunctions));
        } else {
            status.success(PlotI18n.tr("plugin.road.adopt_success"));
        }
        if (duplicatePathCount > 0) {
            status.warning(PlotI18n.tr("plugin.road.adopt_duplicate_path_warning", duplicatePathCount));
        }
        LOGGER.info("认领道路完成: 成功 {} 条, 失败 {} 条 ({} 段边)",
            adoptedCount, failedCount, selectedEdgeIds.size());
    }

    /**
     * Re-runs intersection detection and splitting on the live network (e.g. after validation warns).
     */
    public IntersectionResult reconcileIntersections() {
        return reconcileCrossings();
    }

    /**
     * 检测并注册平面交叉关系，不修改拓扑节点/边。
     */
    public IntersectionResult reconcileCrossings() {
        pushUndoSnapshot();
        com.plot.plugin.road.crossing.CrossingReconcileResult result =
            com.plot.plugin.road.crossing.RoadCrossingReconciler.reconcileCrossingsDetailed(network);
        if (!result.changed()) {
            abortPendingNetworkEdit();
            adoptIntersectionRepairPending = false;
            return result.result();
        }
        commitNetworkChange();
        adoptIntersectionRepairPending = false;
        status.success(PlotI18n.tr("plugin.road.reconcile_intersections_success"));
        return result.result();
    }

    public RoadLoopSeamPickSession getLoopSeamPickSession() {
        return loopSeamPickSession;
    }

    public void beginLoopSeamCanvasPick(String roadId) {
        loopSeamPickSession.begin(roadId);
        status.info(PlotI18n.tr("plugin.road.loop_seam_pick_active"));
    }

    public void cancelLoopSeamCanvasPick() {
        loopSeamPickSession.cancel();
        loopSeamRemapConfirmPending = false;
        pendingLoopSeamReplacement = null;
    }

    public boolean isLoopSeamRemapConfirmPending() {
        return loopSeamRemapConfirmPending;
    }

    public void confirmLoopSeamRemap() {
        if (!loopSeamRemapConfirmPending || pendingLoopSeamReplacement == null) {
            return;
        }
        Road road = network.getRoad(loopSeamPickSession.roadId());
        if (road == null) {
            cancelLoopSeamCanvasPick();
            return;
        }
        pushUndoSnapshot();
        double loopLength = RoadStationing.canonicalLength(network, road);
        com.plot.plugin.road.station.RoadStationDataTransforms.rotateLoopStations(
            network, road, pendingLoopSeamShift, loopLength);
        road.setLoopSeam(pendingLoopSeamReplacement);
        reconcileCrossingsInPlace();
        loopSeamRemapConfirmPending = false;
        pendingLoopSeamReplacement = null;
        loopSeamPickSession.cancel();
        commitNetworkChange();
        status.success(PlotI18n.tr("plugin.road.loop_seam_updated"));
    }

    public void declineLoopSeamRemap() {
        loopSeamRemapConfirmPending = false;
        pendingLoopSeamReplacement = null;
        loopSeamPickSession.cancel();
    }

    /**
     * 画布点击设置闭环剖面开口点；若道路已有沿程数据则进入确认流程。
     */
    public boolean tryApplyLoopSeamPick(Vec2d worldPosition) {
        if (!loopSeamPickSession.isActive() || worldPosition == null) {
            return false;
        }
        Road road = network.getRoad(loopSeamPickSession.roadId());
        if (road == null || road.getTopologyMode() != com.plot.plugin.road.model.RoadTopologyMode.LOOP) {
            cancelLoopSeamCanvasPick();
            return false;
        }
        com.plot.plugin.road.station.RoadLoopSeamService.SeamProjection projection =
            projectLoopSeam(network, road, worldPosition);
        if (projection == null) {
            status.warning(PlotI18n.tr("plugin.road.loop_seam_pick_failed"));
            return true;
        }
        com.plot.plugin.road.model.RoadLoopSeam replacement = com.plot.plugin.road.model.RoadLoopSeam.onSegment(
            projection.position(), projection.segmentId(), projection.localFraction());
        boolean hasStationData = road.getVerticalAlignment() != null && road.getVerticalAlignment().pviCount() > 0
            || road.getVariableCrossSections() != null
            || road.getStationFacilities() != null && !road.getStationFacilities().isEmpty();
        if (!hasStationData) {
            pushUndoSnapshot();
            road.setLoopSeam(replacement);
            loopSeamPickSession.cancel();
            commitNetworkChange();
            status.success(PlotI18n.tr("plugin.road.loop_seam_updated"));
            return true;
        }
        double loopLength = RoadStationing.canonicalLength(network, road);
        double oldOffset = road.getLoopSeam() != null
            ? seamChainOffset(network, road, road.getLoopSeam())
            : 0.0;
        pendingLoopSeamShift = oldOffset - projection.chainStation();
        if (pendingLoopSeamShift < -1e-6) {
            pendingLoopSeamShift += loopLength;
        }
        pendingLoopSeamReplacement = replacement;
        loopSeamRemapConfirmPending = true;
        return true;
    }

    /**
     * 显式连接两条道路端点（唯一允许的拓扑端点合并入口）。
     */
    public boolean connectRoadEndpoints(
            String edgeAId,
            boolean useStartA,
            String edgeBId,
            boolean useStartB) {
        Boolean connected = mutateNetwork(
            () -> com.plot.plugin.road.graph.RoadExplicitConnector.connectEndpoints(
                network, edgeAId, useStartA, edgeBId, useStartB),
            result -> result);
        if (Boolean.TRUE.equals(connected)) {
            status.success(PlotI18n.tr("plugin.road.connect_roads_success"));
        } else {
            status.warning(PlotI18n.tr("plugin.road.connect_roads_failed"));
        }
        return Boolean.TRUE.equals(connected);
    }

    private static double seamChainOffset(
            RoadNetwork network,
            Road road,
            com.plot.plugin.road.model.RoadLoopSeam seam) {
        var projection = projectLoopSeam(network, road, seam.position());
        return projection != null ? projection.chainStation() : 0.0;
    }

    private static com.plot.plugin.road.station.RoadLoopSeamService.SeamProjection projectLoopSeam(
            RoadNetwork network,
            Road road,
            Vec2d worldPosition) {
        return com.plot.plugin.road.station.RoadLoopSeamService.projectForPick(network, road, worldPosition);
    }

    /**
     * 批量修复道路拓扑：断开分量拆分、内部分叉拆分、闭合环提升、分段顺序同步。
     * 与认领末尾的 {@link RoadTopologyRoadSplitter#repairAfterAdopt} 相同。
     */
    public RoadTopologyRoadSplitter.RepairResult repairTopology(Road road) {
        pushUndoSnapshot();
        RoadTopologyRoadSplitter.RepairResult result = road != null
            ? RoadTopologyRoadSplitter.repairRoad(network, road)
            : RoadTopologyRoadSplitter.repairAfterAdopt(network);
        commitNetworkChange();
        return result;
    }

    /**
     * 单条道路一键自动修复（单次撤销步）。
     */
    public com.plot.plugin.road.repair.RoadAutoRepair.Result fixRoad(Road road) {
        if (road == null) {
            return new com.plot.plugin.road.repair.RoadAutoRepair.Result("", List.of(), List.of(), 0);
        }
        pushUndoSnapshot();
        com.plot.plugin.road.repair.RoadAutoRepair.Result result = com.plot.plugin.road.repair.RoadAutoRepair.fix(
            network,
            road,
            config,
            networkBuilder,
            () -> adoptIntersectionRepairPending = false);
        commitNetworkChange();
        return result;
    }

    /**
     * 加载批量编辑的默认值（从当前选中的主要边）
     *
     * 重命名说明：原名 syncBatchEditDefaults 暗示"同步"操作，
     * 实际是加载和合并默认值，因此改为更清晰的名称。
     */
    public BatchEditDefaults loadBatchEditDefaults() {
        String primaryId = getPrimarySelectedEdgeId();
        String selectionKey = batchSelectionKey(primaryId);
        if (selectionKey.equals(lastBatchSelectionKey)) {
            return currentBatchEditDefaults();
        }
        lastBatchSelectionKey = selectionKey;
        RoadEdge primary = network.getEdge(getPrimarySelectedEdgeId());
        if (primary == null) {
            return currentBatchEditDefaults();
        }
        Road road = network.getRoadForEdge(primary);
        if (road == null) {
            return currentBatchEditDefaults();
        }
        batchEditWidth = road.getWidth() != null ? road.getWidth() : config.getRoadWidth();
        batchEditLaneCount = road.getCrossSection().getCarriageway().getEffectiveLaneCount();
        batchEditMaterial = road.getMaterial() != null
            ? road.getMaterial()
            : config.getSelectedMaterial();
        batchIncludeShoulder = road.getEffectiveIncludeShoulder(config);
        batchEditShoulderWidth = road.getShoulderWidth() != null
            ? road.getShoulderWidth()
            : config.getShoulderWidth();
        batchIncludeSidewalk = road.getEffectiveIncludeSidewalk(config);
        batchEditSidewalkWidth = road.getSidewalkWidth() != null
            ? road.getSidewalkWidth()
            : config.getSidewalkWidth();
        batchEditSidewalkMaterial = road.getSidewalkMaterial() != null
            ? road.getSidewalkMaterial()
            : config.getSelectedSidewalkMaterial();
        batchIncludeDrainage = road.getEffectiveIncludeDrainage(config);
        batchIncludeBikeLane = road.getEffectiveIncludeBikeLane(config);
        batchEditBikeLaneWidth = road.getBikeLaneWidth() != null
            ? road.getBikeLaneWidth()
            : 1;
        batchIncludeMedian = road.getIncludeMedian() != null && road.getIncludeMedian();
        batchEditMedianWidth = road.getMedianWidth() != null ? road.getMedianWidth() : 1;
        batchStreetlightSpacing = road.getStreetlightSpacing() != null
            ? road.getStreetlightSpacing()
            : 0;
        batchLaneDividers = road.getLaneDividers() != null
            ? road.getLaneDividers()
            : batchEditLaneCount > 1;
        batchCenterLineStyle = road.getCenterLineStyle() != null
            ? road.getCenterLineStyle()
            : CenterLineStyle.NONE;
        batchMarkingMaterial = road.getMarkingMaterial() != null
            ? road.getMarkingMaterial()
            : ResolvedCrossSection.DEFAULT_MARKING_MATERIAL;
        batchEditMaxSlope = road.getMaxSlope() != null ? road.getMaxSlope() : config.getMaxSlope();
        batchIncludeSlopeBatter = road.getEffectiveIncludeSlopeBatter(config);
        batchFillSlopeRatio = road.getFillSlopeRatio() != null
            ? road.getFillSlopeRatio()
            : road.getEffectiveFillSlopeRatio(config);
        batchCutSlopeRatio = road.getCutSlopeRatio() != null
            ? road.getCutSlopeRatio()
            : road.getEffectiveCutSlopeRatio(config);
        batchFillSlopeMaterial = road.getFillSlopeMaterial() != null
            ? road.getFillSlopeMaterial()
            : road.getEffectiveFillSlopeMaterial(config);
        batchCutSlopeMaterial = road.getCutSlopeMaterial() != null
            ? road.getCutSlopeMaterial()
            : road.getEffectiveCutSlopeMaterial(config);
        return currentBatchEditDefaults();
    }

    private String batchSelectionKey(String primaryId) {
        return (primaryId != null ? primaryId : "") + "|" + String.join("|", selectedEdgeIds);
    }

    public BatchEditDefaults currentBatchEditDefaults() {
        return new BatchEditDefaults(
            batchEditWidth,
            batchEditLaneCount,
            batchEditMaterial,
            batchIncludeShoulder,
            batchEditShoulderWidth,
            batchIncludeSidewalk,
            batchEditSidewalkWidth,
            batchEditSidewalkMaterial,
            batchIncludeDrainage,
            batchIncludeBikeLane,
            batchEditBikeLaneWidth,
            batchIncludeMedian,
            batchEditMedianWidth,
            batchStreetlightSpacing,
            batchLaneDividers,
            batchCenterLineStyle,
            batchMarkingMaterial,
            batchIncludeSlopeBatter,
            batchFillSlopeRatio,
            batchCutSlopeRatio,
            batchFillSlopeMaterial,
            batchCutSlopeMaterial,
            batchEditMaxSlope
        );
    }

    public void updateBatchEditDraft(BatchEditDefaults draft) {
        batchEditWidth = draft.width();
        batchEditLaneCount = draft.laneCount();
        batchEditMaterial = draft.material();
        batchIncludeShoulder = draft.includeShoulder();
        batchEditShoulderWidth = draft.shoulderWidth();
        batchIncludeSidewalk = draft.includeSidewalk();
        batchEditSidewalkWidth = draft.sidewalkWidth();
        batchEditSidewalkMaterial = draft.sidewalkMaterial();
        batchIncludeDrainage = draft.includeDrainage();
        batchIncludeBikeLane = draft.includeBikeLane();
        batchEditBikeLaneWidth = draft.bikeLaneWidth();
        batchIncludeMedian = draft.includeMedian();
        batchEditMedianWidth = draft.medianWidth();
        batchStreetlightSpacing = draft.streetlightSpacing();
        batchLaneDividers = draft.laneDividers();
        batchCenterLineStyle = draft.centerLineStyle();
        batchMarkingMaterial = draft.markingMaterial();
        batchIncludeSlopeBatter = draft.includeSlopeBatter();
        batchFillSlopeRatio = draft.fillSlopeRatio();
        batchCutSlopeRatio = draft.cutSlopeRatio();
        batchFillSlopeMaterial = draft.fillSlopeMaterial();
        batchCutSlopeMaterial = draft.cutSlopeMaterial();
        batchEditMaxSlope = draft.maxSlope();
    }

    public void applyBatchEdit(BatchEditDefaults draft) {
        if (selectedEdgeIds.isEmpty()) {
            return;
        }
        mutateNetwork(() -> {
            LinkedHashSet<String> updatedRoadIds = new LinkedHashSet<>();
            for (String edgeId : selectedEdgeIds) {
                RoadEdge edge = network.getEdge(edgeId);
                if (edge == null || edge.getRoadId() == null) {
                    continue;
                }
                if (!updatedRoadIds.add(edge.getRoadId())) {
                    continue;
                }
                Road road = network.getRoadForEdge(edge);
                if (road == null) {
                    continue;
                }
                applyDraftToRoad(road, draft);
            }
            updateBatchEditDraft(draft);
            status.success(PlotI18n.tr("plugin.road.batch_applied", updatedRoadIds.size()));
        });
    }

    public Road getRoadForEdge(RoadEdge edge) {
        return network.getRoadForEdge(edge);
    }

    private static void applyDraftToRoad(Road road, BatchEditDefaults draft) {
        road.setWidth(draft.width());
        road.setLaneCount(draft.laneCount());
        road.setMaterial(draft.material());
        road.setIncludeShoulder(draft.includeShoulder());
        if (draft.includeShoulder()) {
            road.setShoulderWidth(draft.shoulderWidth());
        }
        road.setIncludeSidewalk(draft.includeSidewalk());
        if (draft.includeSidewalk()) {
            road.setSidewalkWidth(draft.sidewalkWidth());
            road.setSidewalkMaterial(draft.sidewalkMaterial());
        }
        road.setIncludeDrainage(draft.includeDrainage());
        road.setIncludeBikeLane(draft.includeBikeLane());
        if (draft.includeBikeLane()) {
            road.setBikeLaneWidth(draft.bikeLaneWidth());
        }
        road.setIncludeMedian(draft.includeMedian());
        if (draft.includeMedian()) {
            road.setMedianWidth(draft.medianWidth());
        }
        road.setStreetlightSpacing(draft.streetlightSpacing());
        road.setLaneDividers(draft.laneDividers());
        road.setCenterLineStyle(draft.centerLineStyle());
        road.setMarkingMaterial(draft.markingMaterial());
        road.setIncludeSlopeBatter(draft.includeSlopeBatter());
        if (draft.includeSlopeBatter()) {
            road.setFillSlopeRatio(draft.fillSlopeRatio());
            road.setCutSlopeRatio(draft.cutSlopeRatio());
            road.setFillSlopeMaterial(draft.fillSlopeMaterial());
            road.setCutSlopeMaterial(draft.cutSlopeMaterial());
        }
        // 批量面板只编辑横断面和附属设施。纵坡属于路线设计，不能因为用户只想改宽度
        // 就把第一条道路的坡度覆盖到所有选中道路。
    }

    public static List<RoadEdge.SlopeOverride> snapshotSlopeOverrides(List<RoadEdge.SlopeOverride> overrides) {
        List<RoadEdge.SlopeOverride> copy = new ArrayList<>(overrides.size());
        for (RoadEdge.SlopeOverride override : overrides) {
            copy.add(new RoadEdge.SlopeOverride(
                override.startDistance, override.endDistance, override.maxSlope));
        }
        return copy;
    }

    public static boolean slopeOverridesEqual(
            List<RoadEdge.SlopeOverride> left,
            List<RoadEdge.SlopeOverride> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            RoadEdge.SlopeOverride a = left.get(i);
            RoadEdge.SlopeOverride b = right.get(i);
            if (a.startDistance != b.startDistance
                || a.endDistance != b.endDistance
                || a.maxSlope != b.maxSlope) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasOverlappingOverride(List<RoadEdge.SlopeOverride> overrides, int index) {
        if (overrides == null || index < 0 || index >= overrides.size()) {
            return false;
        }

        RoadEdge.SlopeOverride current = overrides.get(index);

        // 验证当前区间有效性：startDistance必须小于endDistance
        if (current.startDistance >= current.endDistance) {
            return true; // 无效区间视为重叠（阻止添加）
        }

        for (int i = 0; i < overrides.size(); i++) {
            if (i == index) {
                continue;
            }
            RoadEdge.SlopeOverride other = overrides.get(i);

            // 跳过无效的other区间
            if (other.startDistance >= other.endDistance) {
                continue;
            }

            // 标准区间重叠检测：A.start < B.end && A.end > B.start
            if (current.startDistance < other.endDistance && current.endDistance > other.startDistance) {
                return true;
            }
        }
        return false;
    }

    public static String junctionTypeLabel(RoadNetworkBuilder.JunctionType type) {
        return switch (type) {
            case ENDPOINT -> PlotI18n.tr("plugin.road.legend.endpoint");
            case THROUGH -> PlotI18n.tr("plugin.road.legend.through");
            case T_JUNCTION -> PlotI18n.tr("plugin.road.legend.t_junction");
            case CROSSROAD -> PlotI18n.tr("plugin.road.legend.crossroad");
            case COMPLEX -> PlotI18n.tr("plugin.road.legend.complex");
        };
    }

    public record BatchEditDefaults(
            int width,
            int laneCount,
            MaterialMix material,
            boolean includeShoulder,
            int shoulderWidth,
            boolean includeSidewalk,
            int sidewalkWidth,
            String sidewalkMaterial,
            boolean includeDrainage,
            boolean includeBikeLane,
            int bikeLaneWidth,
            boolean includeMedian,
            int medianWidth,
            int streetlightSpacing,
            boolean laneDividers,
            CenterLineStyle centerLineStyle,
            String markingMaterial,
            boolean includeSlopeBatter,
            float fillSlopeRatio,
            float cutSlopeRatio,
            String fillSlopeMaterial,
            String cutSlopeMaterial,
            float maxSlope) {

        public BatchEditDefaults withWidth(int newWidth) {
            return new BatchEditDefaults(
                newWidth,
                laneCount,
                material,
                includeShoulder,
                shoulderWidth,
                includeSidewalk,
                sidewalkWidth,
                sidewalkMaterial,
                includeDrainage,
                includeBikeLane,
                bikeLaneWidth,
                includeMedian,
                medianWidth,
                streetlightSpacing,
                laneDividers,
                centerLineStyle,
                markingMaterial,
                includeSlopeBatter,
                fillSlopeRatio,
                cutSlopeRatio,
                fillSlopeMaterial,
                cutSlopeMaterial,
                maxSlope);
        }

        public BatchEditDefaults withLaneCount(int newLaneCount) {
            return new BatchEditDefaults(
                width,
                newLaneCount,
                material,
                includeShoulder,
                shoulderWidth,
                includeSidewalk,
                sidewalkWidth,
                sidewalkMaterial,
                includeDrainage,
                includeBikeLane,
                bikeLaneWidth,
                includeMedian,
                medianWidth,
                streetlightSpacing,
                laneDividers,
                centerLineStyle,
                markingMaterial,
                includeSlopeBatter,
                fillSlopeRatio,
                cutSlopeRatio,
                fillSlopeMaterial,
                cutSlopeMaterial,
                maxSlope);
        }

        /** 将批量草稿转为临时横断面，供预览与解析使用。 */
        public RoadCrossSection toCrossSection() {
            RoadCrossSection section = new RoadCrossSection();
            section.getCarriageway().setWidth(width);
            section.getCarriageway().setLaneCount(laneCount);
            section.getCarriageway().setMaterial(material);
            section.getShoulder().setEnabled(includeShoulder);
            section.getShoulder().setWidth(shoulderWidth);
            section.getSidewalk().setEnabled(includeSidewalk);
            section.getSidewalk().setWidth(sidewalkWidth);
            section.getSidewalk().setMaterial(sidewalkMaterial);
            section.getDrain().setEnabled(includeDrainage);
            section.getBikeLane().setEnabled(includeBikeLane);
            section.getBikeLane().setWidth(bikeLaneWidth);
            section.getMedian().setEnabled(includeMedian);
            section.getMedian().setWidth(medianWidth);
            section.getStreetFurniture().setStreetlightSpacing(streetlightSpacing);
            section.getMarkings().setLaneDividers(laneDividers);
            section.getMarkings().setCenterLineStyle(centerLineStyle);
            section.getMarkings().setMaterial(markingMaterial);
            section.getSlopeBatter().setEnabled(includeSlopeBatter);
            section.getSlopeBatter().setFillRatio(fillSlopeRatio);
            section.getSlopeBatter().setCutRatio(cutSlopeRatio);
            section.getSlopeBatter().setFillMaterial(fillSlopeMaterial);
            section.getSlopeBatter().setCutMaterial(cutSlopeMaterial);
            return section;
        }
    }
}
