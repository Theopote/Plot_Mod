package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadJunctionGeometry;
import com.plot.plugin.road.model.RoadNode;

import java.util.Map;

/**
 * Builds {@link EndpointElevationSnaps} for edge endpoint smoothing at resolved junction elevations.
 */
public final class EndpointElevationSnapResolver {
    private EndpointElevationSnapResolver() {
    }

    public static double blendRadius(double carriagewayHalfWidthWorldUnits) {
        return Math.max(carriagewayHalfWidthWorldUnits + 2.0, RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS);
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
        EndpointElevationSnap start = snapForElevation(startNode, startBuildElevation, blendRadius);
        EndpointElevationSnap end = snapForElevation(endNode, endBuildElevation, blendRadius);
        if (start == null && end == null) {
            return null;
        }
        return new EndpointElevationSnaps(start, end);
    }

    private static EndpointElevationSnap snapForElevation(
            RoadNode node,
            Integer elevation,
            double blendRadius) {
        if (node == null || elevation == null) {
            return null;
        }
        return new EndpointElevationSnap(node.getPosition(), elevation, blendRadius);
    }
}
