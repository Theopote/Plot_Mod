package com.plot.plugin.road.model;

import com.plot.plugin.road.lamp.RoadLampPlacement;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.JsonParser;
import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixTypeAdapter;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadMaterialMixUtils;
import com.plot.plugin.road.RoadMaterialUtils;
import com.plot.plugin.road.alignment.HorizontalAlignmentPersistence;
import com.plot.plugin.road.alignment.HorizontalAlignmentPersistence.AlignmentData;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.vertical.FlatVerticalIntentPersistence;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.VerticalAlignmentPersistence;
import com.plot.plugin.road.vertical.VerticalAlignmentPersistence.VerticalAlignmentData;
import com.plot.plugin.road.model.VariableCrossSectionPersistence.VariableCrossSectionsData;
import com.plot.plugin.road.model.StationFacilityPersistence.StationFacilitiesData;
import com.plot.plugin.road.station.CenterlineEditOperation;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadLoopSeamService;
import com.plot.plugin.road.station.RoadStationDataTransforms;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.model.section.BikeLane;
import com.plot.plugin.road.model.section.Lane;
import com.plot.plugin.road.model.section.LaneGroup;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.Markings;
import com.plot.plugin.road.model.section.Median;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.Shoulder;
import com.plot.plugin.road.model.section.Sidewalk;
import com.plot.plugin.road.model.section.RoadsideEdgeMode;
import com.plot.plugin.road.model.section.SlopeBatter;
import com.plot.plugin.road.model.section.StreetFurniture;
import com.plot.plugin.road.tunnel.TunnelStylePersistence;

import com.plot.plugin.road.graph.RoadGraphQueries;
import com.plot.core.persistence.AtomicFileWriter;
import com.plot.core.persistence.PersistenceException;
import com.plot.utils.PlotI18n;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 道路网络（插件私有数据模型）：拓扑几何 + 逻辑道路工程对象。
 *
 * <h3>线程模型</h3>
 * <p><strong>单写者（client / UI 线程）</strong>：所有拓扑与属性变更（{@code createEdge}、
 * {@code network.getRoad(id).setWidth(...)}、横断面编辑等）应在持有 {@code RoadNetworkManager}
 * 的客户端线程上串行执行。{@link Road}、{@link RoadEdge}、{@link RoadNode}、
 * {@link RoadCrossSection} 均为可变对象，彼此没有内部锁。
 *
 * <p><strong>{@code ConcurrentHashMap} 仅保证索引表安全</strong>：{@code nodes} / {@code edges} /
 * {@code roads} 的 put/remove/get 在并发下不会破坏 Map 结构，但不构成整个路网的线程安全。
 * 并发读写的元素引用仍指向同一可变实例；例如 UI 正在改 {@code crossSection} 的同时
 * {@link #toJson()} 持久化，理论上可能读到中间状态。
 *
 * <p><strong>后台或跨线程读者</strong>：应使用 {@link #snapshot()}（内存 {@link #deepCopy()}）取得
 * 独立副本后再做生成、校验、探测等耗时工作，不要与 live 实例并发读写。
 * 主路网撤销栈（{@link RoadNetworkHistory}）同样走深拷贝，不经 Pretty-Print JSON。
 *
 * @see RoadNetworkManager
 * @see RoadNetworkHistory
 */
public class RoadNetwork {

    /** 路网 sidecar JSON 格式版本（{@link NetworkData#schemaVersion}）。 */
    public static final int CURRENT_SCHEMA_VERSION = 4;

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(MaterialMix.class, new MaterialMixTypeAdapter())
        .create();

    /** 紧凑 JSON（无 pretty-print），仅作深拷贝回退或诊断；热路径优先 {@link #deepCopy()}。 */
    private static final Gson SNAPSHOT_GSON = new GsonBuilder()
        .registerTypeAdapter(MaterialMix.class, new MaterialMixTypeAdapter())
        .create();

    /** 并发安全的 id 索引；元素 {@link RoadNode} 本身非线程安全。 */
    private final Map<String, RoadNode> nodes = new ConcurrentHashMap<>();
    private final Map<String, RoadNode> nodesView = Collections.unmodifiableMap(nodes);
    /** 并发安全的 id 索引；元素 {@link RoadEdge} 本身非线程安全。 */
    private final Map<String, RoadEdge> edges = new ConcurrentHashMap<>();
    private final Map<String, RoadEdge> edgesView = Collections.unmodifiableMap(edges);
    /** 并发安全的 id 索引；元素 {@link Road} 本身非线程安全。 */
    private final Map<String, Road> roads = new ConcurrentHashMap<>();
    private final Map<String, Road> roadsView = Collections.unmodifiableMap(roads);
    /** 平面交叉注册表（几何关系，非拓扑节点共享）。 */
    private final Map<String, com.plot.plugin.road.crossing.RoadCrossing> crossings = new ConcurrentHashMap<>();
    private final Map<String, com.plot.plugin.road.crossing.RoadCrossing> crossingsView =
        Collections.unmodifiableMap(crossings);
    /** 单路自交叠交注册表。 */
    private final Map<String, com.plot.plugin.road.crossing.RoadStackCrossing> stackCrossings =
        new ConcurrentHashMap<>();
    private final Map<String, com.plot.plugin.road.crossing.RoadStackCrossing> stackCrossingsView =
        Collections.unmodifiableMap(stackCrossings);

    private NetworkElevationMode elevationMode = NetworkElevationMode.PER_ROAD;
    private int uniformElevationY =
        com.plot.plugin.road.vertical.NetworkElevationAuthority.DEFAULT_UNIFORM_ELEVATION_Y;

    /**
     * Set when the last logical split/merge could not reliably reapply crossing designs.
     * Consumed by {@link RoadNetworkManager} to abort the undo snapshot.
     */
    private boolean crossingMigrationFailure;

    public NetworkElevationMode getElevationMode() {
        return elevationMode != null ? elevationMode : NetworkElevationMode.PER_ROAD;
    }

    public void setElevationMode(NetworkElevationMode elevationMode) {
        this.elevationMode = elevationMode != null ? elevationMode : NetworkElevationMode.PER_ROAD;
    }

    public int getUniformElevationY() {
        return (int) com.plot.plugin.road.RoadParameterLimits.clampElevation(uniformElevationY);
    }

    public void setUniformElevationY(int uniformElevationY) {
        this.uniformElevationY =
            (int) com.plot.plugin.road.RoadParameterLimits.clampElevation(uniformElevationY);
    }

    /**
     * 只读索引视图（零拷贝）。底层为 {@link ConcurrentHashMap}，遍历弱一致；
     * 需要隔离快照时请用 {@link #snapshot()} 或显式 {@code Map.copyOf}/{@code new ArrayList}。
     */
    public Map<String, RoadNode> getNodes() {
        return nodesView;
    }

    /** @see #getNodes() */
    public Map<String, RoadEdge> getEdges() {
        return edgesView;
    }

    /** @see #getNodes() */
    public Map<String, Road> getRoads() {
        return roadsView;
    }

    /** @see #getNodes() */
    public Map<String, com.plot.plugin.road.crossing.RoadCrossing> getCrossings() {
        return crossingsView;
    }

    public com.plot.plugin.road.crossing.RoadCrossing getCrossing(String crossingId) {
        return crossings.get(crossingId);
    }

    public void registerCrossing(com.plot.plugin.road.crossing.RoadCrossing crossing) {
        if (crossing == null || crossing.id() == null) {
            throw new IllegalArgumentException("crossing id required");
        }
        crossings.put(crossing.id(), crossing);
    }

    public void removeCrossing(String crossingId) {
        if (crossingId != null) {
            crossings.remove(crossingId);
        }
    }

    public List<com.plot.plugin.road.crossing.RoadCrossing> crossingsForRoad(String roadId) {
        if (roadId == null) {
            return List.of();
        }
        return crossings.values().stream()
            .filter(crossing -> crossing.involvesRoad(roadId))
            .toList();
    }

    /** @see #getNodes() */
    public Map<String, com.plot.plugin.road.crossing.RoadStackCrossing> getStackCrossings() {
        return stackCrossingsView;
    }

    public com.plot.plugin.road.crossing.RoadStackCrossing getStackCrossing(String stackCrossingId) {
        return stackCrossings.get(stackCrossingId);
    }

    public void registerStackCrossing(com.plot.plugin.road.crossing.RoadStackCrossing stackCrossing) {
        if (stackCrossing == null || stackCrossing.id() == null) {
            throw new IllegalArgumentException("stack crossing id required");
        }
        stackCrossings.put(stackCrossing.id(), stackCrossing);
    }

    public void removeStackCrossing(String stackCrossingId) {
        if (stackCrossingId != null) {
            stackCrossings.remove(stackCrossingId);
        }
    }

    public List<com.plot.plugin.road.crossing.RoadStackCrossing> stackCrossingsForRoad(String roadId) {
        if (roadId == null) {
            return List.of();
        }
        return stackCrossings.values().stream()
            .filter(stack -> roadId.equals(stack.roadId()))
            .toList();
    }

    public RoadNode getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    public RoadEdge getEdge(String edgeId) {
        return edges.get(edgeId);
    }

    public Road getRoad(String roadId) {
        if (roadId == null) {
            return null;
        }
        return roads.get(roadId);
    }

    public Road getRoadForEdge(RoadEdge edge) {
        if (edge == null || edge.getRoadId() == null) {
            return null;
        }
        return roads.get(edge.getRoadId());
    }

    public RoadNode createNode(Vec2d position) {
        RoadNode node = new RoadNode(position);
        nodes.put(node.getId(), node);
        return node;
    }

    public Road createRoad() {
        Road road = new Road();
        assignDefaultRoadName(road);
        roads.put(road.getId(), road);
        return road;
    }

    public Road createRoad(String roadId) {
        Road road = new Road(roadId);
        assignDefaultRoadName(road);
        roads.put(road.getId(), road);
        return road;
    }

    /** 与土方区域一致：新建时写入「道路 n」/「Road n」，避免列表出现 UUID 片段。 */
    private void assignDefaultRoadName(Road road) {
        if (road.getName() != null && !road.getName().isBlank()) {
            return;
        }
        String desired = PlotI18n.tr("plugin.road.default_name", roads.size() + 1);
        road.setName(com.plot.plugin.road.RoadEdgeListHelper.allocateUniqueRoadName(
            this, road.getId(), desired));
    }

    /**
     * 创建空逻辑道路，工程参数全部为 {@code null}（动态继承 {@link RoadSystemConfig}）。
     * <p>
     * 认领新道路请使用 {@link #createRoadForAdopt(RoadSystemConfig)}。
     */
    public Road createRoad(RoadSystemConfig defaults) {
        return createRoad();
    }

    /**
     * 创建道路并将当前全局默认快照为显式值（不随后续修改认领默认而变）。
     * 垂直模式固定为 {@link RoadVerticalMode#FIT_TERRAIN}，与 UI「适应地形」语义一致。
     *
     * @see RoadParameterInheritance#snapshotGlobalDefaults(Road, RoadSystemConfig)
     */
    public Road createRoadForAdopt(RoadSystemConfig defaults) {
        Road road = createRoad();
        RoadParameterInheritance.snapshotGlobalDefaults(road, defaults);
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setProfileSource(com.plot.plugin.road.vertical.ProfileSource.AUTO);
        return road;
    }

    /**
     * 创建道路并快照写入全局配置（显式值，不随全局配置变更而变）。
     *
     * @see #createRoadForAdopt(RoadSystemConfig)
     */
    public Road createRoadFromDefaults(RoadSystemConfig defaults) {
        return createRoadForAdopt(defaults);
    }

    public RoadEdge createEdge(String startNodeId, String endNodeId, List<Vec2d> points) {
        return createEdge(startNodeId, endNodeId, points, null);
    }

    public RoadEdge createEdge(String startNodeId, String endNodeId, List<Vec2d> points, String roadId) {
        RoadNode start = nodes.get(startNodeId);
        RoadNode end = nodes.get(endNodeId);
        if (start == null || end == null) {
            throw new IllegalArgumentException("Start or end node does not exist");
        }

        RoadEdge edge = new RoadEdge(startNodeId, endNodeId, points);
        edges.put(edge.getId(), edge);
        start.addEdge(edge.getId());
        end.addEdge(edge.getId());
        if (roadId != null && !roadId.isBlank()) {
            assignEdgeToRoad(edge.getId(), roadId);
        }
        return edge;
    }

    /**
     * 将已有节点重新挂回网络（用于图编辑失败回滚或工作副本写回）。
     */
    public void attachExistingNode(RoadNode node) {
        if (node == null || node.getId() == null || node.getId().isBlank()) {
            return;
        }
        if (nodes.containsKey(node.getId())) {
            return;
        }
        nodes.put(node.getId(), node);
    }

    /**
     * 将已有边重新挂回网络（用于图编辑失败回滚）。
     */
    public void attachExistingEdge(RoadEdge edge) {
        if (edge == null || edge.getId() == null || edge.getId().isBlank()) {
            return;
        }
        RoadNode start = nodes.get(edge.getStartNodeId());
        RoadNode end = nodes.get(edge.getEndNodeId());
        if (start == null || end == null) {
            throw new IllegalArgumentException("Start or end node does not exist for edge " + edge.getId());
        }
        edges.put(edge.getId(), edge);
        start.addEdge(edge.getId());
        end.addEdge(edge.getId());
        String roadId = edge.getRoadId();
        if (roadId != null && !roadId.isBlank()) {
            assignEdgeToRoad(edge.getId(), roadId);
        }
    }

    /**
     * 更新已有边的起终点，并同步两端节点的 {@code connectedEdgeIds}。
     * <p>
     * 工作副本写回等场景可能复用边 ID 但更换端点；不可只改边字段而留下陈旧邻接。
     * 原先仅被本边引用且度降为 0 的旧端点会被移除。
     *
     * @return false 若边或新端点节点不存在
     */
    public boolean updateEdgeEndpoints(String edgeId, String newStartNodeId, String newEndNodeId) {
        RoadEdge edge = edges.get(edgeId);
        if (edge == null
            || newStartNodeId == null || newStartNodeId.isBlank()
            || newEndNodeId == null || newEndNodeId.isBlank()) {
            return false;
        }
        RoadNode newStart = nodes.get(newStartNodeId);
        RoadNode newEnd = nodes.get(newEndNodeId);
        if (newStart == null || newEnd == null) {
            return false;
        }
        String oldStartId = edge.getStartNodeId();
        String oldEndId = edge.getEndNodeId();
        if (newStartNodeId.equals(oldStartId) && newEndNodeId.equals(oldEndId)) {
            return true;
        }

        // 先从旧端点摘下，再挂到新端点；起终点互换时 Set 语义仍正确。
        RoadNode oldStart = nodes.get(oldStartId);
        RoadNode oldEnd = nodes.get(oldEndId);
        if (oldStart != null) {
            oldStart.removeEdge(edgeId);
        }
        if (oldEnd != null) {
            oldEnd.removeEdge(edgeId);
        }
        edge.setStartNodeId(newStartNodeId);
        edge.setEndNodeId(newEndNodeId);
        newStart.addEdge(edgeId);
        newEnd.addEdge(edgeId);

        if (oldStart != null
            && oldStart.getDegree() == 0
            && !oldStartId.equals(newStartNodeId)
            && !oldStartId.equals(newEndNodeId)) {
            nodes.remove(oldStartId);
        }
        if (oldEnd != null
            && oldEnd.getDegree() == 0
            && !oldEndId.equals(newStartNodeId)
            && !oldEndId.equals(newEndNodeId)) {
            nodes.remove(oldEndId);
        }
        return true;
    }

    /**
     * 维护 Road ↔ RoadEdge 双向归属的唯一入口。
     *
     * @return false 若 edge 不存在，或 newRoadId 非空但对应 road 不存在
     */
    public boolean assignEdgeToRoad(String edgeId, String newRoadId) {
        RoadEdge edge = edges.get(edgeId);
        if (edge == null) {
            return false;
        }
        if (newRoadId == null || newRoadId.isBlank()) {
            return unassignEdgeFromRoad(edgeId);
        }

        Road newRoad = roads.get(newRoadId);
        if (newRoad == null) {
            return false;
        }

        String oldRoadId = edge.getRoadId();
        boolean alreadyOnTarget = newRoadId.equals(oldRoadId)
            && newRoad.getOrderedSegmentIds().contains(edgeId);
        // Always scrub other roads' stale membership; only skip sync on the target when
        // the edge is already correctly listed there (load/reconcile must not rewrite order).
        for (Road road : roads.values()) {
            if (road == null || road.getId() == null || road.getId().equals(newRoadId)) {
                continue;
            }
            if (road.getOrderedSegmentIds().contains(edgeId)
                    || (oldRoadId != null && oldRoadId.equals(road.getId()))) {
                road.removeSegment(edgeId);
                RoadTopologyInvariantValidator.syncStorageOrderIfMaintainable(this, road);
            }
        }
        if (alreadyOnTarget) {
            return true;
        }
        edge.setRoadId(newRoadId);
        newRoad.addSegment(edgeId);
        RoadTopologyInvariantValidator.syncStorageOrderIfMaintainable(this, newRoad);
        return true;
    }

    /**
     * 解除 edge 与 road 的双向归属，不改变拓扑。
     * Scrubs the edge id from every road's segment list (not only {@code edge.roadId}).
     */
    public boolean unassignEdgeFromRoad(String edgeId) {
        RoadEdge edge = edges.get(edgeId);
        if (edge == null) {
            return false;
        }
        for (Road road : roads.values()) {
            if (road != null && road.getOrderedSegmentIds().contains(edgeId)) {
                road.removeSegment(edgeId);
                RoadTopologyInvariantValidator.syncStorageOrderIfMaintainable(this, road);
            }
        }
        edge.setRoadId(null);
        return true;
    }

    /**
     * 修正 Road.segmentIds 与 RoadEdge.roadId 的双向归属一致性。
     */
    public void reconcileRoadSegmentLinks() {
        for (Road road : roads.values()) {
            for (String segmentId : List.copyOf(road.getOrderedSegmentIds())) {
                if (!edges.containsKey(segmentId)) {
                    road.removeSegment(segmentId);
                    continue;
                }
                RoadEdge edge = edges.get(segmentId);
                String owner = edge != null ? edge.getRoadId() : null;
                // Orphan / null-road edges must not linger in any road's segment list.
                if (owner == null || owner.isBlank() || !owner.equals(road.getId())) {
                    road.removeSegment(segmentId);
                }
            }
        }
        for (RoadEdge edge : edges.values()) {
            String roadId = edge.getRoadId();
            if (roadId == null || roadId.isBlank()) {
                // Ensure no road still lists this unowned edge.
                for (Road road : roads.values()) {
                    if (road.getOrderedSegmentIds().contains(edge.getId())) {
                        road.removeSegment(edge.getId());
                    }
                }
                continue;
            }
            if (!roads.containsKey(roadId)) {
                edge.setRoadId(null);
                continue;
            }
            assignEdgeToRoad(edge.getId(), roadId);
        }
    }

    public RoadNetworkValidationResult validateInvariants() {
        return RoadNetworkInvariantValidator.validate(this);
    }

    /**
     * 开发模式下可通过 {@code -ea} 在 load / undo / redo 后断言网络不变量。
     */
    public void assertInvariants() {
        RoadNetworkValidationResult result = validateInvariants();
        assert result.isValid() : "Road network invariant violations: " + result.violations();
    }

    public void removeEdge(String edgeId) {
        RoadEdge edge = edges.get(edgeId);
        String roadId = edge != null ? edge.getRoadId() : null;
        unassignEdgeFromRoad(edgeId);
        detachEdge(edgeId);
        if (roadId != null) {
            Road road = roads.get(roadId);
            if (road != null) {
                if (road.getSegmentIds().isEmpty()) {
                    roads.remove(roadId);
                } else {
                    RoadTopologyInvariantValidator.syncStorageOrderIfMaintainable(this, road);
                }
            }
        }
        cleanupIsolatedNodes();
    }

    public void removeRoad(String roadId) {
        if (roadId == null || roadId.isBlank()) {
            return;
        }
        Road road = roads.get(roadId);
        if (road == null) {
            return;
        }
        List<String> edgeIds = edges.values().stream()
            .filter(edge -> roadId.equals(edge.getRoadId()))
            .map(RoadEdge::getId)
            .collect(Collectors.toCollection(ArrayList::new));
        for (String edgeId : edgeIds) {
            unassignEdgeFromRoad(edgeId);
            detachEdge(edgeId);
        }
        roads.remove(roadId);
        cleanupIsolatedNodes();
    }

    /**
     * 在指定分段前断开逻辑道路：该分段及其后续分段移至新 Road，之前分段保留在原 Road。
     * <p>返回 {@code null} 表示拒绝或交叉迁移失败；调用方不得假定拓扑已提交成功。
     * UI 路径须经 {@link RoadNetworkManager} 快照事务。
     *
     * @return 新 Road 的 id；失败时 null
     */
    public String splitRoadBeforeSegment(String roadId, String segmentEdgeId) {
        return splitRoadBeforeSegment(roadId, segmentEdgeId, null);
    }

    public String splitRoadBeforeSegment(
            String roadId,
            String segmentEdgeId,
            com.plot.plugin.config.RoadSystemConfig defaults) {
        crossingMigrationFailure = false;
        Road road = roads.get(roadId);
        if (road == null || segmentEdgeId == null || segmentEdgeId.isBlank()) {
            return null;
        }
        if (road.getTopologyMode() == RoadTopologyMode.LOOP && hasProtectedAlongTrackData(road)) {
            return null;
        }
        List<String> segmentIds = RoadSegmentOrdering.orderedSegmentIds(this, road);
        int index = segmentIds.indexOf(segmentEdgeId);
        if (index < 0 || index == 0) {
            return null;
        }
        // Atomic: mutate working copy only; adopt on success.
        RoadNetwork working = snapshot();
        String newRoadId = working.splitRoadBeforeSegmentMutating(roadId, segmentEdgeId, defaults);
        if (newRoadId == null) {
            crossingMigrationFailure = working.crossingMigrationFailure;
            return null;
        }
        replaceContentsFrom(working);
        return newRoadId;
    }

    private String splitRoadBeforeSegmentMutating(
            String roadId,
            String segmentEdgeId,
            com.plot.plugin.config.RoadSystemConfig defaults) {
        Road road = roads.get(roadId);
        if (road == null) {
            return null;
        }
        List<String> segmentIds = RoadSegmentOrdering.orderedSegmentIds(this, road);
        int index = segmentIds.indexOf(segmentEdgeId);
        if (index < 0 || index == 0) {
            return null;
        }
        List<String> headIds = new ArrayList<>(segmentIds.subList(0, index));
        List<String> tailIds = new ArrayList<>(segmentIds.subList(index, segmentIds.size()));
        double splitStation = RoadStationing.segmentStartStation(this, road, segmentEdgeId);
        // Non-finite / unresolved station must refuse — never skip migration after topology mutate.
        if (!Double.isFinite(splitStation) || splitStation < 0.0) {
            return null;
        }
        List<OrientedRoadSegment> designWalk = RoadStationing.orientedSegments(this, road);
        String priorEntryNode = road.getDesignEntryNodeId();
        String priorEntryEdge = road.getDesignEntryEdgeId();
        boolean sourceEditedValid =
            com.plot.plugin.road.vertical.ResolvedProfileAuthority.hasValidEditedProfile(this, road);
        var vaCapture = RoadStationDataTransforms.captureVaPhysicalAnchors(
            this, road, List.of(splitStation));
        if (vaCapture.failed()) {
            return null;
        }
        var vaAnchors = vaCapture.anchors();
        var alongCapture = RoadStationDataTransforms.captureAlongTrackPhysicalAnchors(
            this, road, List.of(splitStation), defaults);
        if (alongCapture.failed()) {
            return null;
        }
        var crossingCapture = com.plot.plugin.road.crossing.RoadCrossingOwnershipMigrator.captureForRoads(
            this, Set.of(roadId));
        Road newRoad = createRoad();
        newRoad.copyEngineeringFrom(road);

        com.plot.plugin.road.station.StationMigrationResult migration =
            CenterlineEditOperation.SPLIT_ROAD.defaultStationPolicy()
                .applyRoadSplit(this, road, newRoad, splitStation, defaults);
        if (!migration.isSuccess()) {
            roads.remove(newRoad.getId());
            return null;
        }
        for (String segmentId : tailIds) {
            assignEdgeToRoad(segmentId, newRoad.getId());
        }
        road.reorderSegments(headIds);
        newRoad.reorderSegments(tailIds);
        RoadTopologyRoadSplitter.demoteOpenLoopToLinear(this, road);
        RoadTopologyRoadSplitter.demoteOpenLoopToLinear(this, newRoad);

        // Design entry from original oriented walk — never Edge storage start.
        bindDesignEntryFromWalk(road, headIds, designWalk, priorEntryEdge, priorEntryNode);
        bindDesignEntryFromWalk(newRoad, tailIds, designWalk, null, null);

        if (migration.requiresHaRefit()) {
            RoadStationDataTransforms.refitHorizontalAlignmentFromCenterline(this, road);
            RoadStationDataTransforms.refitHorizontalAlignmentFromCenterline(this, newRoad);
            if (road.getHorizontalAlignment() == null || newRoad.getHorizontalAlignment() == null) {
                crossingMigrationFailure = true;
                return null;
            }
        }
        // Extracted or refit HA must stay domain-reliable on both components.
        if (!stationDomainReliable(road) || !stationDomainReliable(newRoad)) {
            crossingMigrationFailure = true;
            return null;
        }
        // Physical elev / VCS / facility equivalence after final HA — isReliable alone is not enough.
        if (!vaAnchors.isEmpty()) {
            if (!RoadStationDataTransforms.verifyVaPhysicalAnchors(this, road, vaAnchors)
                    || !RoadStationDataTransforms.verifyVaPhysicalAnchors(this, newRoad, vaAnchors)) {
                crossingMigrationFailure = true;
                return null;
            }
        }
        if (!alongCapture.isEmpty()) {
            var sectionOnly = RoadStationDataTransforms.AlongTrackAnchorCapture.ok(
                alongCapture.sectionAnchors(), List.of());
            if (!sectionOnly.isEmpty()) {
                if (!RoadStationDataTransforms.verifyAlongTrackPhysicalAnchors(
                            this, road, sectionOnly, defaults)
                        || !RoadStationDataTransforms.verifyAlongTrackPhysicalAnchors(
                            this, newRoad, sectionOnly, defaults)) {
                    crossingMigrationFailure = true;
                    return null;
                }
            }
            if (!alongCapture.facilityIntervals().isEmpty()) {
                double headLen = RoadStationing.canonicalLength(this, road);
                double tailLen = RoadStationing.canonicalLength(this, newRoad);
                var expectedHead = RoadStationDataTransforms.clipAndShiftFacilityIntervals(
                    alongCapture.facilityIntervals(), 0.0, headLen);
                var expectedTail = RoadStationDataTransforms.clipAndShiftFacilityIntervals(
                    alongCapture.facilityIntervals(), splitStation, splitStation + tailLen);
                var actualHead = RoadStationDataTransforms.captureFacilityIntervals(road, headLen);
                var actualTail = RoadStationDataTransforms.captureFacilityIntervals(newRoad, tailLen);
                if (!RoadStationDataTransforms.facilityIntervalsEqual(expectedHead, actualHead)
                        || !RoadStationDataTransforms.facilityIntervalsEqual(expectedTail, actualTail)) {
                    crossingMigrationFailure = true;
                    return null;
                }
            }
        }
        if (!RoadStationDataTransforms.rebindEditedProfileAfterFinalGeometry(
                this, road, sourceEditedValid)
                || !RoadStationDataTransforms.rebindEditedProfileAfterFinalGeometry(
                this, newRoad, sourceEditedValid)) {
            crossingMigrationFailure = true;
            return null;
        }
        var apply = com.plot.plugin.road.crossing.RoadCrossingOwnershipMigrator.reapply(
            this, crossingCapture);
        if (apply.isPending()) {
            crossingMigrationFailure = true;
            return null;
        }
        return newRoad.getId();
    }

    private boolean stationDomainReliable(Road road) {
        if (road == null || road.getHorizontalAlignment() == null) {
            return true;
        }
        var map = RoadStationing.domainMap(this, road);
        return map != null && map.isReliable();
    }

    /**
     * Bind design entry from the first oriented segment whose edge remains in {@code keepEdgeIds}.
     * Prefer prior entry when it still belongs to this road; otherwise use walk entryNodeId.
     */
    private void bindDesignEntryFromWalk(
            Road target,
            List<String> keepEdgeIds,
            List<OrientedRoadSegment> designWalk,
            String priorEntryEdge,
            String priorEntryNode) {
        if (target == null || keepEdgeIds == null || keepEdgeIds.isEmpty()) {
            return;
        }
        Set<String> keep = Set.copyOf(keepEdgeIds);
        if (priorEntryEdge != null && keep.contains(priorEntryEdge)) {
            target.setDesignEntryEdgeId(priorEntryEdge);
            if (priorEntryNode != null) {
                target.setDesignEntryNodeId(priorEntryNode);
                return;
            }
            if (designWalk != null) {
                for (OrientedRoadSegment segment : designWalk) {
                    if (priorEntryEdge.equals(segment.edgeId())) {
                        target.setDesignEntryNodeId(segment.entryNodeId());
                        return;
                    }
                }
            }
        }
        if (designWalk != null) {
            for (OrientedRoadSegment segment : designWalk) {
                if (keep.contains(segment.edgeId())) {
                    target.setDesignEntryEdgeId(segment.edgeId());
                    target.setDesignEntryNodeId(segment.entryNodeId());
                    return;
                }
            }
        }
    }

    /** Replace live maps with a successful working-copy snapshot (ownership transfer). */
    public void replaceContentsFrom(RoadNetwork source) {
        if (source == null || source == this) {
            return;
        }
        nodes.clear();
        edges.clear();
        roads.clear();
        crossings.clear();
        stackCrossings.clear();
        nodes.putAll(source.nodes);
        edges.putAll(source.edges);
        roads.putAll(source.roads);
        crossings.putAll(source.crossings);
        stackCrossings.putAll(source.stackCrossings);
        elevationMode = source.elevationMode;
        uniformElevationY = source.uniformElevationY;
    }

    /** True when the previous logical split/merge left unresolved crossing designs. */
    public boolean consumeCrossingMigrationFailure() {
        boolean failed = crossingMigrationFailure;
        crossingMigrationFailure = false;
        return failed;
    }

    /**
     * 在链端点将 {@code tailRoadId} 并入 {@code headRoadId}：保留 head 的 id，删除 tail。
     * 支持 tail 接在 head 之后，或接在 head 之前。
     * <p>返回 {@code null} 表示拒绝或交叉迁移失败；调用方不得假定拓扑已提交成功。
     * UI 路径须经 {@link RoadNetworkManager} 快照事务。
     *
     * @return 合并后的 road id（即 head）；失败时 null
     */
    public String mergeRoadTailIntoHead(String headRoadId, String tailRoadId) {
        return mergeRoadTailIntoHead(headRoadId, tailRoadId, null);
    }

    public String mergeRoadTailIntoHead(
            String headRoadId,
            String tailRoadId,
            com.plot.plugin.config.RoadSystemConfig defaults) {
        crossingMigrationFailure = false;
        if (headRoadId == null || tailRoadId == null || headRoadId.equals(tailRoadId)) {
            return null;
        }
        Road head = roads.get(headRoadId);
        Road tail = roads.get(tailRoadId);
        if (head == null || tail == null) {
            return null;
        }

        List<String> headSegments = new ArrayList<>(RoadSegmentOrdering.orderedSegmentIds(this, head));
        List<String> tailSegments = new ArrayList<>(RoadSegmentOrdering.orderedSegmentIds(this, tail));
        if (headSegments.isEmpty() || tailSegments.isEmpty()) {
            return null;
        }

        Optional<String> headExit = RoadStationing.chainExitNodeId(this, head);
        Optional<String> tailEntry = RoadStationing.chainEntryNodeId(this, tail);
        Optional<String> headEntry = RoadStationing.chainEntryNodeId(this, head);
        Optional<String> tailExit = RoadStationing.chainExitNodeId(this, tail);

        boolean appendTail = headExit.isPresent() && headExit.equals(tailEntry);
        boolean prependTail = headEntry.isPresent() && headEntry.equals(tailExit);
        // Ring case: both open chains share the same two endpoints; orientation may flip.
        if (!appendTail && !prependTail
                && headEntry.isPresent() && headExit.isPresent()
                && tailEntry.isPresent() && tailExit.isPresent()
                && Set.of(headEntry.get(), headExit.get()).equals(Set.of(tailEntry.get(), tailExit.get()))
                && !headEntry.get().equals(headExit.get())) {
            List<String> oriented = orientSegmentChainFrom(tailSegments, headExit.get());
            if (oriented.isEmpty()) {
                return null;
            }
            boolean reverseReorientation = isReverseSegmentOrder(tailSegments, oriented)
                || chainTraversalReversesAnyEdge(oriented, headExit.get());
            if (reverseReorientation
                    && (hasProtectedAlongTrackData(head) || hasProtectedAlongTrackData(tail))) {
                return null;
            }
            appendTail = true;
        }
        if (!appendTail && !prependTail) {
            return null;
        }
        if ((head.getTopologyMode() == RoadTopologyMode.LOOP || tail.getTopologyMode() == RoadTopologyMode.LOOP)
                && (hasProtectedAlongTrackData(head) || hasProtectedAlongTrackData(tail))) {
            return null;
        }

        RoadNetwork working = snapshot();
        String mergedId = working.mergeRoadTailIntoHeadMutating(headRoadId, tailRoadId, defaults);
        if (mergedId == null) {
            crossingMigrationFailure = working.crossingMigrationFailure;
            return null;
        }
        replaceContentsFrom(working);
        return mergedId;
    }

    private String mergeRoadTailIntoHeadMutating(
            String headRoadId,
            String tailRoadId,
            com.plot.plugin.config.RoadSystemConfig defaults) {
        Road head = roads.get(headRoadId);
        Road tail = roads.get(tailRoadId);
        if (head == null || tail == null) {
            return null;
        }

        List<String> headSegments = new ArrayList<>(RoadSegmentOrdering.orderedSegmentIds(this, head));
        List<String> tailSegments = new ArrayList<>(RoadSegmentOrdering.orderedSegmentIds(this, tail));
        if (headSegments.isEmpty() || tailSegments.isEmpty()) {
            return null;
        }

        Optional<String> headExit = RoadStationing.chainExitNodeId(this, head);
        Optional<String> tailEntry = RoadStationing.chainEntryNodeId(this, tail);
        Optional<String> headEntry = RoadStationing.chainEntryNodeId(this, head);
        Optional<String> tailExit = RoadStationing.chainExitNodeId(this, tail);

        boolean appendTail = headExit.isPresent() && headExit.equals(tailEntry);
        boolean prependTail = headEntry.isPresent() && headEntry.equals(tailExit);
        if (!appendTail && !prependTail
                && headEntry.isPresent() && headExit.isPresent()
                && tailEntry.isPresent() && tailExit.isPresent()
                && Set.of(headEntry.get(), headExit.get()).equals(Set.of(tailEntry.get(), tailExit.get()))
                && !headEntry.get().equals(headExit.get())) {
            List<String> oriented = orientSegmentChainFrom(tailSegments, headExit.get());
            if (oriented.isEmpty()) {
                return null;
            }
            boolean reverseReorientation = isReverseSegmentOrder(tailSegments, oriented)
                || chainTraversalReversesAnyEdge(oriented, headExit.get());
            if (reverseReorientation
                    && (hasProtectedAlongTrackData(head) || hasProtectedAlongTrackData(tail))) {
                return null;
            }
            tailSegments = oriented;
            appendTail = true;
        }
        if (!appendTail && !prependTail) {
            return null;
        }

        // Prepend inherits tail design entry; append keeps head entry.
        String priorEntryNode = head.getDesignEntryNodeId();
        String priorEntryEdge = head.getDesignEntryEdgeId();
        List<OrientedRoadSegment> entryWalk = RoadStationing.orientedSegments(this, head);
        if (prependTail) {
            priorEntryNode = tail.getDesignEntryNodeId();
            priorEntryEdge = tail.getDesignEntryEdgeId();
            entryWalk = RoadStationing.orientedSegments(this, tail);
        }

        double headLengthBefore = RoadStationing.canonicalLength(this, head);
        double tailLengthBefore = RoadStationing.canonicalLength(this, tail);
        boolean headValidEdited = com.plot.plugin.road.vertical.ResolvedProfileAuthority
            .hasValidEditedProfile(this, head);
        boolean tailValidEdited = com.plot.plugin.road.vertical.ResolvedProfileAuthority
            .hasValidEditedProfile(this, tail);
        // Asymmetric validity (valid+stale EDITED or valid+AUTO) is lossy — refuse (Q01).
        if (headValidEdited != tailValidEdited) {
            return null;
        }
        // Only both-valid rebinds; stale+stale keeps archive without activating.
        boolean wasValidEdited = headValidEdited && tailValidEdited;
        var headCapture = RoadStationDataTransforms.captureVaPhysicalAnchors(this, head);
        var tailCapture = RoadStationDataTransforms.captureVaPhysicalAnchors(this, tail);
        if (headCapture.failed() || tailCapture.failed()) {
            return null;
        }
        var headAnchors = headCapture.anchors();
        var tailAnchors = tailCapture.anchors();
        var headAlong = RoadStationDataTransforms.captureAlongTrackPhysicalAnchors(
            this, head, List.of(), defaults);
        var tailAlong = RoadStationDataTransforms.captureAlongTrackPhysicalAnchors(
            this, tail, List.of(), defaults);
        if (headAlong.failed() || tailAlong.failed()) {
            return null;
        }
        // Capture splice origin before topology mutate for LOOP seam (P03).
        Vec2d spliceOrigin = null;
        if (appendTail) {
            spliceOrigin = RoadStationing.chainOrigin(this, head).orElse(null);
        } else {
            spliceOrigin = RoadStationing.chainOrigin(this, tail).orElse(null);
        }
        var crossingCapture = com.plot.plugin.road.crossing.RoadCrossingOwnershipMigrator.captureForRoads(
            this, Set.of(headRoadId, tailRoadId));

        com.plot.plugin.road.station.StationMigrationResult migration;
        List<String> mergedSegments = new ArrayList<>(headSegments.size() + tailSegments.size());
        if (appendTail) {
            migration = CenterlineEditOperation.MERGE_ROAD.defaultStationPolicy()
                .applyRoadMerge(head, head, tail, headLengthBefore, tailLengthBefore, defaults);
            mergedSegments.addAll(headSegments);
            mergedSegments.addAll(tailSegments);
        } else {
            migration = CenterlineEditOperation.MERGE_ROAD.defaultStationPolicy()
                .applyRoadMerge(head, tail, head, tailLengthBefore, headLengthBefore, defaults);
            mergedSegments.addAll(tailSegments);
            mergedSegments.addAll(headSegments);
        }
        if (!migration.isSuccess()) {
            return null;
        }
        for (String segmentId : tailSegments) {
            assignEdgeToRoad(segmentId, headRoadId);
        }
        head.reorderSegments(mergedSegments);
        roads.remove(tailRoadId);
        RoadTopologyInvariantValidator.syncStorageOrderIfMaintainable(this, head);

        bindDesignEntryFromWalk(head, mergedSegments, entryWalk, priorEntryEdge, priorEntryNode);

        // Promote with splice-origin seam — never let min-XY override the join origin (P03).
        boolean becameLoop = spliceOrigin != null
            ? RoadTopologyRoadSplitter.promoteClosedRoadToLoop(
                this, head, List.of(spliceOrigin.copy()))
            : RoadTopologyRoadSplitter.promoteClosedRoadToLoop(this, head);
        if (!becameLoop) {
            RoadTopologyRoadSplitter.demoteOpenLoopToLinear(this, head);
        }

        if (migration.requiresHaRefit()) {
            RoadStationDataTransforms.refitHorizontalAlignmentFromCenterline(this, head);
            if (head.getHorizontalAlignment() == null) {
                crossingMigrationFailure = true;
                return null;
            }
        }
        if (!stationDomainReliable(head)) {
            crossingMigrationFailure = true;
            return null;
        }
        List<RoadStationDataTransforms.PhysicalStationAnchor> mergedAnchors = new ArrayList<>();
        mergedAnchors.addAll(headAnchors);
        mergedAnchors.addAll(tailAnchors);
        if (!mergedAnchors.isEmpty()
                && !RoadStationDataTransforms.verifyVaPhysicalAnchors(this, head, mergedAnchors)) {
            crossingMigrationFailure = true;
            return null;
        }
        List<RoadStationDataTransforms.AlongTrackIdentityAnchor> mergedSections = new ArrayList<>();
        mergedSections.addAll(headAlong.sectionAnchors());
        mergedSections.addAll(tailAlong.sectionAnchors());
        var sectionOnly = RoadStationDataTransforms.AlongTrackAnchorCapture.ok(
            mergedSections, List.of());
        if (!sectionOnly.isEmpty()
                && !RoadStationDataTransforms.verifyAlongTrackPhysicalAnchors(
                    this, head, sectionOnly, defaults)) {
            crossingMigrationFailure = true;
            return null;
        }
        double facilityOffset = appendTail ? headLengthBefore : tailLengthBefore;
        List<RoadStationDataTransforms.FacilityIntervalAnchor> baseSide =
            appendTail ? headAlong.facilityIntervals() : tailAlong.facilityIntervals();
        List<RoadStationDataTransforms.FacilityIntervalAnchor> offsetSide =
            appendTail ? tailAlong.facilityIntervals() : headAlong.facilityIntervals();
        List<RoadStationDataTransforms.FacilityIntervalAnchor> expectedFacilities =
            new ArrayList<>(baseSide);
        for (var interval : offsetSide) {
            if (interval == null) {
                continue;
            }
            expectedFacilities.add(new RoadStationDataTransforms.FacilityIntervalAnchor(
                interval.identity(),
                interval.start() + facilityOffset,
                interval.end() + facilityOffset));
        }
        if (!expectedFacilities.isEmpty()) {
            double mergedLen = RoadStationing.canonicalLength(this, head);
            var actualFacilities = RoadStationDataTransforms.captureFacilityIntervals(head, mergedLen);
            if (!RoadStationDataTransforms.facilityIntervalsEqual(expectedFacilities, actualFacilities)) {
                crossingMigrationFailure = true;
                return null;
            }
        }
        if (!RoadStationDataTransforms.rebindEditedProfileAfterFinalGeometry(
                this, head, wasValidEdited)) {
            crossingMigrationFailure = true;
            return null;
        }
        var apply = com.plot.plugin.road.crossing.RoadCrossingOwnershipMigrator.reapply(
            this, crossingCapture);
        if (apply.isPending()) {
            crossingMigrationFailure = true;
            return null;
        }
        return headRoadId;
    }

    /** VA / VCS / facilities that Phase 2.3 cannot reliably remap under reverse/loop edits. */
    public boolean hasProtectedAlongTrackData(Road road) {
        if (road == null) {
            return false;
        }
        // Crossing/stack designs migrate via CrossingPassBinding; VA/VCS/facilities wait for Phase 2.4.
        if (road.getVerticalAlignment() != null && road.getVerticalAlignment().pviCount() > 0) {
            return true;
        }
        if (road.getVariableCrossSections() != null) {
            return true;
        }
        return road.getStationFacilities() != null && !road.getStationFacilities().isEmpty();
    }

    private static boolean isReverseSegmentOrder(List<String> original, List<String> oriented) {
        if (original == null || oriented == null || original.size() != oriented.size() || original.isEmpty()) {
            return false;
        }
        if (original.equals(oriented)) {
            return false;
        }
        for (int i = 0; i < original.size(); i++) {
            if (!original.get(i).equals(oriented.get(original.size() - 1 - i))) {
                return !original.equals(oriented);
            }
        }
        return true;
    }

    private boolean chainTraversalReversesAnyEdge(List<String> segmentIds, String startNodeId) {
        if (segmentIds == null || segmentIds.isEmpty() || startNodeId == null) {
            return false;
        }
        String current = startNodeId;
        for (String edgeId : segmentIds) {
            RoadEdge edge = edges.get(edgeId);
            if (edge == null) {
                return true;
            }
            if (edge.getStartNodeId().equals(current)) {
                current = edge.getEndNodeId();
            } else if (edge.getEndNodeId().equals(current)) {
                return true;
            } else {
                return true;
            }
        }
        return false;
    }

    /**
     * Walk {@code segmentIds} as an undirected chain starting at {@code startNodeId}.
     * Returns empty when the segments are not a single chain from that node.
     */
    private List<String> orientSegmentChainFrom(List<String> segmentIds, String startNodeId) {
        if (segmentIds == null || segmentIds.isEmpty() || startNodeId == null) {
            return List.of();
        }
        Set<String> remaining = new LinkedHashSet<>(segmentIds);
        List<String> ordered = new ArrayList<>(segmentIds.size());
        String current = startNodeId;
        while (!remaining.isEmpty()) {
            String nextEdgeId = null;
            String nextNode = null;
            for (String edgeId : remaining) {
                RoadEdge edge = edges.get(edgeId);
                if (edge == null) {
                    continue;
                }
                if (current.equals(edge.getStartNodeId())) {
                    nextEdgeId = edgeId;
                    nextNode = edge.getEndNodeId();
                    break;
                }
                if (current.equals(edge.getEndNodeId())) {
                    nextEdgeId = edgeId;
                    nextNode = edge.getStartNodeId();
                    break;
                }
            }
            if (nextEdgeId == null) {
                return List.of();
            }
            ordered.add(nextEdgeId);
            remaining.remove(nextEdgeId);
            current = nextNode;
        }
        return ordered;
    }

    /**
     * 仅断开边连接，不清理孤立节点（供打断求交等需要立即复用端点的场景）
     */
    public void detachEdge(String edgeId) {
        RoadEdge edge = edges.remove(edgeId);
        if (edge == null) {
            return;
        }

        RoadNode start = nodes.get(edge.getStartNodeId());
        RoadNode end = nodes.get(edge.getEndNodeId());
        if (start != null) {
            start.removeEdge(edgeId);
        }
        if (end != null) {
            end.removeEdge(edgeId);
        }
    }

    public void removeNode(String nodeId) {
        RoadNode node = nodes.get(nodeId);
        if (node == null) {
            return;
        }
        if (node.getDegree() != 0) {
            throw new IllegalStateException("Cannot remove node with connected edges: " + nodeId);
        }
        nodes.remove(nodeId);
    }

    private void cleanupIsolatedNodes() {
        List<String> isolated = nodes.values().stream()
            .filter(node -> node.getDegree() == 0)
            .map(RoadNode::getId)
            .collect(Collectors.toCollection(ArrayList::new));
        for (String nodeId : isolated) {
            nodes.remove(nodeId);
        }
    }

    public double getTotalLength() {
        return edges.values().stream().mapToDouble(RoadEdge::getLength).sum();
    }

    /**
     * 拓扑路口数（连接边度 ≥ 3 的节点）。
     * <p>
     * UI「交点」摘要请用 {@link com.plot.plugin.road.crossing.RoadIntersectionInventory#count}：
     * 现代路网交叉多在注册表中，拓扑度可能仍为 2。
     */
    public int getJunctionCount() {
        return (int) nodes.values().stream()
            .filter(node -> node.getDegree() >= 3)
            .count();
    }

    /**
     * 获取节点处连接的道路 ID（按 roadId 去重）。
     */
    public Set<String> getDistinctRoadIdsAtNode(String nodeId) {
        RoadNode node = nodes.get(nodeId);
        if (node == null) {
            return Set.of();
        }
        Set<String> roadIds = new LinkedHashSet<>();
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = edges.get(edgeId);
            if (edge != null && edge.getRoadId() != null && !edge.getRoadId().isBlank()) {
                roadIds.add(edge.getRoadId());
            }
        }
        return roadIds;
    }

    public List<RoadEdge> getEdgesAtNode(String nodeId) {
        RoadNode node = nodes.get(nodeId);
        if (node == null) {
            return List.of();
        }
        List<RoadEdge> connected = new ArrayList<>();
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = edges.get(edgeId);
            if (edge != null) {
                connected.add(edge);
            }
        }
        return connected;
    }

    /**
     * 设置节点的立体交叉标记；校验失败时返回 false 且不修改状态。
     */
    public boolean setCrossingSharedElevation(String crossingId, Double sharedElevation) {
        com.plot.plugin.road.crossing.RoadCrossing crossing = crossings.get(crossingId);
        if (crossing == null) {
            return false;
        }
        crossings.put(crossingId, crossing.withSharedElevation(sharedElevation));
        return true;
    }

    public boolean setStackCrossingSharedElevation(String stackCrossingId, Double sharedElevation) {
        com.plot.plugin.road.crossing.RoadStackCrossing stack = stackCrossings.get(stackCrossingId);
        if (stack == null) {
            return false;
        }
        stackCrossings.put(stackCrossingId, stack.withSharedElevation(sharedElevation));
        return true;
    }

    public boolean setStackCrossingGradeSeparation(
            String stackCrossingId,
            com.plot.plugin.road.crossing.CrossingType type,
            Double lowerStation,
            Double upperStation,
            Double crossingClearance) {
        com.plot.plugin.road.crossing.RoadStackCrossing stack = stackCrossings.get(stackCrossingId);
        if (stack == null) {
            return false;
        }
        if (type == com.plot.plugin.road.crossing.CrossingType.GRADE_SEPARATED) {
            double lower = lowerStation != null ? lowerStation : stack.stationA();
            double upper = upperStation != null ? upperStation : stack.stationB();
            if (!Double.isFinite(lower) || !Double.isFinite(upper)) {
                return false;
            }
            double[] normalized = normalizeStackPassStations(stack, lower, upper);
            if (normalized == null) {
                return false;
            }
            if (crossingClearance != null
                    && (!Double.isFinite(crossingClearance) || crossingClearance < 0.0)) {
                return false;
            }
            stackCrossings.put(stackCrossingId, new com.plot.plugin.road.crossing.RoadStackCrossing(
                stack.id(),
                stack.roadId(),
                stack.position(),
                stack.stationA(),
                stack.stationB(),
                com.plot.plugin.road.crossing.CrossingType.GRADE_SEPARATED,
                normalized[0],
                normalized[1],
                // null：运行时按下穿道路隧道净高解析，不在此烘焙固定默认值
                crossingClearance,
                stack.sharedElevation()));
        } else {
            stackCrossings.put(stackCrossingId, new com.plot.plugin.road.crossing.RoadStackCrossing(
                stack.id(),
                stack.roadId(),
                stack.position(),
                stack.stationA(),
                stack.stationB(),
                com.plot.plugin.road.crossing.CrossingType.AT_GRADE,
                null,
                null,
                null,
                stack.sharedElevation()));
        }
        return true;
    }

    /**
     * One-to-one match of lower/upper to the two geometric pass stations (order may swap).
     * Returns {@code {authoritativeLower, authoritativeUpper}} or null when invalid /
     * both inputs collapse onto the same pass.
     */
    private static double[] normalizeStackPassStations(
            com.plot.plugin.road.crossing.RoadStackCrossing stack,
            double lower,
            double upper) {
        final double eps = 0.5;
        boolean lowerA = Math.abs(lower - stack.stationA()) <= eps;
        boolean lowerB = Math.abs(lower - stack.stationB()) <= eps;
        boolean upperA = Math.abs(upper - stack.stationA()) <= eps;
        boolean upperB = Math.abs(upper - stack.stationB()) <= eps;
        // Require exclusive pairing — reject both-near-same-pass inputs.
        if (lowerA && upperB && !lowerB && !upperA) {
            return new double[] {stack.stationA(), stack.stationB()};
        }
        if (lowerB && upperA && !lowerA && !upperB) {
            return new double[] {stack.stationB(), stack.stationA()};
        }
        return null;
    }

    public boolean setCrossingGradeSeparation(
            String crossingId,
            com.plot.plugin.road.crossing.CrossingType type,
            String elevatedRoadId,
            Double crossingClearance) {
        com.plot.plugin.road.crossing.RoadCrossing crossing = crossings.get(crossingId);
        if (crossing == null) {
            return false;
        }
        if (type == com.plot.plugin.road.crossing.CrossingType.GRADE_SEPARATED
                && elevatedRoadId != null
                && !elevatedRoadId.isBlank()
                && !crossing.involvesRoad(elevatedRoadId)) {
            return false;
        }
        crossings.put(crossingId, new com.plot.plugin.road.crossing.RoadCrossing(
            crossing.id(),
            crossing.roadAId(),
            crossing.stationA(),
            crossing.roadBId(),
            crossing.stationB(),
            crossing.position(),
            type != null ? type : com.plot.plugin.road.crossing.CrossingType.AT_GRADE,
            elevatedRoadId,
            crossingClearance,
            crossing.sharedElevation(),
            crossing.cornerRadius()));
        return true;
    }

    public boolean setCrossingCornerRadius(String crossingId, Double cornerRadius) {
        com.plot.plugin.road.crossing.RoadCrossing crossing = crossings.get(crossingId);
        if (crossing == null) {
            return false;
        }
        crossings.put(crossingId, crossing.withCornerRadius(cornerRadius));
        return true;
    }

    public boolean setNodeGradeSeparation(
            String nodeId,
            boolean gradeSeparated,
            String elevatedRoadId,
            Double crossingClearance) {
        RoadNode node = nodes.get(nodeId);
        if (node == null) {
            return false;
        }
        if (!gradeSeparated) {
            node.clearGradeSeparation();
            return true;
        }
        if (!RoadGraphQueries.isSimpleCrossing(node, this)) {
            return false;
        }
        return applyCrossingGradeSeparation(nodeId, elevatedRoadId, crossingClearance);
    }

    /**
     * Applies grade-separation flags from a registered crossing without requiring a simple
     * four-arm topology. Used after materialize, when the registry is already GRADE_SEPARATED.
     */
    public boolean applyCrossingGradeSeparation(
            String nodeId,
            String elevatedRoadId,
            Double crossingClearance) {
        return applyCrossingGradeSeparation(nodeId, elevatedRoadId, crossingClearance, false);
    }

    /**
     * @param authoritativeClearance when true, registry clearance replaces the node value
     *                               (user edit 8→5 must stick); when false, keep max for
     *                               auto-detect paths that must not shrink an edited underpass.
     */
    public boolean applyCrossingGradeSeparation(
            String nodeId,
            String elevatedRoadId,
            Double crossingClearance,
            boolean authoritativeClearance) {
        RoadNode node = nodes.get(nodeId);
        if (node == null) {
            return false;
        }
        if (elevatedRoadId != null && !elevatedRoadId.isBlank()) {
            Set<String> roadIds = getDistinctRoadIdsAtNode(nodeId);
            if (!roadIds.contains(elevatedRoadId)) {
                // Unknown / not-incident road must not silently enable grade separation.
                return false;
            }
            node.setElevatedRoadId(elevatedRoadId);
        } else {
            node.setElevatedRoadId(null);
        }
        node.setGradeSeparated(true);
        if (crossingClearance != null) {
            if (authoritativeClearance) {
                node.setCrossingClearance(crossingClearance);
            } else {
                Double existing = node.getCrossingClearance();
                node.setCrossingClearance(
                    existing == null ? crossingClearance : Math.max(existing, crossingClearance));
            }
        }
        return true;
    }

    /**
     * 序列化为 JSON。非同步方法：调用方须保证单写者，或先 {@link #snapshot()} 再序列化副本。
     */
    public String toJson() {
        NetworkData data = NetworkData.from(this);
        return GSON.toJson(data);
    }

    public static RoadNetwork fromJson(String json) throws RoadNetworkFormatException {
        if (json == null || json.isBlank()) {
            throw new CorruptedRoadNetworkException(
                PlotI18n.error("error.plot.road.network.corrupted_file"));
        }

        JsonObject root;
        try {
            JsonElement element = JsonParser.parseString(json);
            if (element == null || !element.isJsonObject()) {
                throw new RoadNetworkFormatException(
                    RoadNetworkFormatException.Reason.VALIDATION_FAILED,
                    PlotI18n.error("error.plot.road.network.invalid_json"));
            }
            root = element.getAsJsonObject();
        } catch (RuntimeException e) {
            throw new RoadNetworkFormatException(
                RoadNetworkFormatException.Reason.INVALID_JSON,
                PlotI18n.error("error.plot.road.network.invalid_json"),
                e);
        }

        final NetworkData data;
        try {
            data = GSON.fromJson(root, NetworkData.class);
        } catch (JsonSyntaxException e) {
            throw new RoadNetworkFormatException(
                RoadNetworkFormatException.Reason.INVALID_JSON,
                PlotI18n.error("error.plot.road.network.invalid_json"),
                e);
        }

        if (data == null) {
            throw new RoadNetworkFormatException(
                RoadNetworkFormatException.Reason.VALIDATION_FAILED,
                PlotI18n.error("error.plot.road.network.invalid_json"));
        }
        validateNetworkData(data);
        return migrateNetworkData(data).toNetwork();
    }

    private static NetworkData migrateNetworkData(NetworkData data) throws RoadNetworkFormatException {
        int version = data.schemaVersion;
        if (version <= 0) {
            data.schemaVersion = CURRENT_SCHEMA_VERSION;
        } else if (version != CURRENT_SCHEMA_VERSION) {
            throw new RoadNetworkFormatException(
                RoadNetworkFormatException.Reason.UNSUPPORTED_FORMAT_VERSION,
                PlotI18n.error(
                    "error.plot.road.network.unsupported_format_version",
                    version,
                    CURRENT_SCHEMA_VERSION));
        }
        applyCurrentSchemaDefaults(data);
        return data;
    }

    /** 填充当前 schema 下可省略字段的默认值（非旧版迁移）。 */
    private static void applyCurrentSchemaDefaults(NetworkData data) {
        if (data.elevationMode == null || data.elevationMode.isBlank()) {
            data.elevationMode = NetworkElevationMode.PER_ROAD.name();
        }
        if (data.uniformElevationY == null) {
            data.uniformElevationY =
                com.plot.plugin.road.vertical.NetworkElevationAuthority.DEFAULT_UNIFORM_ELEVATION_Y;
        }
        if (data.roads == null) {
            return;
        }
        for (RoadData road : data.roads) {
            applyRoadLoadDefaults(road);
        }
    }

    private static void applyRoadLoadDefaults(RoadData road) {
        if (road == null) {
            return;
        }
        boolean hasFlatIntent = road.flatVerticalIntent != null
            && Double.isFinite(road.flatVerticalIntent.baseElevation);
        boolean hasAlignment = road.verticalAlignment != null
            && road.verticalAlignment.pvis != null
            && !road.verticalAlignment.pvis.isEmpty();
        boolean modeOmitted = road.verticalMode == null || road.verticalMode.isBlank();
        if (modeOmitted) {
            if (hasFlatIntent) {
                road.verticalMode = RoadVerticalMode.FLAT.name();
            } else {
                road.verticalMode = RoadVerticalMode.FIT_TERRAIN.name();
                if (hasAlignment && (road.profileSource == null || road.profileSource.isBlank())) {
                    road.profileSource = com.plot.plugin.road.vertical.ProfileSource.EDITED.name();
                }
            }
        }
        if (road.profileSource == null || road.profileSource.isBlank()) {
            road.profileSource = RoadVerticalMode.MANUAL_PROFILE.name().equalsIgnoreCase(road.verticalMode)
                ? com.plot.plugin.road.vertical.ProfileSource.EDITED.name()
                : com.plot.plugin.road.vertical.ProfileSource.AUTO.name();
        }
    }

    private static void validateNetworkData(NetworkData data) throws RoadNetworkFormatException {
        if (data.nodes == null || data.edges == null || data.roads == null) {
            throw new RoadNetworkFormatException(
                RoadNetworkFormatException.Reason.VALIDATION_FAILED,
                PlotI18n.error("error.plot.road.network.invalid_json"));
        }
        for (RoadData roadData : data.roads) {
            if (roadData != null && roadData.crossSection == null) {
                throw new RoadNetworkFormatException(
                    RoadNetworkFormatException.Reason.VALIDATION_FAILED,
                    "Road missing crossSection: " + roadData.id);
            }
        }
    }

    /**
     * 保存网络到文件（原子写入 + 备份 + autosave + 保存前 round-trip 校验）。
     */
    public void saveTo(Path file) throws IOException {
        String json = toJson();
        try {
            AtomicFileWriter.write(file, json, AtomicFileWriter.Options.projectDocument(RoadNetwork::fromJson));
        } catch (PersistenceException e) {
            throw new IOException(PlotI18n.error("error.plot.road.network.save_validation_failed"), e);
        } catch (IOException e) {
            if (e.getCause() instanceof RoadNetworkFormatException formatException) {
                throw new IOException(PlotI18n.error("error.plot.road.network.save_validation_failed"), formatException);
            }
            throw e;
        }
    }

    /**
     * 从 sidecar 加载路网。
     * <ul>
     *   <li>文件不存在 → 新工程空路网</li>
     *   <li>文件存在但空白 → {@link CorruptedRoadNetworkException}</li>
     *   <li>无效 JSON → {@link RoadNetworkFormatException}（{@code INVALID_JSON}）</li>
     *   <li>合法空文档 → 空路网</li>
     * </ul>
     */
    public static RoadNetwork loadFrom(Path file) throws IOException {
        if (!Files.exists(file)) {
            return new RoadNetwork();
        }
        String json = Files.readString(file);
        if (json.isBlank()) {
            throw new CorruptedRoadNetworkException(
                PlotI18n.error("error.plot.road.network.corrupted_file_named", file.getFileName()));
        }
        return fromJson(json);
    }

    /**
     * 解析由 {@link #toJson()} 或撤销栈产生的 JSON；损坏内容视为内部错误。
     */
    public static RoadNetwork parseSnapshot(String json) {
        try {
            return fromJson(json);
        } catch (RoadNetworkFormatException e) {
            throw new IllegalStateException("Invalid road network snapshot", e);
        }
    }

    /**
     * 深拷贝快照，供后台生成/校验或与 live 网络隔离的编辑/预览使用。
     * <p>
     * 走内存 {@link #deepCopy()}，保留节点上自交立交等物化瞬态字段；
     * 不再经 Pretty-Print JSON 往返。
     */
    public RoadNetwork snapshot() {
        return deepCopy();
    }

    /**
     * 内存深拷贝：节点/边/道路/交叉注册表及 {@link RoadNode} 栈交瞬态字段。
     */
    public RoadNetwork deepCopy() {
        RoadNetwork copy = new RoadNetwork();
        copy.elevationMode = getElevationMode();
        copy.uniformElevationY = uniformElevationY;
        for (RoadNode node : nodes.values()) {
            copy.nodes.put(node.getId(), node.copy());
        }
        for (RoadEdge edge : edges.values()) {
            copy.edges.put(edge.getId(), edge.copy());
        }
        for (Road road : roads.values()) {
            copy.roads.put(road.getId(), road.copy());
        }
        for (com.plot.plugin.road.crossing.RoadCrossing crossing : crossings.values()) {
            copy.crossings.put(crossing.id(), copyCrossing(crossing));
        }
        for (com.plot.plugin.road.crossing.RoadStackCrossing stack : stackCrossings.values()) {
            copy.stackCrossings.put(stack.id(), copyStackCrossing(stack));
        }
        return copy;
    }

    /** 诊断：紧凑 JSON 往返（不含 pretty-print）。 */
    RoadNetwork snapshotViaCompactJson() {
        return parseSnapshot(SNAPSHOT_GSON.toJson(NetworkData.from(this)));
    }

    private static com.plot.plugin.road.crossing.RoadCrossing copyCrossing(
            com.plot.plugin.road.crossing.RoadCrossing source) {
        return new com.plot.plugin.road.crossing.RoadCrossing(
            source.id(),
            source.roadAId(),
            source.stationA(),
            source.roadBId(),
            source.stationB(),
            source.position(),
            source.type(),
            source.elevatedRoadId(),
            source.crossingClearance(),
            source.sharedElevation(),
            source.cornerRadius());
    }

    private static com.plot.plugin.road.crossing.RoadStackCrossing copyStackCrossing(
            com.plot.plugin.road.crossing.RoadStackCrossing source) {
        return new com.plot.plugin.road.crossing.RoadStackCrossing(
            source.id(),
            source.roadId(),
            source.position(),
            source.stationA(),
            source.stationB(),
            source.type(),
            source.lowerStation(),
            source.upperStation(),
            source.crossingClearance(),
            source.sharedElevation());
    }

    static class Vec2dData {
        double x;
        double y;

        Vec2dData() {
        }

        Vec2dData(Vec2d vec) {
            this.x = vec.x;
            this.y = vec.y;
        }

        Vec2d toVec2d() {
            return new Vec2d(x, y);
        }
    }

    static class NodeData {
        String id;
        Vec2dData position;
        Double manualElevation;
        String manualElevationOrigin;
        Boolean gradeSeparated;
        String elevatedRoadId;
        Double crossingClearance;
        Double cornerRadius;
        Double plazaRadius;
        String stopLines;
        String continuedMarkings;
        String crosswalks;
        String turnArrows;
        String railwayJunctionMode;
        List<String> connectedEdgeIds = new ArrayList<>();
    }

    static class SlopeOverrideData {
        double startDistance;
        double endDistance;
        float maxSlope;
    }

    static class EdgeData {
        String id;
        String startNodeId;
        String endNodeId;
        List<Vec2dData> centerlinePoints = new ArrayList<>();
        String roadId;
        List<SlopeOverrideData> slopeOverrides = new ArrayList<>();
        String sourceRoadId;
    }

    static class LaneData {
        Integer width;
        String material;
    }

    static class LaneGroupData {
        Integer laneCount;
        Integer width;
        Integer defaultLaneWidth;
        MaterialMix material;
        List<LaneData> lanes = new ArrayList<>();
    }

    static class MedianData {
        Boolean enabled;
        Integer width;
        Integer height;
        String material;
    }

    static class MarkingsData {
        Boolean laneDividers;
        Boolean centerLine;
        String centerLineStyle;
        Boolean centerLineSolid;
        String material;
    }

    static class ShoulderData {
        Boolean enabled;
        Integer width;
        String material;
    }

    static class SidewalkData {
        Boolean enabled;
        Integer width;
        String material;
    }

    static class BikeLaneData {
        Boolean enabled;
        Integer width;
        String material;
    }

    static class SlopeBatterData {
        Boolean enabled;
        String mode;
        Float fillRatio;
        Float cutRatio;
        String fillMaterial;
        String cutMaterial;
        String wallMaterial;
        Integer maxRunBlocks;
    }

    static class StreetFurnitureData {
        Integer streetlightSpacing;
        String streetlightBlock;
        RoadLampPlacement roadLampPlacement;
    }

    static class CrossSectionData {
        LaneGroupData carriageway;
        MedianData median;
        MarkingsData markings;
        ShoulderData shoulder;
        BikeLaneData bikeLane;
        SidewalkData sidewalk;
        SlopeBatterData slopeBatter;
        StreetFurnitureData streetFurniture;

        static CrossSectionData from(RoadCrossSection section) {
            CrossSectionData data = new CrossSectionData();
            if (section == null) {
                return data;
            }
            LaneGroup carriageway = section.getCarriageway();
            if (carriageway != null) {
                data.carriageway = new LaneGroupData();
                data.carriageway.laneCount = carriageway.getLaneCount();
                data.carriageway.width = carriageway.getWidth();
                data.carriageway.defaultLaneWidth = carriageway.getDefaultLaneWidth();
                data.carriageway.material = carriageway.getMaterial();
                for (Lane lane : carriageway.getLanes()) {
                    LaneData laneData = new LaneData();
                    laneData.width = lane.getWidth();
                    laneData.material = lane.getMaterial();
                    data.carriageway.lanes.add(laneData);
                }
            }
            Median median = section.getMedian();
            if (median != null) {
                data.median = new MedianData();
                data.median.enabled = median.getEnabled();
                data.median.width = median.getWidth();
                data.median.height = median.getHeight();
                data.median.material = median.getMaterial();
            }
            Markings markings = section.getMarkings();
            if (markings != null) {
                data.markings = new MarkingsData();
                data.markings.laneDividers = markings.getLaneDividers();
                data.markings.centerLine = markings.getCenterLine();
                data.markings.centerLineStyle = markings.getCenterLineStyle() != null
                    ? markings.getCenterLineStyle().name()
                    : null;
                data.markings.centerLineSolid = markings.getCenterLineSolid();
                data.markings.material = markings.getMaterial();
            }
            Shoulder shoulder = section.getShoulder();
            if (shoulder != null) {
                data.shoulder = new ShoulderData();
                data.shoulder.enabled = shoulder.getEnabled();
                data.shoulder.width = shoulder.getWidth();
                data.shoulder.material = shoulder.getMaterial();
            }
            BikeLane bikeLane = section.getBikeLane();
            if (bikeLane != null) {
                data.bikeLane = new BikeLaneData();
                data.bikeLane.enabled = bikeLane.getEnabled();
                data.bikeLane.width = bikeLane.getWidth();
                data.bikeLane.material = bikeLane.getMaterial();
            }
            Sidewalk sidewalk = section.getSidewalk();
            if (sidewalk != null) {
                data.sidewalk = new SidewalkData();
                data.sidewalk.enabled = sidewalk.getEnabled();
                data.sidewalk.width = sidewalk.getWidth();
                data.sidewalk.material = sidewalk.getMaterial();
            }
            SlopeBatter slopeBatter = section.getSlopeBatter();
            if (slopeBatter != null) {
                data.slopeBatter = new SlopeBatterData();
                RoadsideEdgeMode edgeMode = slopeBatter.getMode();
                data.slopeBatter.mode = edgeMode != null ? edgeMode.name() : null;
                data.slopeBatter.enabled = slopeBatter.getEnabled();
                data.slopeBatter.fillRatio = slopeBatter.getFillRatio();
                data.slopeBatter.cutRatio = slopeBatter.getCutRatio();
                data.slopeBatter.fillMaterial = slopeBatter.getFillMaterial();
                data.slopeBatter.cutMaterial = slopeBatter.getCutMaterial();
                data.slopeBatter.wallMaterial = slopeBatter.getWallMaterial();
                data.slopeBatter.maxRunBlocks = slopeBatter.getMaxRunBlocks();
            }
            StreetFurniture furniture = section.getStreetFurniture();
            if (furniture != null) {
                data.streetFurniture = new StreetFurnitureData();
                data.streetFurniture.streetlightSpacing = furniture.getStreetlightSpacing();
                data.streetFurniture.streetlightBlock = furniture.getStreetlightBlock();
                data.streetFurniture.roadLampPlacement = furniture.getRoadLampPlacement();
            }
            return data;
        }

        RoadCrossSection toCrossSection() {
            RoadCrossSection section = new RoadCrossSection();
            if (carriageway != null) {
                LaneGroup laneGroup = new LaneGroup();
                laneGroup.setLaneCount(carriageway.laneCount);
                laneGroup.setWidth(carriageway.width);
                if (carriageway.defaultLaneWidth != null) {
                    laneGroup.setDefaultLaneWidth(carriageway.defaultLaneWidth);
                }
                laneGroup.setMaterial(carriageway.material != null
                    ? RoadMaterialMixUtils.normalizeStored(carriageway.material)
                    : null);
                if (carriageway.lanes != null) {
                    List<Lane> lanes = new ArrayList<>();
                    for (LaneData laneData : carriageway.lanes) {
                        Lane lane = new Lane();
                        lane.setWidth(laneData.width);
                        lane.setMaterial(RoadMaterialUtils.normalizeStoredMaterial(laneData.material));
                        lanes.add(lane);
                    }
                    laneGroup.setLanes(lanes);
                }
                section.setCarriageway(laneGroup);
            }
            if (median != null) {
                Median medianComponent = new Median();
                medianComponent.setEnabled(median.enabled);
                medianComponent.setWidth(median.width);
                if (median.height != null) {
                    medianComponent.setHeight(median.height);
                }
                medianComponent.setMaterial(RoadMaterialUtils.normalizeStoredMaterial(median.material));
                section.setMedian(medianComponent);
            }
            if (markings != null) {
                Markings markingsComponent = new Markings();
                markingsComponent.setLaneDividers(markings.laneDividers);
                markingsComponent.setCenterLine(markings.centerLine);
                if (markings.centerLineStyle != null && !markings.centerLineStyle.isBlank()) {
                    try {
                        markingsComponent.setCenterLineStyle(CenterLineStyle.valueOf(markings.centerLineStyle));
                    } catch (IllegalArgumentException ignored) {
                        markingsComponent.setCenterLineStyle(CenterLineStyle.NONE);
                    }
                }
                markingsComponent.setCenterLineSolid(markings.centerLineSolid);
                markingsComponent.setMaterial(RoadMaterialUtils.normalizeStoredMaterial(markings.material));
                section.setMarkings(markingsComponent);
            }
            if (shoulder != null) {
                Shoulder shoulderComponent = new Shoulder();
                shoulderComponent.setEnabled(shoulder.enabled);
                shoulderComponent.setWidth(shoulder.width);
                shoulderComponent.setMaterial(RoadMaterialUtils.normalizeStoredMaterial(shoulder.material));
                section.setShoulder(shoulderComponent);
            }
            if (bikeLane != null) {
                BikeLane bikeLaneComponent = new BikeLane();
                bikeLaneComponent.setEnabled(bikeLane.enabled);
                bikeLaneComponent.setWidth(bikeLane.width);
                bikeLaneComponent.setMaterial(RoadMaterialUtils.normalizeStoredMaterial(bikeLane.material));
                section.setBikeLane(bikeLaneComponent);
            }
            if (sidewalk != null) {
                Sidewalk sidewalkComponent = new Sidewalk();
                sidewalkComponent.setEnabled(sidewalk.enabled);
                sidewalkComponent.setWidth(sidewalk.width);
                sidewalkComponent.setMaterial(RoadMaterialUtils.normalizeStoredMaterial(sidewalk.material));
                section.setSidewalk(sidewalkComponent);
            }
            if (slopeBatter != null) {
                SlopeBatter slopeComponent = new SlopeBatter();
                RoadsideEdgeMode edgeMode = RoadsideEdgeMode.fromId(slopeBatter.mode);
                if (edgeMode != null) {
                    slopeComponent.setMode(edgeMode);
                } else {
                    slopeComponent.setEnabled(slopeBatter.enabled);
                }
                slopeComponent.setFillRatio(slopeBatter.fillRatio);
                slopeComponent.setCutRatio(slopeBatter.cutRatio);
                slopeComponent.setFillMaterial(RoadMaterialUtils.normalizeStoredMaterial(slopeBatter.fillMaterial));
                slopeComponent.setCutMaterial(RoadMaterialUtils.normalizeStoredMaterial(slopeBatter.cutMaterial));
                slopeComponent.setWallMaterial(RoadMaterialUtils.normalizeStoredMaterial(slopeBatter.wallMaterial));
                slopeComponent.setMaxRunBlocks(slopeBatter.maxRunBlocks);
                section.setSlopeBatter(slopeComponent);
            }
            if (streetFurniture != null) {
                StreetFurniture furniture = new StreetFurniture();
                furniture.setStreetlightSpacing(streetFurniture.streetlightSpacing);
                furniture.setStreetlightBlock(streetFurniture.streetlightBlock);
                furniture.setRoadLampPlacement(streetFurniture.roadLampPlacement);
                section.setStreetFurniture(furniture);
            }
            return section;
        }
    }

    static class LoopSeamData {
        Vec2dData position;
        String segmentHintId;
        Double localFraction;
    }

    static class RoadData {
        String id;
        String name;
        String styleId;
        String themeId;
        CrossSectionData crossSection;
        Float maxSlope;
        String topologyMode;
        LoopSeamData loopSeam;
        String designEntryNodeId;
        String designEntryEdgeId;
        AlignmentData horizontalAlignment;
        VerticalAlignmentData verticalAlignment;
        String verticalMode;
        String profileSource;
        Integer editedGeometrySignature;
        String terrainStyle;
        Integer bridgeThreshold;
        TunnelStylePersistence tunnelStyle;
        Boolean generateBridgePillars;
        Double bridgePierSpacingBlocks;
        String bridgePierMaterial;
        String bridgePierStyle;
        Integer bridgePierDiameterBlocks;
        Integer bridgePierOffsetBlocks;
        Boolean includeBridgeGuardrail;
        String bridgeGuardrailMaterial;
        Integer bridgeGuardrailHeightBlocks;
        FlatVerticalIntentPersistence.FlatVerticalIntentData flatVerticalIntent;
        VariableCrossSectionsData variableCrossSections;
        StationFacilitiesData stationFacilities;
        List<String> segmentIds = new ArrayList<>();
    }

    static class CrossingData {
        String id;
        String roadAId;
        double stationA;
        String roadBId;
        double stationB;
        Vec2dData position;
        String type;
        String elevatedRoadId;
        Double crossingClearance;
        Double sharedElevation;
        Double cornerRadius;
    }

    static class StackCrossingData {
        String id;
        String roadId;
        Vec2dData position;
        double stationA;
        double stationB;
        String type;
        Double lowerStation;
        Double upperStation;
        Double crossingClearance;
        Double sharedElevation;
    }

    static class NetworkData {
        int schemaVersion = CURRENT_SCHEMA_VERSION;
        String elevationMode;
        Integer uniformElevationY;
        List<NodeData> nodes = new ArrayList<>();
        List<EdgeData> edges = new ArrayList<>();
        List<RoadData> roads = new ArrayList<>();
        List<CrossingData> crossings = new ArrayList<>();
        List<StackCrossingData> stackCrossings = new ArrayList<>();

        static NetworkData from(RoadNetwork network) {
            NetworkData data = new NetworkData();
            data.schemaVersion = CURRENT_SCHEMA_VERSION;
            data.elevationMode = network.getElevationMode().name();
            data.uniformElevationY = network.getUniformElevationY();

            for (RoadNode node : network.nodes.values()) {
                NodeData nodeData = new NodeData();
                nodeData.id = node.getId();
                nodeData.position = new Vec2dData(node.getPosition());
                nodeData.manualElevation = node.getManualElevation();
                if (node.getManualElevationOrigin() != null) {
                    nodeData.manualElevationOrigin = node.getManualElevationOrigin().name();
                }
                if (node.isGradeSeparated()) {
                    nodeData.gradeSeparated = true;
                }
                nodeData.elevatedRoadId = node.getElevatedRoadId();
                nodeData.crossingClearance = node.getCrossingClearance();
                nodeData.cornerRadius = node.getCornerRadius();
                nodeData.plazaRadius = node.getPlazaRadius();
                if (node.getStopLines() != JunctionMarkingSetting.AUTO) {
                    nodeData.stopLines = node.getStopLines().name();
                }
                if (node.getContinuedMarkings() != JunctionMarkingSetting.AUTO) {
                    nodeData.continuedMarkings = node.getContinuedMarkings().name();
                }
                if (node.getCrosswalks() != JunctionMarkingSetting.AUTO) {
                    nodeData.crosswalks = node.getCrosswalks().name();
                }
                if (node.getTurnArrows() != JunctionMarkingSetting.AUTO) {
                    nodeData.turnArrows = node.getTurnArrows().name();
                }
                if (node.getRailwayJunctionMode() != RailwayJunctionMode.DECORATIVE) {
                    nodeData.railwayJunctionMode = node.getRailwayJunctionMode().name();
                }
                nodeData.connectedEdgeIds = new ArrayList<>(node.getConnectedEdgeIds());
                data.nodes.add(nodeData);
            }

            for (Road road : network.roads.values()) {
                RoadData roadData = new RoadData();
                roadData.id = road.getId();
                roadData.name = road.getName();
                roadData.styleId = road.getStyleId();
                roadData.themeId = road.getThemeId();
                roadData.crossSection = CrossSectionData.from(road.getCrossSection());
                roadData.maxSlope = road.getMaxSlope();
                if (road.getTopologyMode() != RoadTopologyMode.LINEAR) {
                    roadData.topologyMode = road.getTopologyMode().name();
                }
                if (road.getLoopSeam() != null) {
                    LoopSeamData seamData = new LoopSeamData();
                    seamData.position = new Vec2dData(road.getLoopSeam().position());
                    seamData.segmentHintId = road.getLoopSeam().segmentHintId();
                    seamData.localFraction = road.getLoopSeam().localFraction();
                    roadData.loopSeam = seamData;
                }
                roadData.designEntryNodeId = road.getDesignEntryNodeId();
                roadData.designEntryEdgeId = road.getDesignEntryEdgeId();
                roadData.horizontalAlignment = HorizontalAlignmentPersistence.toData(road.getHorizontalAlignment());
                roadData.verticalAlignment = VerticalAlignmentPersistence.toData(road.getVerticalAlignment());
                if (road.getStoredVerticalMode() != null) {
                    roadData.verticalMode = road.getStoredVerticalMode().name();
                }
                if (road.getStoredProfileSource() != null) {
                    roadData.profileSource = road.getStoredProfileSource().name();
                }
                if (road.getEditedGeometrySignature() != 0) {
                    roadData.editedGeometrySignature = road.getEditedGeometrySignature();
                }
                if (road.getStoredTerrainStyle() != null) {
                    roadData.terrainStyle = road.getStoredTerrainStyle().name();
                }
                roadData.bridgeThreshold = road.getBridgeThreshold();
                roadData.tunnelStyle = TunnelStylePersistence.from(road.getStoredTunnelStyle());
                roadData.generateBridgePillars = road.getGenerateBridgePillars();
                roadData.bridgePierSpacingBlocks = road.getBridgePierSpacingBlocks();
                roadData.bridgePierMaterial = road.getBridgePierMaterial();
                if (road.getBridgePierStyle() != null) {
                    roadData.bridgePierStyle = road.getBridgePierStyle().name();
                }
                roadData.bridgePierDiameterBlocks = road.getBridgePierDiameterBlocks();
                roadData.bridgePierOffsetBlocks = road.getBridgePierOffsetBlocks();
                roadData.includeBridgeGuardrail = road.getIncludeBridgeGuardrail();
                roadData.bridgeGuardrailMaterial = road.getBridgeGuardrailMaterial();
                roadData.bridgeGuardrailHeightBlocks = road.getBridgeGuardrailHeightBlocks();
                roadData.flatVerticalIntent = FlatVerticalIntentPersistence.toData(road.getFlatVerticalIntent());
                roadData.variableCrossSections = VariableCrossSectionPersistence.toData(road.getVariableCrossSections());
                roadData.stationFacilities = StationFacilityPersistence.toData(road.getStationFacilities());
                roadData.segmentIds = new ArrayList<>(road.getOrderedSegmentIds());
                data.roads.add(roadData);
            }

            for (com.plot.plugin.road.crossing.RoadCrossing crossing : network.crossings.values()) {
                CrossingData crossingData = new CrossingData();
                crossingData.id = crossing.id();
                crossingData.roadAId = crossing.roadAId();
                crossingData.stationA = crossing.stationA();
                crossingData.roadBId = crossing.roadBId();
                crossingData.stationB = crossing.stationB();
                crossingData.position = new Vec2dData(crossing.position());
                crossingData.type = crossing.type().name();
                crossingData.elevatedRoadId = crossing.elevatedRoadId();
                crossingData.crossingClearance = crossing.crossingClearance();
                crossingData.sharedElevation = crossing.sharedElevation();
                crossingData.cornerRadius = crossing.cornerRadius();
                data.crossings.add(crossingData);
            }

            for (com.plot.plugin.road.crossing.RoadStackCrossing stack : network.stackCrossings.values()) {
                StackCrossingData stackData = new StackCrossingData();
                stackData.id = stack.id();
                stackData.roadId = stack.roadId();
                stackData.position = new Vec2dData(stack.position());
                stackData.stationA = stack.stationA();
                stackData.stationB = stack.stationB();
                stackData.type = stack.type().name();
                stackData.lowerStation = stack.lowerStation();
                stackData.upperStation = stack.upperStation();
                stackData.crossingClearance = stack.crossingClearance();
                stackData.sharedElevation = stack.sharedElevation();
                data.stackCrossings.add(stackData);
            }

            // ConcurrentHashMap iteration order is not stable across JVMs — sort for deterministic JSON.
            List<RoadEdge> orderedEdges = new ArrayList<>(network.edges.values());
            orderedEdges.sort(Comparator.comparing(
                edge -> edge.getId() != null ? edge.getId() : "",
                String::compareTo));
            for (RoadEdge edge : orderedEdges) {
                EdgeData edgeData = new EdgeData();
                edgeData.id = edge.getId();
                edgeData.startNodeId = edge.getStartNodeId();
                edgeData.endNodeId = edge.getEndNodeId();
                for (Vec2d point : edge.getCenterlinePoints()) {
                    edgeData.centerlinePoints.add(new Vec2dData(point));
                }
                edgeData.roadId = edge.getRoadId();
                edgeData.sourceRoadId = edge.getSourceRoadId();
                for (RoadEdge.SlopeOverride override : edge.getSlopeOverrides()) {
                    SlopeOverrideData overrideData = new SlopeOverrideData();
                    overrideData.startDistance = override.startDistance;
                    overrideData.endDistance = override.endDistance;
                    overrideData.maxSlope = override.maxSlope;
                    edgeData.slopeOverrides.add(overrideData);
                }
                data.edges.add(edgeData);
            }

            return data;
        }

        RoadNetwork toNetwork() {
            RoadNetwork network = new RoadNetwork();
            network.setElevationMode(NetworkElevationMode.fromStored(elevationMode));
            if (uniformElevationY != null) {
                network.setUniformElevationY(uniformElevationY);
            }

            for (NodeData nodeData : nodes) {
                RoadNode node = new RoadNode(
                    nodeData.id,
                    nodeData.position != null ? nodeData.position.toVec2d() : new Vec2d(0, 0),
                    nodeData.manualElevation,
                    nodeData.cornerRadius,
                    null
                );
                node.setPlazaRadius(nodeData.plazaRadius);
                node.setStopLines(JunctionMarkingSetting.fromString(nodeData.stopLines));
                node.setContinuedMarkings(JunctionMarkingSetting.fromString(nodeData.continuedMarkings));
                node.setCrosswalks(JunctionMarkingSetting.fromString(nodeData.crosswalks));
                node.setTurnArrows(JunctionMarkingSetting.fromString(nodeData.turnArrows));
                node.setRailwayJunctionMode(RailwayJunctionMode.fromString(nodeData.railwayJunctionMode));
                ManualElevationOrigin origin = ManualElevationOrigin.fromStored(nodeData.manualElevationOrigin);
                if (nodeData.manualElevation != null) {
                    node.setManualElevation(nodeData.manualElevation, origin);
                }
                if (Boolean.TRUE.equals(nodeData.gradeSeparated)) {
                    node.setGradeSeparated(true);
                    node.setElevatedRoadId(nodeData.elevatedRoadId);
                    node.setCrossingClearance(nodeData.crossingClearance);
                } else {
                    node.clearGradeSeparation();
                }
                // 拓扑由下方 rebuildTopologyFromEdges 按边 start/end 重建，不信任序列化的 connectedEdgeIds
                network.nodes.put(node.getId(), node);
            }

            boolean hasRoadData = roads != null && !roads.isEmpty();
            if (hasRoadData) {
                for (RoadData roadData : roads) {
                    // crossSection 缺失已在 validateNetworkData 拒绝；此处防御。
                    if (roadData.crossSection == null) {
                        continue;
                    }
                    RoadCrossSection crossSection = roadData.crossSection.toCrossSection();
                    Road road = new Road(
                        roadData.id,
                        roadData.name,
                        crossSection,
                        roadData.maxSlope,
                        roadData.segmentIds != null ? new java.util.LinkedHashSet<>(roadData.segmentIds) : java.util.Set.of()
                    );
                    road.setStyleId(roadData.styleId);
                    road.setThemeId(roadData.themeId);
                    road.setTopologyMode(RoadTopologyMode.fromStored(roadData.topologyMode));
                    if (roadData.loopSeam != null && roadData.loopSeam.position != null) {
                        road.setLoopSeam(new RoadLoopSeam(
                            roadData.loopSeam.position.toVec2d(),
                            roadData.loopSeam.segmentHintId,
                            roadData.loopSeam.localFraction));
                    }
                    // Design-entry binding applied after edges exist — mid-load
                    // assignEdgeToRoad → syncStorageOrder → ensureBound must not rebind
                    // against a partial topology and wipe the persisted authority.
                    road.setHorizontalAlignment(HorizontalAlignmentPersistence.fromData(roadData.horizontalAlignment));
                    road.setVerticalAlignment(VerticalAlignmentPersistence.fromData(roadData.verticalAlignment));
                    road.setVerticalMode(com.plot.plugin.road.vertical.RoadVerticalMode.fromStored(
                        roadData.verticalMode));
                    road.setProfileSource(com.plot.plugin.road.vertical.ProfileSource.fromStored(
                        roadData.profileSource));
                    if (roadData.editedGeometrySignature != null) {
                        road.setEditedGeometrySignature(roadData.editedGeometrySignature);
                    }
                    if (roadData.terrainStyle != null && !roadData.terrainStyle.isBlank()) {
                        road.setTerrainStyle(com.plot.plugin.road.terrain.RoadTerrainStyle
                            .fromStored(roadData.terrainStyle));
                    }
                    road.setBridgeThreshold(roadData.bridgeThreshold);
                    road.setTunnelStyle(TunnelStylePersistence.fromData(roadData.tunnelStyle));
                    road.setGenerateBridgePillars(roadData.generateBridgePillars);
                    road.setBridgePierSpacingBlocks(roadData.bridgePierSpacingBlocks);
                    road.setBridgePierMaterial(roadData.bridgePierMaterial);
                    road.setBridgePierStyle(
                        com.plot.plugin.road.model.section.BridgePierStyle.fromId(roadData.bridgePierStyle));
                    road.setBridgePierDiameterBlocks(roadData.bridgePierDiameterBlocks);
                    road.setBridgePierOffsetBlocks(roadData.bridgePierOffsetBlocks);
                    road.setIncludeBridgeGuardrail(roadData.includeBridgeGuardrail);
                    road.setBridgeGuardrailMaterial(roadData.bridgeGuardrailMaterial);
                    road.setBridgeGuardrailHeightBlocks(roadData.bridgeGuardrailHeightBlocks);
                    road.setFlatVerticalIntent(FlatVerticalIntentPersistence.fromData(roadData.flatVerticalIntent));
                    road.setVariableCrossSections(VariableCrossSectionPersistence.fromData(roadData.variableCrossSections));
                    road.setStationFacilities(StationFacilityPersistence.fromData(roadData.stationFacilities));
                    network.roads.put(road.getId(), road);
                }
            }

            for (EdgeData edgeData : edges) {
                List<Vec2d> points = new ArrayList<>();
                if (edgeData.centerlinePoints != null) {
                    for (Vec2dData pointData : edgeData.centerlinePoints) {
                        points.add(pointData.toVec2d());
                    }
                }

                List<RoadEdge.SlopeOverride> overrides = new ArrayList<>();
                if (edgeData.slopeOverrides != null) {
                    for (SlopeOverrideData overrideData : edgeData.slopeOverrides) {
                        overrides.add(new RoadEdge.SlopeOverride(
                            overrideData.startDistance,
                            overrideData.endDistance,
                            overrideData.maxSlope
                        ));
                    }
                }

                String roadId = edgeData.roadId != null ? edgeData.roadId : edgeData.sourceRoadId;
                String adoptGroupId = edgeData.roadId != null ? edgeData.sourceRoadId : null;
                RoadEdge edge = new RoadEdge(
                    edgeData.id,
                    edgeData.startNodeId,
                    edgeData.endNodeId,
                    points,
                    roadId,
                    overrides,
                    adoptGroupId
                );
                network.edges.put(edge.getId(), edge);
                // Do not assignEdgeToRoad here: syncStorageOrder→ensureBound on a partial edge
                // set rewrites persisted segment order (ConcurrentHashMap-sensitive on Linux CI).
                // Membership comes from roadData.segmentIds; orphans are fixed in reconcile.
                if (roadId != null && !roadId.isBlank()) {
                    Road road = network.roads.get(roadId);
                    if (road != null && !road.getOrderedSegmentIds().contains(edge.getId())) {
                        road.addSegment(edge.getId());
                    }
                }
            }

            if (crossings != null) {
                for (CrossingData crossingData : crossings) {
                    if (crossingData == null || crossingData.id == null || crossingData.position == null) {
                        continue;
                    }
                    network.registerCrossing(new com.plot.plugin.road.crossing.RoadCrossing(
                        crossingData.id,
                        crossingData.roadAId,
                        crossingData.stationA,
                        crossingData.roadBId,
                        crossingData.stationB,
                        crossingData.position.toVec2d(),
                        com.plot.plugin.road.crossing.CrossingType.fromStored(crossingData.type),
                        crossingData.elevatedRoadId,
                        crossingData.crossingClearance,
                        crossingData.sharedElevation,
                        crossingData.cornerRadius));
                }
            }

            if (stackCrossings != null) {
                for (StackCrossingData stackData : stackCrossings) {
                    if (stackData == null || stackData.id == null || stackData.position == null) {
                        continue;
                    }
                    network.registerStackCrossing(new com.plot.plugin.road.crossing.RoadStackCrossing(
                        stackData.id,
                        stackData.roadId,
                        stackData.position.toVec2d(),
                        stackData.stationA,
                        stackData.stationB,
                        com.plot.plugin.road.crossing.CrossingType.fromStored(stackData.type),
                        stackData.lowerStation,
                        stackData.upperStation,
                        stackData.crossingClearance,
                        stackData.sharedElevation));
                }
            }

            rebuildTopologyFromEdges(network);
            // Restore design-entry authority before reconcile so any real re-assign syncs
            // against the persisted binding instead of a mid-load ensureBound guess.
            if (hasRoadData) {
                for (RoadData roadData : roads) {
                    if (roadData == null || roadData.id == null) {
                        continue;
                    }
                    Road road = network.roads.get(roadData.id);
                    if (road == null) {
                        continue;
                    }
                    road.setDesignEntryNodeId(roadData.designEntryNodeId);
                    road.setDesignEntryEdgeId(roadData.designEntryEdgeId);
                }
            }
            network.reconcileRoadSegmentLinks();
            network.assertInvariants();
            return network;
        }

        /**
         * 按所有边的 start/end 重建节点连接列表，修正损坏或旧数据中的拓扑不一致。
         */
        private static void rebuildTopologyFromEdges(RoadNetwork network) {
            for (RoadNode node : network.nodes.values()) {
                node.clearConnectedEdges();
            }
            for (RoadEdge edge : network.edges.values()) {
                RoadNode start = network.nodes.get(edge.getStartNodeId());
                RoadNode end = network.nodes.get(edge.getEndNodeId());
                if (start != null) {
                    start.addEdge(edge.getId());
                }
                if (end != null) {
                    end.addEdge(edge.getId());
                }
            }
        }
    }
}
