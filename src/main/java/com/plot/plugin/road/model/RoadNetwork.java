package com.plot.plugin.road.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadMaterialUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 道路网络（插件私有数据模型）
 */
public class RoadNetwork {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, RoadNode> nodes = new LinkedHashMap<>();
    private final Map<String, RoadEdge> edges = new LinkedHashMap<>();

    public Map<String, RoadNode> getNodes() {
        return Map.copyOf(nodes);
    }

    public Map<String, RoadEdge> getEdges() {
        return Map.copyOf(edges);
    }

    public RoadNode getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    public RoadEdge getEdge(String edgeId) {
        return edges.get(edgeId);
    }

    public RoadNode createNode(Vec2d position) {
        RoadNode node = new RoadNode(position);
        nodes.put(node.getId(), node);
        return node;
    }

    public RoadEdge createEdge(String startNodeId, String endNodeId, List<Vec2d> points) {
        RoadNode start = nodes.get(startNodeId);
        RoadNode end = nodes.get(endNodeId);
        if (start == null || end == null) {
            throw new IllegalArgumentException("Start or end node does not exist");
        }

        RoadEdge edge = new RoadEdge(startNodeId, endNodeId, points);
        edges.put(edge.getId(), edge);
        start.addEdge(edge.getId());
        end.addEdge(edge.getId());
        return edge;
    }

    public void removeEdge(String edgeId) {
        detachEdge(edgeId);
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
        Integer width;
        String material;
        Boolean includeSidewalk;
        Integer sidewalkWidth;
        String sidewalkMaterial;
        Integer streetlightSpacing;
        Float maxSlope;
        List<SlopeOverrideData> slopeOverrides = new ArrayList<>();
        String sourceRoadId;
    }

    static class NetworkData {
        List<NodeData> nodes = new ArrayList<>();
        List<EdgeData> edges = new ArrayList<>();

        static NetworkData from(RoadNetwork network) {
            NetworkData data = new NetworkData();

            for (RoadNode node : network.nodes.values()) {
                NodeData nodeData = new NodeData();
                nodeData.id = node.getId();
                nodeData.position = new Vec2dData(node.getPosition());
                nodeData.manualElevation = node.getManualElevation();
                nodeData.connectedEdgeIds = new ArrayList<>(node.getConnectedEdgeIds());
                data.nodes.add(nodeData);
            }

            for (RoadEdge edge : network.edges.values()) {
                EdgeData edgeData = new EdgeData();
                edgeData.id = edge.getId();
                edgeData.startNodeId = edge.getStartNodeId();
                edgeData.endNodeId = edge.getEndNodeId();
                for (Vec2d point : edge.getCenterlinePoints()) {
                    edgeData.centerlinePoints.add(new Vec2dData(point));
                }
                edgeData.width = edge.getWidth();
                edgeData.material = edge.getMaterial();
                edgeData.includeSidewalk = edge.getIncludeSidewalk();
                edgeData.sidewalkWidth = edge.getSidewalkWidth();
                edgeData.sidewalkMaterial = edge.getSidewalkMaterial();
                edgeData.streetlightSpacing = edge.getStreetlightSpacing();
                edgeData.maxSlope = edge.getMaxSlope();
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
                    null
                );
                if (nodeData.connectedEdgeIds != null) {
                    for (String edgeId : nodeData.connectedEdgeIds) {
                        node.addEdge(edgeId);
                    }
                }
                network.nodes.put(node.getId(), node);
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

                RoadEdge edge = new RoadEdge(
                    edgeData.id,
                    edgeData.startNodeId,
                    edgeData.endNodeId,
                    points,
                    edgeData.width,
                    RoadMaterialUtils.normalizeStoredMaterial(edgeData.material),
                    edgeData.includeSidewalk,
                    edgeData.sidewalkWidth,
                    RoadMaterialUtils.normalizeStoredMaterial(edgeData.sidewalkMaterial),
                    edgeData.streetlightSpacing,
                    edgeData.maxSlope,
                    overrides
                );
                edge.setSourceRoadId(edgeData.sourceRoadId);
                network.edges.put(edge.getId(), edge);
            }

            return network;
        }
    }
}
