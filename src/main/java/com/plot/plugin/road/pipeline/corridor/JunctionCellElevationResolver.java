package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.pipeline.profile.EndpointElevationSnapResolver;
import com.plot.plugin.road.solid.RoadGenerationResult;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Per-cell junction surface Y: inverse-distance blend of connected edge build profiles,
 * smoothed toward the network consensus elevation near the node center.
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
        if (cell == null || nodeCenter == null) {
            return junctionY;
        }
        if (edges == null || edges.isEmpty() || edgeResults == null || edgeResults.isEmpty()) {
            return junctionY;
        }
        double scale = unitsPerBlock > EPSILON ? unitsPerBlock : 1.0;
        double effectiveBlend = blendRadius > EPSILON
            ? blendRadius
            : EndpointElevationSnapResolver.blendRadius(
                maxHalfWidthWorldUnits(edges, network, config, scale),
                scale);

        WeightedElevation profile = weightedProfileElevation(
            cell, nodeId, edges, network, edgeResults, scale);
        if (profile.weight <= EPSILON) {
            return junctionY;
        }
        int profileY = (int) Math.round(profile.elevationSum / profile.weight);

        double dist = cell.distance(nodeCenter);
        if (dist >= effectiveBlend - EPSILON) {
            return profileY;
        }
        double t = dist / effectiveBlend;
        double blend = smoothStep(t);
        return (int) Math.round(junctionY * (1.0 - blend) + profileY * blend);
    }

    public static double blendRadiusForJunction(
            List<RoadEdge> edges,
            RoadNetwork network,
            RoadSystemConfig config,
            double unitsPerBlock) {
        double scale = unitsPerBlock > EPSILON ? unitsPerBlock : 1.0;
        return EndpointElevationSnapResolver.blendRadius(
            maxHalfWidthWorldUnits(edges, network, config, scale),
            scale);
    }

    private static double maxHalfWidthWorldUnits(
            List<RoadEdge> edges,
            RoadNetwork network,
            RoadSystemConfig config,
            double scale) {
        double maxHalf = 0.0;
        for (RoadEdge edge : edges) {
            maxHalf = Math.max(
                maxHalf,
                RoadModelUtils.getEffectiveWidth(network, edge, config) / 2.0 * scale);
        }
        return maxHalf;
    }

    private record WeightedElevation(double elevationSum, double weight) {
    }

    record StationSample(double worldStation, double projectionDistance) {
    }

    private static WeightedElevation weightedProfileElevation(
            Vec2d cell,
            String nodeId,
            List<RoadEdge> edges,
            RoadNetwork network,
            Map<String, RoadGenerationResult> edgeResults,
            double scale) {
        Function<RoadEdge, List<Vec2d>> centerlineResolver =
            RoadJunctionCenterlineResolver.forNetwork(network);
        double elevationSum = 0.0;
        double weight = 0.0;
        for (RoadEdge edge : edges) {
            RoadGenerationResult result = edgeResults.get(edge.getId());
            if (result == null) {
                continue;
            }
            StationSample sample = sampleStationAt(cell, nodeId, edge, centerlineResolver, scale);
            if (sample == null) {
                continue;
            }
            int y = elevationAtStation(result, sample.worldStation());
            if (y < 0) {
                continue;
            }
            double w = 1.0 / (sample.projectionDistance() + scale);
            elevationSum += w * y;
            weight += w;
        }
        return new WeightedElevation(elevationSum, weight);
    }

    static StationSample sampleStationAt(
            Vec2d cell,
            String nodeId,
            RoadEdge edge,
            Function<RoadEdge, List<Vec2d>> centerlineResolver,
            double scale) {
        List<Vec2d> centerline = centerlineResolver.apply(edge);
        if (centerline == null || centerline.size() < 2) {
            return null;
        }
        boolean fromStart = edge.getStartNodeId().equals(nodeId);
        if (!fromStart && !edge.getEndNodeId().equals(nodeId)) {
            return null;
        }
        int segmentCount = centerline.size() - 1;
        StationSample best = null;
        double accumulated = 0.0;
        for (int i = 0; i < segmentCount; i++) {
            Vec2d segStart;
            Vec2d segEnd;
            if (fromStart) {
                segStart = centerline.get(i);
                segEnd = centerline.get(i + 1);
            } else {
                segStart = centerline.get(centerline.size() - 1 - i);
                segEnd = centerline.get(centerline.size() - 2 - i);
            }
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
