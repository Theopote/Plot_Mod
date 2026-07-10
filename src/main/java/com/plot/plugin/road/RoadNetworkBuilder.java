package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.model.Shape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 道路网络拓扑构建（认领、求交打断、路口分类）
 */
public class RoadNetworkBuilder {
    public static final double NODE_TOLERANCE = 0.5;

    public enum JunctionType {
        ENDPOINT,
        THROUGH,
        T_JUNCTION,
        CROSSROAD,
        COMPLEX
    }

    public RoadEdge adoptShape(RoadNetwork network, Shape shape, RoadSystemConfig defaults) {
        List<Vec2d> points = RoadGeometryUtils.extractShapePoints(shape);
        if (points.size() < 2) {
            throw new IllegalArgumentException("Shape must have at least 2 points");
        }

        List<Vec2d> endpoints = shape.getEndpoints();
        Vec2d startPoint = endpoints != null && !endpoints.isEmpty()
            ? endpoints.getFirst()
            : points.getFirst();
        Vec2d endPoint = endpoints != null && endpoints.size() > 1
            ? endpoints.getLast()
            : points.getLast();

        RoadNode startNode = findOrCreateNode(network, startPoint);
        RoadNode endNode = findOrCreateNode(network, endPoint);

        RoadEdge edge = network.createEdge(startNode.getId(), endNode.getId(), points);
        if (defaults != null) {
            edge.setWidth(defaults.getRoadWidth());
            edge.setMaterial(defaults.getSelectedMaterial());
            edge.setIncludeSidewalk(defaults.isIncludeSidewalk());
            edge.setSidewalkWidth(defaults.getSidewalkWidth());
            edge.setMaxSlope(defaults.getMaxSlope());
        }

        detectAndSplitIntersections(network);
        return network.getEdge(edge.getId());
    }

    public void detectAndSplitIntersections(RoadNetwork network) {
        boolean changed = true;
        int safety = 0;
        while (changed && safety++ < 100) {
            changed = false;
            List<RoadEdge> edgeList = new ArrayList<>(network.getEdges().values());

            for (int i = 0; i < edgeList.size(); i++) {
                for (int j = i + 1; j < edgeList.size(); j++) {
                    RoadEdge edgeA = network.getEdge(edgeList.get(i).getId());
                    RoadEdge edgeB = network.getEdge(edgeList.get(j).getId());
                    if (edgeA == null || edgeB == null) {
                        continue;
                    }

                    List<Vec2d> intersections = findIntersections(edgeA, edgeB);
                    for (Vec2d intersection : intersections) {
                        if (isNearEdgeEndpoint(edgeA, intersection) || isNearEdgeEndpoint(edgeB, intersection)) {
                            continue;
                        }
                        if (alreadyConnectedAt(network, edgeA, edgeB, intersection)) {
                            continue;
                        }

                        RoadNode junctionNode = findOrCreateNode(network, intersection);
                        boolean splitA = splitEdgeAtNode(network, edgeA.getId(), junctionNode.getId(), intersection);
                        boolean splitB = splitEdgeAtNode(network, edgeB.getId(), junctionNode.getId(), intersection);
                        if (splitA || splitB) {
                            changed = true;
                        }
                    }
                }
            }
        }
    }

    public JunctionType classify(RoadNode node) {
        if (node == null) {
            return JunctionType.ENDPOINT;
        }
        return switch (node.getDegree()) {
            case 0, 1 -> JunctionType.ENDPOINT;
            case 2 -> JunctionType.THROUGH;
            case 3 -> JunctionType.T_JUNCTION;
            case 4 -> JunctionType.CROSSROAD;
            default -> JunctionType.COMPLEX;
        };
    }

    private RoadNode findOrCreateNode(RoadNetwork network, Vec2d position) {
        for (RoadNode node : network.getNodes().values()) {
            if (RoadGeometryUtils.pointsNear(node.getPosition(), position, NODE_TOLERANCE)) {
                return node;
            }
        }
        return network.createNode(position);
    }

    private List<Vec2d> findIntersections(RoadEdge edgeA, RoadEdge edgeB) {
        PolylineShape polyA = new PolylineShape(edgeA.getCenterlinePoints(), false);
        PolylineShape polyB = new PolylineShape(edgeB.getCenterlinePoints(), false);
        List<Vec2d> raw = polyA.getIntersectionsWith(polyB);
        return deduplicatePoints(raw, NODE_TOLERANCE);
    }

    private List<Vec2d> deduplicatePoints(List<Vec2d> points, double tolerance) {
        List<Vec2d> unique = new ArrayList<>();
        for (Vec2d point : points) {
            boolean exists = false;
            for (Vec2d existing : unique) {
                if (RoadGeometryUtils.pointsNear(existing, point, tolerance)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                unique.add(point.copy());
            }
        }
        return unique;
    }

    private boolean isNearEdgeEndpoint(RoadEdge edge, Vec2d point) {
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points.isEmpty()) {
            return true;
        }
        return RoadGeometryUtils.pointsNear(points.getFirst(), point, NODE_TOLERANCE)
            || RoadGeometryUtils.pointsNear(points.getLast(), point, NODE_TOLERANCE);
    }

    private boolean alreadyConnectedAt(RoadNetwork network, RoadEdge edgeA, RoadEdge edgeB, Vec2d point) {
        RoadNode sharedNode = findExistingNode(network, point);
        if (sharedNode == null) {
            return false;
        }
        String nodeId = sharedNode.getId();
        boolean aConnected = edgeA.getStartNodeId().equals(nodeId) || edgeA.getEndNodeId().equals(nodeId);
        boolean bConnected = edgeB.getStartNodeId().equals(nodeId) || edgeB.getEndNodeId().equals(nodeId);
        return aConnected && bConnected;
    }

    private RoadNode findExistingNode(RoadNetwork network, Vec2d position) {
        for (RoadNode node : network.getNodes().values()) {
            if (RoadGeometryUtils.pointsNear(node.getPosition(), position, NODE_TOLERANCE)) {
                return node;
            }
        }
        return null;
    }

    private boolean splitEdgeAtNode(RoadNetwork network, String edgeId, String nodeId, Vec2d splitPoint) {
        RoadEdge edge = network.getEdge(edgeId);
        if (edge == null) {
            return false;
        }
        if (edge.getStartNodeId().equals(nodeId) || edge.getEndNodeId().equals(nodeId)) {
            return false;
        }

        List<List<Vec2d>> parts = RoadGeometryUtils.splitPolylineAt(
            edge.getCenterlinePoints(),
            splitPoint,
            NODE_TOLERANCE
        );
        if (parts.size() != 2) {
            return false;
        }

        List<Vec2d> firstPart = parts.get(0);
        List<Vec2d> secondPart = parts.get(1);
        if (firstPart.size() < 2 || secondPart.size() < 2) {
            return false;
        }

        String startNodeId = edge.getStartNodeId();
        String endNodeId = edge.getEndNodeId();
        double splitDistance = RoadGeometryUtils.calculatePathLength(firstPart);
        double totalLength = edge.getLength();
        List<RoadEdge.SlopeOverride> slopeOverrides = edge.getSlopeOverrides();

        network.detachEdge(edgeId);
        RoadEdge firstEdge = network.createEdge(startNodeId, nodeId, firstPart);
        RoadEdge secondEdge = network.createEdge(nodeId, endNodeId, secondPart);
        copyProperties(edge, firstEdge);
        copyProperties(edge, secondEdge);
        firstEdge.setSlopeOverrides(splitSlopeOverrides(slopeOverrides, splitDistance, totalLength, true));
        secondEdge.setSlopeOverrides(splitSlopeOverrides(slopeOverrides, splitDistance, totalLength, false));
        return true;
    }

    static List<RoadEdge.SlopeOverride> splitSlopeOverrides(
            List<RoadEdge.SlopeOverride> overrides,
            double splitDistance,
            double totalLength,
            boolean firstPart) {
        if (overrides == null || overrides.isEmpty()) {
            return List.of();
        }

        List<RoadEdge.SlopeOverride> result = new ArrayList<>();
        for (RoadEdge.SlopeOverride override : overrides) {
            double start = override.startDistance;
            double end = override.endDistance;
            if (firstPart) {
                if (end <= 0 || start >= splitDistance) {
                    continue;
                }
                double newStart = Math.max(0, start);
                double newEnd = Math.min(splitDistance, end);
                if (newEnd > newStart) {
                    result.add(new RoadEdge.SlopeOverride(newStart, newEnd, override.maxSlope));
                }
            } else {
                double secondLength = Math.max(0, totalLength - splitDistance);
                if (secondLength <= 0 || end <= splitDistance || start >= totalLength) {
                    continue;
                }
                double newStart = Math.max(splitDistance, start) - splitDistance;
                double newEnd = Math.min(totalLength, end) - splitDistance;
                if (newEnd > newStart) {
                    result.add(new RoadEdge.SlopeOverride(newStart, newEnd, override.maxSlope));
                }
            }
        }
        return result;
    }

    private void copyProperties(RoadEdge source, RoadEdge target) {
        target.setWidth(source.getWidth());
        target.setMaterial(source.getMaterial());
        target.setIncludeSidewalk(source.getIncludeSidewalk());
        target.setSidewalkWidth(source.getSidewalkWidth());
        target.setSidewalkMaterial(source.getSidewalkMaterial());
        target.setStreetlightSpacing(source.getStreetlightSpacing());
        target.setMaxSlope(source.getMaxSlope());
    }
}
