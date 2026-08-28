package com.plot.plugin.road.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixTypeAdapter;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadMaterialMixUtils;
import com.plot.plugin.road.RoadMaterialUtils;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.section.BikeLane;
import com.plot.plugin.road.model.section.Drain;
import com.plot.plugin.road.model.section.Lane;
import com.plot.plugin.road.model.section.LaneGroup;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.Markings;
import com.plot.plugin.road.model.section.Median;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.Shoulder;
import com.plot.plugin.road.model.section.Sidewalk;
import com.plot.plugin.road.model.section.SlopeBatter;
import com.plot.plugin.road.model.section.StreetFurniture;

import com.plot.plugin.road.graph.RoadGraphQueries;
import com.plot.utils.PlotI18n;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
 * <p><strong>后台或跨线程读者</strong>：应使用 {@link #snapshot()}（或等价的 JSON 往返）取得
 * 独立副本后再做生成、校验、探测等耗时工作，不要与 live 实例并发读写。
 *
 * @see RoadNetworkManager
 * @see RoadNetworkHistory
 */
public class RoadNetwork {
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(MaterialMix.class, new MaterialMixTypeAdapter())
        .create();

    /** 并发安全的 id 索引；元素 {@link RoadNode} 本身非线程安全。 */
    private final Map<String, RoadNode> nodes = new ConcurrentHashMap<>();
    /** 并发安全的 id 索引；元素 {@link RoadEdge} 本身非线程安全。 */
    private final Map<String, RoadEdge> edges = new ConcurrentHashMap<>();
    /** 并发安全的 id 索引；元素 {@link Road} 本身非线程安全。 */
    private final Map<String, Road> roads = new ConcurrentHashMap<>();

    public Map<String, RoadNode> getNodes() {
        return Map.copyOf(nodes);
    }

    public Map<String, RoadEdge> getEdges() {
        return Map.copyOf(edges);
    }

    public Map<String, Road> getRoads() {
        return Map.copyOf(roads);
    }

    public RoadNode getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    public RoadEdge getEdge(String edgeId) {
        return edges.get(edgeId);
    }

    public Road getRoad(String roadId) {
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
        roads.put(road.getId(), road);
        return road;
    }

    public Road createRoad(String roadId) {
        Road road = new Road(roadId);
        roads.put(road.getId(), road);
        return road;
    }

    public Road createRoad(RoadSystemConfig defaults) {
        return createRoad();
    }

    /**
     * 创建道路并快照写入全局配置（显式值，不随全局配置变更而变）。
     */
    public Road createRoadFromDefaults(RoadSystemConfig defaults) {
        Road road = createRoad();
        if (defaults != null) {
            road.applyDefaults(defaults);
        }
        return road;
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
        if (oldRoadId != null && !oldRoadId.equals(newRoadId)) {
            Road oldRoad = roads.get(oldRoadId);
            if (oldRoad != null) {
                oldRoad.removeSegment(edgeId);
            }
        }
        edge.setRoadId(newRoadId);
        newRoad.addSegment(edgeId);
        return true;
    }

    /**
     * 解除 edge 与 road 的双向归属，不改变拓扑。
     */
    public boolean unassignEdgeFromRoad(String edgeId) {
        RoadEdge edge = edges.get(edgeId);
        if (edge == null) {
            return false;
        }
        String oldRoadId = edge.getRoadId();
        if (oldRoadId != null) {
            Road oldRoad = roads.get(oldRoadId);
            if (oldRoad != null) {
                oldRoad.removeSegment(edgeId);
            }
        }
        edge.setRoadId(null);
        return true;
    }

    /** @deprecated 使用 {@link #assignEdgeToRoad(String, String)} */
    @Deprecated
    public void linkEdgeToRoad(String roadId, String edgeId) {
        assignEdgeToRoad(edgeId, roadId);
    }

    /**
     * 修正 Road.segmentIds 与 RoadEdge.roadId 的双向归属一致性。
     */
    public void reconcileRoadSegmentLinks() {
        for (Road road : roads.values()) {
            for (String segmentId : List.copyOf(road.getSegmentIds())) {
                if (!edges.containsKey(segmentId)) {
                    road.removeSegment(segmentId);
                }
            }
        }
        for (RoadEdge edge : edges.values()) {
            String roadId = edge.getRoadId();
            if (roadId == null || roadId.isBlank()) {
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
            if (road != null && road.getSegmentIds().isEmpty()) {
                roads.remove(roadId);
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
     *
     * @return 新 Road 的 id；若无法拆分（分段不存在或为第一段）则 null
     */
    public String splitRoadBeforeSegment(String roadId, String segmentEdgeId) {
        Road road = roads.get(roadId);
        if (road == null || segmentEdgeId == null || segmentEdgeId.isBlank()) {
            return null;
        }
        List<String> segmentIds = new ArrayList<>(road.getSegmentIds());
        int index = segmentIds.indexOf(segmentEdgeId);
        if (index < 0 || index == 0) {
            return null;
        }
        Road newRoad = createRoad();
        newRoad.copyEngineeringFrom(road);
        for (int i = index; i < segmentIds.size(); i++) {
            assignEdgeToRoad(segmentIds.get(i), newRoad.getId());
        }
        return newRoad.getId();
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
        if (elevatedRoadId != null && !elevatedRoadId.isBlank()) {
            Set<String> roadIds = getDistinctRoadIdsAtNode(nodeId);
            if (roadIds.size() != 2 || !roadIds.contains(elevatedRoadId)) {
                return false;
            }
            node.setElevatedRoadId(elevatedRoadId);
        } else {
            node.setElevatedRoadId(null);
        }
        node.setGradeSeparated(true);
        node.setCrossingClearance(crossingClearance);
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
            throw new RoadNetworkFormatException(
                RoadNetworkFormatException.Reason.EMPTY_INPUT,
                PlotI18n.error("error.plot.road.network.empty_input"));
        }
        try {
            NetworkData data = GSON.fromJson(json, NetworkData.class);
            if (data == null) {
                throw new RoadNetworkFormatException(
                    RoadNetworkFormatException.Reason.VALIDATION_FAILED,
                    PlotI18n.error("error.plot.road.network.invalid_json"));
            }
            validateNetworkData(data);
            return data.toNetwork();
        } catch (RoadNetworkFormatException e) {
            throw e;
        } catch (JsonParseException e) {
            throw new RoadNetworkFormatException(
                RoadNetworkFormatException.Reason.INVALID_JSON,
                PlotI18n.error("error.plot.road.network.invalid_json"),
                e);
        } catch (RuntimeException e) {
            throw new RoadNetworkFormatException(
                RoadNetworkFormatException.Reason.INVALID_JSON,
                PlotI18n.error("error.plot.road.network.invalid_json"),
                e);
        }
    }

    private static void validateNetworkData(NetworkData data) throws RoadNetworkFormatException {
        if (data.nodes == null || data.edges == null || data.roads == null) {
            throw new RoadNetworkFormatException(
                RoadNetworkFormatException.Reason.VALIDATION_FAILED,
                PlotI18n.error("error.plot.road.network.invalid_json"));
        }
    }

    /**
     * 保存网络到文件（原子性保存，先写临时文件再重命名）。
     * 应在 client 线程对 live 网络调用，或传入 {@link #snapshot()} 的副本。
     */
    public void saveTo(Path file) throws IOException {
        com.plot.core.persistence.AtomicFileWriter.write(file, toJson());
    }

    public static RoadNetwork loadFrom(Path file) throws IOException {
        if (!Files.exists(file)) {
            return new RoadNetwork();
        }
        String json = Files.readString(file);
        if (json.isBlank()) {
            throw new RoadNetworkFormatException(
                RoadNetworkFormatException.Reason.EMPTY_INPUT,
                PlotI18n.error("error.plot.road.network.empty_input") + " (" + file.getFileName() + ")");
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
     * 深拷贝快照（JSON 往返），供后台生成/校验或与 live 网络隔离的只读分析使用。
     */
    public RoadNetwork snapshot() {
        return parseSnapshot(toJson());
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
        Boolean gradeSeparated;
        String elevatedRoadId;
        Double crossingClearance;
        Double cornerRadius;
        String stopLines;
        String continuedMarkings;
        String crosswalks;
        String turnArrows;
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

        // Legacy fields (v1) — migrated into Road on load
        Integer width;
        String material;
        Boolean includeSidewalk;
        Integer sidewalkWidth;
        String sidewalkMaterial;
        Integer streetlightSpacing;
        Float maxSlope;
        String sourceRoadId;
    }

    static class LaneData {
        Integer width;
        String material;
    }

    static class LaneGroupData {
        Integer laneCount;
        Integer width;
        MaterialMix material;
        List<LaneData> lanes = new ArrayList<>();
    }

    static class MedianData {
        Boolean enabled;
        Integer width;
        String material;
    }

    static class MarkingsData {
        Boolean laneDividers;
        Boolean centerLine;
        String centerLineStyle;
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

    static class DrainData {
        Boolean enabled;
    }

    static class BikeLaneData {
        Boolean enabled;
        Integer width;
        String material;
    }

    static class SlopeBatterData {
        Boolean enabled;
        Float fillRatio;
        Float cutRatio;
        String fillMaterial;
        String cutMaterial;
    }

    static class StreetFurnitureData {
        Integer streetlightSpacing;
    }

    static class CrossSectionData {
        LaneGroupData carriageway;
        MedianData median;
        MarkingsData markings;
        ShoulderData shoulder;
        BikeLaneData bikeLane;
        SidewalkData sidewalk;
        DrainData drain;
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
            Drain drain = section.getDrain();
            if (drain != null) {
                data.drain = new DrainData();
                data.drain.enabled = drain.getEnabled();
            }
            SlopeBatter slopeBatter = section.getSlopeBatter();
            if (slopeBatter != null) {
                data.slopeBatter = new SlopeBatterData();
                data.slopeBatter.enabled = slopeBatter.getEnabled();
                data.slopeBatter.fillRatio = slopeBatter.getFillRatio();
                data.slopeBatter.cutRatio = slopeBatter.getCutRatio();
                data.slopeBatter.fillMaterial = slopeBatter.getFillMaterial();
                data.slopeBatter.cutMaterial = slopeBatter.getCutMaterial();
            }
            StreetFurniture furniture = section.getStreetFurniture();
            if (furniture != null) {
                data.streetFurniture = new StreetFurnitureData();
                data.streetFurniture.streetlightSpacing = furniture.getStreetlightSpacing();
            }
            return data;
        }

        RoadCrossSection toCrossSection() {
            RoadCrossSection section = new RoadCrossSection();
            if (carriageway != null) {
                LaneGroup laneGroup = new LaneGroup();
                laneGroup.setLaneCount(carriageway.laneCount);
                laneGroup.setWidth(carriageway.width);
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
            if (drain != null) {
                Drain drainComponent = new Drain();
                drainComponent.setEnabled(drain.enabled);
                section.setDrain(drainComponent);
            }
            if (slopeBatter != null) {
                SlopeBatter slopeComponent = new SlopeBatter();
                slopeComponent.setEnabled(slopeBatter.enabled);
                slopeComponent.setFillRatio(slopeBatter.fillRatio);
                slopeComponent.setCutRatio(slopeBatter.cutRatio);
                slopeComponent.setFillMaterial(RoadMaterialUtils.normalizeStoredMaterial(slopeBatter.fillMaterial));
                slopeComponent.setCutMaterial(RoadMaterialUtils.normalizeStoredMaterial(slopeBatter.cutMaterial));
                section.setSlopeBatter(slopeComponent);
            }
            if (streetFurniture != null) {
                StreetFurniture furniture = new StreetFurniture();
                furniture.setStreetlightSpacing(streetFurniture.streetlightSpacing);
                section.setStreetFurniture(furniture);
            }
            return section;
        }
    }

    static class RoadData {
        String id;
        String name;
        String styleId;
        CrossSectionData crossSection;
        // Legacy flat fields (v1) — read for migration, not written on save
        Integer width;
        String material;
        Boolean includeSidewalk;
        Integer sidewalkWidth;
        String sidewalkMaterial;
        Boolean includeShoulder;
        Integer shoulderWidth;
        String shoulderMaterial;
        Boolean includeDrainage;
        Integer streetlightSpacing;
        Float maxSlope;
        List<String> segmentIds = new ArrayList<>();
    }

    static class NetworkData {
        List<NodeData> nodes = new ArrayList<>();
        List<EdgeData> edges = new ArrayList<>();
        List<RoadData> roads = new ArrayList<>();

        static NetworkData from(RoadNetwork network) {
            NetworkData data = new NetworkData();

            for (RoadNode node : network.nodes.values()) {
                NodeData nodeData = new NodeData();
                nodeData.id = node.getId();
                nodeData.position = new Vec2dData(node.getPosition());
                nodeData.manualElevation = node.getManualElevation();
                if (node.isGradeSeparated()) {
                    nodeData.gradeSeparated = true;
                }
                nodeData.elevatedRoadId = node.getElevatedRoadId();
                nodeData.crossingClearance = node.getCrossingClearance();
                nodeData.cornerRadius = node.getCornerRadius();
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
                nodeData.connectedEdgeIds = new ArrayList<>(node.getConnectedEdgeIds());
                data.nodes.add(nodeData);
            }

            for (Road road : network.roads.values()) {
                RoadData roadData = new RoadData();
                roadData.id = road.getId();
                roadData.name = road.getName();
                roadData.styleId = road.getStyleId();
                roadData.crossSection = CrossSectionData.from(road.getCrossSection());
                roadData.maxSlope = road.getMaxSlope();
                roadData.segmentIds = new ArrayList<>(road.getSegmentIds());
                data.roads.add(roadData);
            }

            for (RoadEdge edge : network.edges.values()) {
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

            for (NodeData nodeData : nodes) {
                RoadNode node = new RoadNode(
                    nodeData.id,
                    nodeData.position != null ? nodeData.position.toVec2d() : new Vec2d(0, 0),
                    nodeData.manualElevation,
                    nodeData.cornerRadius,
                    null
                );
                node.setStopLines(JunctionMarkingSetting.fromString(nodeData.stopLines));
                node.setContinuedMarkings(JunctionMarkingSetting.fromString(nodeData.continuedMarkings));
                node.setCrosswalks(JunctionMarkingSetting.fromString(nodeData.crosswalks));
                node.setTurnArrows(JunctionMarkingSetting.fromString(nodeData.turnArrows));
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
                    RoadCrossSection crossSection = roadData.crossSection != null
                        ? roadData.crossSection.toCrossSection()
                        : RoadCrossSection.fromLegacy(
                            roadData.width,
                            RoadMaterialUtils.normalizeStoredMaterial(roadData.material),
                            roadData.includeSidewalk,
                            roadData.sidewalkWidth,
                            RoadMaterialUtils.normalizeStoredMaterial(roadData.sidewalkMaterial),
                            roadData.streetlightSpacing
                        );
                    RoadCrossSection.mergeLegacyFlatFields(
                        crossSection,
                        roadData.width,
                        roadData.material,
                        roadData.includeSidewalk,
                        roadData.sidewalkWidth,
                        roadData.sidewalkMaterial,
                        roadData.includeShoulder,
                        roadData.shoulderWidth,
                        roadData.shoulderMaterial,
                        roadData.includeDrainage,
                        roadData.streetlightSpacing
                    );
                    Road road = new Road(
                        roadData.id,
                        roadData.name,
                        crossSection,
                        roadData.maxSlope,
                        roadData.segmentIds != null ? new java.util.LinkedHashSet<>(roadData.segmentIds) : java.util.Set.of()
                    );
                    road.setStyleId(roadData.styleId);
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

                if (!hasRoadData) {
                    migrateLegacyEdge(network, edgeData, edge);
                } else if (roadId != null && !roadId.isBlank()) {
                    network.assignEdgeToRoad(edge.getId(), roadId);
                }
            }

            rebuildTopologyFromEdges(network);
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

        private static void migrateLegacyEdge(RoadNetwork network, EdgeData edgeData, RoadEdge edge) {
            String roadId = edgeData.roadId != null ? edgeData.roadId : edgeData.sourceRoadId;
            if (roadId == null || roadId.isBlank()) {
                roadId = UUID.randomUUID().toString();
                edge.setRoadId(roadId);
            }

            Road road = network.roads.get(roadId);
            if (road == null) {
                road = new Road(
                    roadId,
                    null,
                    edgeData.width,
                    RoadMaterialUtils.normalizeStoredMaterial(edgeData.material),
                    edgeData.includeSidewalk,
                    edgeData.sidewalkWidth,
                    RoadMaterialUtils.normalizeStoredMaterial(edgeData.sidewalkMaterial),
                    edgeData.streetlightSpacing,
                    edgeData.maxSlope,
                    java.util.Set.of()
                );
                network.roads.put(roadId, road);
            }
            network.assignEdgeToRoad(edge.getId(), roadId);
        }
    }
}
