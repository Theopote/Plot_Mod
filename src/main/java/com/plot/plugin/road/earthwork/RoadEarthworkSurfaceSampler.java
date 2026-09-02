package com.plot.plugin.road.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.terrain.TerrainSampler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 为土方插件提供道路设计纵断面采样。
 */
public final class RoadEarthworkSurfaceSampler {

    public record EdgeRef(String id, String label) {
    }

    private RoadEarthworkSurfaceSampler() {
    }

    public static List<EdgeRef> listEdgeRefs(RoadNetwork network) {
        if (network == null || network.getEdges().isEmpty()) {
            return List.of();
        }
        List<EdgeRef> refs = new ArrayList<>();
        for (RoadEdge edge : network.getEdges().values()) {
            if (edge == null) {
                continue;
            }
            Road road = network.getRoadForEdge(edge);
            String roadName = road != null && road.getName() != null && !road.getName().isBlank()
                ? road.getName()
                : "Road";
            String label = roadName + " · " + edge.getId().substring(0, Math.min(8, edge.getId().length()));
            refs.add(new EdgeRef(edge.getId(), label));
        }
        refs.sort(Comparator.comparing(EdgeRef::label, String.CASE_INSENSITIVE_ORDER));
        return refs;
    }

    public static Integer sampleDesignSurfaceY(RoadNetwork network, String edgeId, Vec2d planPoint) {
        if (network == null || edgeId == null || edgeId.isBlank() || planPoint == null) {
            return null;
        }
        RoadEdge edge = network.getEdge(edgeId);
        if (edge == null) {
            return null;
        }
        List<Vec2d> centerline = RoadPlanGeometry.resolveEdgeCenterline(network, edge);
        if (centerline.size() < 2) {
            return null;
        }
        double localDistance = RoadGeometryUtils.distanceAlongPolyline(centerline, planPoint);
        if (localDistance < 0.0) {
            return null;
        }
        List<PathSegment> segments = toPathSegments(centerline);
        DesignElevationSource designElevation = DesignElevationSource.forEdge(network, edge, segments);
        if (designElevation.isActive()) {
            return designElevation.elevationAtLocalDistance(localDistance);
        }
        return interpolateNodeElevations(network, edge, centerline, localDistance);
    }

    private static List<PathSegment> toPathSegments(List<Vec2d> centerline) {
        List<PathSegment> segments = new ArrayList<>();
        for (int i = 0; i < centerline.size() - 1; i++) {
            Vec2d start = centerline.get(i);
            Vec2d end = centerline.get(i + 1);
            if (start == null || end == null) {
                continue;
            }
            segments.add(new PathSegment(start, end));
        }
        return segments;
    }

    private static Integer interpolateNodeElevations(
            RoadNetwork network,
            RoadEdge edge,
            List<Vec2d> centerline,
            double localDistance) {
        RoadNode start = network.getNode(edge.getStartNodeId());
        RoadNode end = network.getNode(edge.getEndNodeId());
        if (start == null || end == null) {
            return null;
        }
        int startY = resolveNodeElevation(start);
        int endY = resolveNodeElevation(end);
        double totalLength = RoadGeometryUtils.calculatePathLength(centerline);
        if (totalLength <= 1e-6) {
            return startY;
        }
        double ratio = Math.max(0.0, Math.min(1.0, localDistance / totalLength));
        return (int) Math.round(startY + ratio * (endY - startY));
    }

    private static int resolveNodeElevation(RoadNode node) {
        if (node.getManualElevation() != null) {
            return node.getManualElevation().intValue();
        }
        return TerrainSampler.DEFAULT_SEA_LEVEL;
    }
}
