package com.plot.plugin.road.manager;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.model.Shape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.*;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNetworkHistory;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.terrain.TerrainSampler;
import com.plot.utils.PlotI18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 道路网络数据、选择状态与可撤销变更。
 */
public final class RoadNetworkManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadNetwork");

    private final RoadSystemConfig config;
    private final RoadNetworkHistory history = new RoadNetworkHistory();
    private final RoadNetworkBuilder networkBuilder = new RoadNetworkBuilder();
    private final RoadProjectStatus status;

    private RoadNetwork network = new RoadNetwork();
    private long networkRevision = 0L;
    private final LinkedHashSet<String> selectedEdgeIds = new LinkedHashSet<>();
    private String selectedNodeId = "";
    private String lastSelectedEdgeId = "";
    /** 路网变更时回调（用于使预览失效），由插件装配。 */
    private Runnable onNetworkChanged;

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

    public RoadNetworkManager(RoadSystemConfig config, RoadProjectStatus status) {
        this.config = config;
        this.status = status;
    }

    /**
     * 注册路网变更监听（预览失效等）。重复设置会覆盖。
     */
    public void setOnNetworkChanged(Runnable onNetworkChanged) {
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
        selectedEdgeIds.clear();
        lastSelectedEdgeId = "";
    }

    public void clearNodeSelection() {
        selectedNodeId = "";
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
     * 推入撤销快照，并标记路网即将/已经变更（使预览失效）。
     * UI 与 Manager 在修改前应统一调用此方法。
     */
    public void pushHistory() {
        history.push(network);
        notifyNetworkChanged();
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
        pushHistory();
        for (RoadNode node : network.getNodes().values()) {
            node.setManualElevation((double) elevation);
        }
        for (Road road : network.getRoads().values()) {
            road.setMaxSlope(0f);
        }
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
        networkRevision++;
        if (onNetworkChanged != null) {
            onNetworkChanged.run();
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
        }
        selectedNodeId = "";
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
        List<String> segmentIds = new ArrayList<>(road.getSegmentIds());
        segmentIds.removeIf(id -> network.getEdge(id) == null);
        if (segmentIds.isEmpty()) {
            return;
        }
        if (multiSelect) {
            boolean allSelected = segmentIds.stream().allMatch(selectedEdgeIds::contains);
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
        selectedNodeId = "";
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
        pushHistory();
        network.removeEdge(edgeId);
        selectedEdgeIds.remove(edgeId);
        if (edgeId.equals(lastSelectedEdgeId)) {
            lastSelectedEdgeId = getPrimarySelectedEdgeId();
        }
        // 确保选择状态有效，删除边后其他相关边可能也变为无效
        ensureSelectionValid();
    }

    public void deleteRoad(String roadId) {
        if (roadId == null || roadId.isBlank()) {
            return;
        }
        pushHistory();
        Road road = network.getRoad(roadId);
        List<String> edgeIds = road != null ? new ArrayList<>(road.getSegmentIds()) : List.of();
        network.removeRoad(roadId);
        selectedEdgeIds.removeIf(edgeIds::contains);
        if (edgeIds.contains(lastSelectedEdgeId)) {
            lastSelectedEdgeId = "";
        }
        ensureSelectionValid();
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
        List<String> segmentIds = new ArrayList<>(road.getSegmentIds());
        int index = segmentIds.indexOf(segmentEdgeId);
        if (index <= 0) {
            return null;
        }
        pushHistory();
        String newRoadId = network.splitRoadBeforeSegment(roadId, segmentEdgeId);
        if (newRoadId == null) {
            return null;
        }
        notifyNetworkChanged();
        return newRoadId;
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

        for (List<Vec2d> pathPoints : adoptionGroups) {
            try {
                if (!historyPushed) {
                    pushHistory();
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
                // 可恢复的业务逻辑错误
                failedCount++;
                LOGGER.warn("认领单条道路失败: {}", e.getMessage());
            } catch (OutOfMemoryError | StackOverflowError e) {
                // 严重错误，立即停止
                LOGGER.error("严重错误，停止认领: {}", e.getMessage(), e);
                throw e;
            } catch (Exception e) {
                // 其他未预期的错误
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
            status.error(PlotI18n.tr("plugin.road.adopt_failed"));
            return;
        }

        adoptIntersectionRepairPending = intersectionIncomplete;

        if (intersectionIncomplete) {
            IntersectionResult retry = networkBuilder.detectAndSplitIntersections(network);
            notifyNetworkChanged();
            if (retry == IntersectionResult.COMPLETE) {
                intersectionIncomplete = false;
                adoptIntersectionRepairPending = false;
            }
        }

        if (failedCount > 0) {
            status.warning(String.format(
                PlotI18n.tr("plugin.road.adopt_partial_success"),
                adoptedCount,
                failedCount));
        } else if (intersectionIncomplete) {
            status.warning(PlotI18n.tr("plugin.road.adopt_intersection_incomplete"));
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
        LOGGER.info("认领道路完成: 成功 {} 条, 失败 {} 条 ({} 段边)",
            adoptedCount, failedCount, selectedEdgeIds.size());
    }

    /**
     * Re-runs intersection detection and splitting on the live network (e.g. after validation warns).
     */
    public IntersectionResult reconcileIntersections() {
        pushHistory();
        IntersectionResult result = networkBuilder.detectAndSplitIntersections(network);
        notifyNetworkChanged();
        if (result == IntersectionResult.INCOMPLETE) {
            status.warning(PlotI18n.tr("plugin.road.reconcile_intersection_incomplete"));
        } else {
            adoptIntersectionRepairPending = false;
            status.success(PlotI18n.tr("plugin.road.reconcile_intersections_success"));
        }
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
        pushHistory();
        LinkedHashSet<String> updatedRoadIds = new LinkedHashSet<>();
        for (String edgeId : selectedEdgeIds) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null || edge.getRoadId() == null) {
                continue;
            }
            if (!updatedRoadIds.add(edge.getRoadId())) {
                continue;
            }
            Road road = network.getRoad(edge.getRoadId());
            if (road == null) {
                continue;
            }
            applyDraftToRoad(road, draft);
        }
        updateBatchEditDraft(draft);
        status.success(PlotI18n.tr("plugin.road.batch_applied", updatedRoadIds.size()));
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
