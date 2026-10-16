package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.solid.RoadGenerationResult;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Junction plaza surface Y. At-grade cells blend approach profile elevations toward the
 * unified {@code junctionY} near the node center; grade-separated underpass plazas stay flat.
 */
public final class JunctionCellElevationResolver {
    private static final double EPSILON = 1e-9;

    private JunctionCellElevationResolver() {
    }

    public static int resolve(
            Vec2d cell,
            Vec2d nodeCenter,
            String nodeId,
            int junctionY,
            List<RoadEdge> edges,
            RoadNetwork network,
            RoadSystemConfig config,
            Map<String, RoadGenerationResult> edgeResults,
            double unitsPerBlock,
            double blendRadius) {
        if (cell == null || nodeCenter == null || blendRadius <= EPSILON
                || edges == null || edges.isEmpty() || edgeResults == null || edgeResults.isEmpty()) {
            return junctionY;
        }
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        Function<RoadEdge, List<Vec2d>> centerlineResolver = RoadJunctionCenterlineResolver.forNetwork(network);

        double weightedSum = 0.0;
        double weightTotal = 0.0;
        for (RoadEdge edge : edges) {
            RoadGenerationResult result = edgeResults.get(edge.getId());
            if (result == null) {
                continue;
            }
            StationSample sample = sampleStationAt(cell, nodeId, edge, centerlineResolver, scale);
            if (sample == null) {
                continue;
            }
            int profileY = elevationAtStation(result, sample.worldStation());
            if (profileY < 0) {
                continue;
            }
            double weight = 1.0 / (sample.projectionDistance() / scale + 1.0);
            weightedSum += profileY * weight;
            weightTotal += weight;
        }
        if (weightTotal <= EPSILON) {
            return junctionY;
        }
        int profileBlendY = (int) Math.round(weightedSum / weightTotal);
        double distanceFromCenter = cell.distance(nodeCenter) / scale;
        if (distanceFromCenter >= blendRadius) {
            return profileBlendY;
        }
        double t = smoothStep(1.0 - distanceFromCenter / blendRadius);
        return (int) Math.round(junctionY * t + profileBlendY * (1.0 - t));
    }

    record StationSample(double worldStation, double projectionDistance) {
    }

    /**
     * Projects {@code cell} onto the edge centerline and returns world-station from the
     * geometric start of the edge (same axis as {@link BuildHeightProfile} / profileDistances).
     * <p>
     * Must not reverse-walk from the junction: when the junction is the edge end node,
     * approach-local station would read the far-end profile elevation and skew plaza Y.
     */
    static StationSample sampleStationAt(
            Vec2d cell,
            String nodeId,
            RoadEdge edge,
            Function<RoadEdge, List<Vec2d>> centerlineResolver,
            double scale) {
        List<Vec2d> centerline = centerlineResolver.apply(edge);
        if (centerline == null || centerline.size() < 2 || edge == null) {
            return null;
        }
        if (!edge.getStartNodeId().equals(nodeId) && !edge.getEndNodeId().equals(nodeId)) {
            return null;
        }
        int segmentCount = centerline.size() - 1;
        StationSample best = null;
        double accumulated = 0.0;
        for (int i = 0; i < segmentCount; i++) {
            Vec2d segStart = centerline.get(i);
            Vec2d segEnd = centerline.get(i + 1);
            double segLen = segStart.distance(segEnd);
            Vec2d projected = RoadGeometryUtils.projectPointOnSegment(segStart, segEnd, cell);
            double perpDist = projected.distance(cell);
            double along = segStart.distance(projected);
            double worldStation = (accumulated + along) / scale;
            if (best == null || perpDist < best.projectionDistance()) {
                best = new StationSample(worldStation, perpDist);
            }
            accumulated += segLen;
        }
        return best;
    }

    static int elevationAtStation(RoadGenerationResult result, double worldStation) {
        if (result == null) {
            return -1;
        }
        if (result.buildProfile != null && result.buildProfile.isActive()) {
            return result.buildProfile.elevationAtWorldStation(worldStation);
        }
        List<Double> distances = result.profileDistances;
        List<Integer> heights = result.profileBuildHeights;
        if (distances == null || heights == null || distances.isEmpty() || heights.isEmpty()) {
            return -1;
        }
        if (worldStation <= distances.getFirst() + EPSILON) {
            return heights.getFirst();
        }
        if (worldStation >= distances.getLast() - EPSILON) {
            return heights.getLast();
        }
        for (int i = 0; i < distances.size() - 1; i++) {
            double d0 = distances.get(i);
            double d1 = distances.get(i + 1);
            if (worldStation >= d0 - EPSILON && worldStation <= d1 + EPSILON) {
                double t = d1 - d0 > EPSILON ? (worldStation - d0) / (d1 - d0) : 0.0;
                return (int) Math.round(heights.get(i) * (1.0 - t) + heights.get(i + 1) * t);
            }
        }
        return heights.getLast();
    }

    private static double smoothStep(double t) {
        if (t <= 0.0) {
            return 0.0;
        }
        if (t >= 1.0) {
            return 1.0;
        }
        return t * t * (3.0 - 2.0 * t);
    }
}
