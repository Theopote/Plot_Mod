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

        EndpointElevationSnap start = snapForNode(startNode, networkNodeElevations, blendRadius);
        EndpointElevationSnap end = snapForNode(endNode, networkNodeElevations, blendRadius);
        if (start == null && end == null) {
            return null;
        }
        return new EndpointElevationSnaps(start, end);
    }

    private static EndpointElevationSnap snapForNode(
            RoadNode node,
            Map<String, Integer> networkNodeElevations,
            double blendRadius) {
        if (node == null) {
            return null;
        }
        Integer elevation = networkNodeElevations.get(node.getId());
        if (elevation == null) {
            return null;
        }
        return new EndpointElevationSnap(node.getPosition(), elevation, blendRadius);
    }
}
