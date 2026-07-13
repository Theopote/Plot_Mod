package com.plot.plugin.road.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadMaterialUtils;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 道路网络（插件私有数据模型）：拓扑几何 + 逻辑道路工程对象。
 *
 * 使用ConcurrentHashMap保证线程安全，支持多线程并发访问（UI线程、渲染线程、持久化线程）。
 */
public class RoadNetwork {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, RoadNode> nodes = new ConcurrentHashMap<>();
    private final Map<String, RoadEdge> edges = new ConcurrentHashMap<>();
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
        Road road = createRoad();
        road.applyDefaults(defaults);
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
        if (roadId != null && !roadId.isBlank()) {
            edge.setRoadId(roadId);
            Road road = roads.get(roadId);
            if (road != null) {
                road.addSegment(edge.getId());
            }
        }
        edges.put(edge.getId(), edge);
        start.addEdge(edge.getId());
        end.addEdge(edge.getId());
        return edge;
    }

    public void linkEdgeToRoad(String roadId, String edgeId) {
        Road road = roads.get(roadId);
        RoadEdge edge = edges.get(edgeId);
        if (road == null || edge == null) {
            return;
        }
        edge.setRoadId(roadId);
        road.addSegment(edgeId);
    }

    public void removeEdge(String edgeId) {
        RoadEdge edge = edges.get(edgeId);
        String roadId = edge != null ? edge.getRoadId() : null;
        detachEdge(edgeId);
        if (roadId != null) {
            Road road = roads.get(roadId);
            if (road != null) {
                road.removeSegment(edgeId);
                if (road.getSegmentIds().isEmpty()) {
                    roads.remove(roadId);
                }
            }
        }
        cleanupIsolatedNodes();
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

    public String toJson() {
        NetworkData data = NetworkData.from(this);
        return GSON.toJson(data);
    }

    public static RoadNetwork fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new RoadNetwork();
        }
        NetworkData data = GSON.fromJson(json, NetworkData.class);
        return data != null ? data.toNetwork() : new RoadNetwork();
    }

    public void saveTo(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, toJson());
    }

    public static RoadNetwork loadFrom(Path file) throws IOException {
        if (!Files.exists(file)) {
            return new RoadNetwork();
        }
        return fromJson(Files.readString(file));
    }

    RoadNetwork deepCopy() {
        return fromJson(toJson());
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
        String material;
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
                laneGroup.setMaterial(RoadMaterialUtils.normalizeStoredMaterial(carriageway.material));
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
                if (nodeData.connectedEdgeIds != null) {
                    for (String edgeId : nodeData.connectedEdgeIds) {
                        node.addEdge(edgeId);
                    }
                }
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
                RoadEdge edge = new RoadEdge(
                    edgeData.id,
                    edgeData.startNodeId,
                    edgeData.endNodeId,
                    points,
                    roadId,
                    overrides
                );
                network.edges.put(edge.getId(), edge);

                if (!hasRoadData) {
                    migrateLegacyEdge(network, edgeData, edge);
                } else if (roadId != null && !roadId.isBlank()) {
                    Road road = network.roads.get(roadId);
                    if (road != null) {
                        road.addSegment(edge.getId());
                    }
                }
            }

            return network;
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
                edge.setRoadId(roadId);
            }
            road.addSegment(edge.getId());
        }
    }
}
