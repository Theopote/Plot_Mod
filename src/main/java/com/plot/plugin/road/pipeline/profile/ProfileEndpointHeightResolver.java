package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.terrain.TerrainSampler;

import java.util.Map;

/**
 * Combines grade-separation / manual overrides with first-pass network node elevations.
 */
public final class ProfileEndpointHeightResolver {
    private final GradeSeparationPolicy gradeSeparation;
    private final NodeTargetHeightResolver nodeTargetHeights;

    public ProfileEndpointHeightResolver(
            GradeSeparationPolicy gradeSeparation,
            NodeTargetHeightResolver nodeTargetHeights) {
        this.gradeSeparation = gradeSeparation;
        this.nodeTargetHeights = nodeTargetHeights;
    }

    public Integer resolve(
            RoadNode node,
            RoadNetwork network,
            RoadEdge edge,
            TerrainSampler terrain,
            boolean applyGradeSeparation,
            Map<String, Integer> networkNodeElevations) {
        Integer forced = gradeSeparation.resolveForcedEndpointHeight(
            node,
            network,
            edge,
            terrain,
            applyGradeSeparation,
            nodeTargetHeights.naturalRoadHeightAtNode(),
            nodeTargetHeights.naturalEdgeHeightAtNode());
        if (forced != null) {
            return forced;
        }
        return lookupNetworkNodeElevation(networkNodeElevations, node);
    }

    private static Integer lookupNetworkNodeElevation(
            Map<String, Integer> networkNodeElevations,
            RoadNode node) {
        if (networkNodeElevations == null || node == null) {
            return null;
        }
        return networkNodeElevations.get(node.getId());
    }
}
