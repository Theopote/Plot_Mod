package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadJunctionGeometry;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.model.RoadNode;

import java.util.Map;

/**
 * Builds {@link EndpointElevationSnaps} for edge endpoint smoothing at resolved junction elevations.
 */
public final class EndpointElevationSnapResolver {
    private EndpointElevationSnapResolver() {
    }

    /** Minimum transition length in canvas units when {@code unitsPerBlock} is unknown. */
    private static final double MIN_BLEND_RADIUS = 8.0;

    public static double blendRadius(double carriagewayHalfWidthWorldUnits) {
        return Math.max(carriagewayHalfWidthWorldUnits + 2.0, MIN_BLEND_RADIUS);
    }

    /**
     * Blend radius sized to the junction footprint: at least one portal-transition band
     * in world blocks so endpoint Y eases into the resolved node elevation.
     */
    public static double blendRadius(double carriagewayHalfWidthWorldUnits, double unitsPerBlock) {
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        double junctionFootprint = Math.max(
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS * scale,
            carriagewayHalfWidthWorldUnits + 2.0 * scale);
        double transitionBand = RoadCorridorWidth.PORTAL_TRANSITION_CLEAR_BAND_BLOCKS * scale;
        return Math.max(junctionFootprint, transitionBand);
    }

    public static EndpointElevationSnaps resolve(
            RoadNode startNode,
            RoadNode endNode,
            Map<String, Integer> networkNodeElevations,
            double blendRadius) {
        if (networkNodeElevations == null || networkNodeElevations.isEmpty()) {
            return null;
        }
        Integer startElevation = startNode == null ? null : networkNodeElevations.get(startNode.getId());
        Integer endElevation = endNode == null ? null : networkNodeElevations.get(endNode.getId());
        return resolveForEdge(startNode, endNode, startElevation, endElevation, blendRadius);
    }

    /**
     * Snap toward this edge's solved build elevations.
     * Grade-separated nodes store the underpass layer in the network map; overpass
     * edges must pass their elevated endpoint Y here so nearby samples are not
     * pulled down to the underpass.
     */
    public static EndpointElevationSnaps resolveForEdge(
            RoadNode startNode,
            RoadNode endNode,
            Integer startBuildElevation,
            Integer endBuildElevation,
            double blendRadius) {
        return resolveForEdge(
            startNode, endNode, startBuildElevation, endBuildElevation,
            blendRadius, blendRadius, 0.0, 0.0);
    }

    public static EndpointElevationSnaps resolveForEdge(
            RoadNode startNode,
            RoadNode endNode,
            Integer startBuildElevation,
            Integer endBuildElevation,
            double startBlendRadius,
            double endBlendRadius,
            double startCoreRadius,
            double endCoreRadius) {
        EndpointElevationSnap start = snapForElevation(
            startNode, startBuildElevation, startBlendRadius, startCoreRadius);
        EndpointElevationSnap end = snapForElevation(
            endNode, endBuildElevation, endBlendRadius, endCoreRadius);
        if (start == null && end == null) {
            return null;
        }
        return new EndpointElevationSnaps(start, end);
    }

    private static EndpointElevationSnap snapForElevation(
            RoadNode node,
            Integer elevation,
            double blendRadius) {
        return snapForElevation(node, elevation, blendRadius, 0.0);
    }

    private static EndpointElevationSnap snapForElevation(
            RoadNode node,
            Integer elevation,
            double blendRadius,
            double coreRadius) {
        if (node == null || elevation == null) {
            return null;
        }
        return new EndpointElevationSnap(node.getPosition(), elevation, blendRadius, coreRadius);
    }
}
