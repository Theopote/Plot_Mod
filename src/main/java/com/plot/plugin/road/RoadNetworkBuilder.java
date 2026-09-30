package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.core.model.Shape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.crossing.RoadCrossingReconciler;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;

import java.util.List;
import java.util.UUID;

/**
 * 道路网络拓扑构建（认领、路口分类）。
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

    public record AdoptResult(
            List<RoadEdge> edges,
            int junctionCount,
            IntersectionResult intersectionResult) {
        public AdoptResult {
            edges = List.copyOf(edges);
            if (intersectionResult == null) {
                intersectionResult = IntersectionResult.COMPLETE;
            }
        }
    }

    public AdoptResult adoptShape(RoadNetwork network, Shape shape, RoadSystemConfig defaults) {
        List<Vec2d> points = RoadGeometryUtils.extractShapePoints(shape);
        if (points.size() < 2) {
            throw new IllegalArgumentException("Shape must have at least 2 points");
        }

        Vec2d startPoint = points.getFirst();
        Vec2d endPoint = points.getLast();
        List<Vec2d> shapeEndpoints = shape.getEndpoints();
        if (shapeEndpoints != null && shapeEndpoints.size() >= 2
            && !RoadGeometryUtils.pointsNear(startPoint, endPoint, NODE_TOLERANCE)) {
            startPoint = shapeEndpoints.getFirst();
            endPoint = shapeEndpoints.getLast();
        }

        boolean geometricallyClosed = RoadGeometryUtils.pointsNear(startPoint, endPoint, NODE_TOLERANCE);
        RoadNode startNode = network.createNode(startPoint);
        RoadNode endNode = geometricallyClosed ? startNode : network.createNode(endPoint);

        Road road = network.createRoadForAdopt(defaults);
        RoadEdge edge = network.createEdge(startNode.getId(), endNode.getId(), points, road.getId());
        edge.setSourceRoadId(UUID.randomUUID().toString());

        return new AdoptResult(List.of(edge), 0, IntersectionResult.COMPLETE);
    }

    /**
     * 探测几何交叉是否均已注册为 Crossing（不修改 live 拓扑）。
     */
    public IntersectionProbeResult probeIntersectionCompleteness(RoadNetwork network) {
        return RoadCrossingReconciler.probeRegistryCompleteness(network);
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
}
