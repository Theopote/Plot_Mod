package com.plot.plugin.road.manager;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.model.Shape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNetworkHistory;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
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
    private final LinkedHashSet<String> selectedEdgeIds = new LinkedHashSet<>();
    private String selectedNodeId = "";
    private String lastSelectedEdgeId = "";

    private int lastBatchSelectionSize = -1;
    private int batchEditWidth = 5;
    private int batchEditLaneCount = 1;
    private String batchEditMaterial = com.plot.plugin.road.RoadMaterialUtils.DEFAULT_ROAD_BLOCK;
    private String batchEditSidewalkMaterial = com.plot.plugin.road.RoadMaterialUtils.DEFAULT_ROAD_BLOCK;
    private boolean batchIncludeShoulder = false;
    private int batchEditShoulderWidth = 1;
    private boolean batchIncludeSidewalk = true;
    private int batchEditSidewalkWidth = 1;
    private boolean batchIncludeDrainage = false;
    private boolean batchLaneDividers = false;
    private CenterLineStyle batchCenterLineStyle = CenterLineStyle.NONE;
    private String batchMarkingMaterial = ResolvedCrossSection.DEFAULT_MARKING_MATERIAL;
    private float batchEditMaxSlope = 10f;

    public RoadNetworkManager(RoadSystemConfig config, RoadProjectStatus status) {
        this.config = config;
        this.status = status;
    }

    public RoadNetwork getNetwork() {
        return network;
    }

    public void setNetwork(RoadNetwork network) {
        this.network = network != null ? network : new RoadNetwork();
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

    public String getSelectedNodeId() {
        return selectedNodeId;
    }

    public void setSelectedNodeId(String selectedNodeId) {
        this.selectedNodeId = selectedNodeId != null ? selectedNodeId : "";
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

    public void pushHistory() {
        history.push(network);
    }

    public void undo() {
        network = history.undo(network);
    }

    public void redo() {
        network = history.redo(network);
    }

    public void resetSelection() {
        selectedEdgeIds.clear();
        lastSelectedEdgeId = "";
        selectedNodeId = "";
    }

    public RoadNode getSelectedJunctionNode() {
        if (selectedNodeId == null || selectedNodeId.isBlank()) {
            return null;
        }
        RoadNode node = network.getNode(selectedNodeId);
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
            } else {
                selectedEdgeIds.add(edgeId);
                lastSelectedEdgeId = edgeId;
            }
        } else {
            selectedEdgeIds.clear();
            selectedEdgeIds.add(edgeId);
            lastSelectedEdgeId = edgeId;
        }
        selectedNodeId = "";
        ensureSelectionValid();
    }

    public void ensureSelectionValid() {
        selectedEdgeIds.removeIf(id -> network.getEdge(id) == null);
        if (selectedEdgeIds.isEmpty() && !network.getEdges().isEmpty()) {
            String firstId = network.getEdges().values().iterator().next().getId();
            selectedEdgeIds.add(firstId);
            lastSelectedEdgeId = firstId;
        }
        if (!lastSelectedEdgeId.isEmpty() && network.getEdge(lastSelectedEdgeId) == null) {
            lastSelectedEdgeId = getPrimarySelectedEdgeId();
        }
    }

    public String getPrimarySelectedEdgeId() {
        if (!lastSelectedEdgeId.isEmpty() && network.getEdge(lastSelectedEdgeId) != null) {
            return lastSelectedEdgeId;
        }
        if (!selectedEdgeIds.isEmpty()) {
            return selectedEdgeIds.getFirst();
        }
        return "";
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
    }

    public void clearEdgeSelection() {
        selectedEdgeIds.clear();
        lastSelectedEdgeId = "";
        ensureSelectionValid();
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

    public void adoptSelectedPaths(List<Shape> selectedPaths) {
        if (selectedPaths.isEmpty()) {
            return;
        }

        int adoptedCount = 0;
        int failedCount = 0;
        int totalJunctions = 0;
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
            status.set(PlotI18n.tr("plugin.road.adopt_failed"));
            return;
        }

        if (failedCount > 0) {
            status.set(String.format(
                PlotI18n.tr("plugin.road.adopt_partial_success"),
                adoptedCount,
                failedCount));
        } else if (adoptedCount > 1) {
            status.set(String.format(
                PlotI18n.tr("plugin.road.adopt_success_batch"),
                adoptedCount,
                totalJunctions));
        } else if (totalJunctions > 0) {
            status.set(String.format(
                PlotI18n.tr("plugin.road.adopt_success_junction"),
                totalJunctions));
        } else {
            status.set(PlotI18n.tr("plugin.road.adopt_success"));
        }
        LOGGER.info("认领道路完成: 成功 {} 条, 失败 {} 条 ({} 段边)",
            adoptedCount, failedCount, selectedEdgeIds.size());
    }

    /**
     * 加载批量编辑的默认值（从当前选中的主要边）
     *
     * 重命名说明：原名 syncBatchEditDefaults 暗示"同步"操作，
     * 实际是加载和合并默认值，因此改为更清晰的名称。
     */
    public BatchEditDefaults loadBatchEditDefaults() {
        String primaryId = getPrimarySelectedEdgeId();
        if (selectedEdgeIds.size() == lastBatchSelectionSize && primaryId.equals(lastSelectedEdgeId)) {
            return currentBatchEditDefaults();
        }
        lastSelectedEdgeId = primaryId;
        lastBatchSelectionSize = selectedEdgeIds.size();
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
        return currentBatchEditDefaults();
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
            batchLaneDividers,
            batchCenterLineStyle,
            batchMarkingMaterial,
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
        batchLaneDividers = draft.laneDividers();
        batchCenterLineStyle = draft.centerLineStyle();
        batchMarkingMaterial = draft.markingMaterial();
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
        status.set(PlotI18n.tr("plugin.road.batch_applied", selectedEdgeIds.size()));
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
        road.setLaneDividers(draft.laneDividers());
        road.setCenterLineStyle(draft.centerLineStyle());
        road.setMarkingMaterial(draft.markingMaterial());
        road.setMaxSlope(draft.maxSlope());
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
            String material,
            boolean includeShoulder,
            int shoulderWidth,
            boolean includeSidewalk,
            int sidewalkWidth,
            String sidewalkMaterial,
            boolean includeDrainage,
            boolean laneDividers,
            CenterLineStyle centerLineStyle,
            String markingMaterial,
            float maxSlope) {
    }
}
