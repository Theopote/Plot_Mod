package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.terrain.TerrainSampler;

import java.util.List;
import java.util.Map;

/**
 * Resolves endpoint overrides and delegates longitudinal profile solving to {@link RoadProfileSolver}.
 */
public final class RoadProfileSolveCoordinator {
    private final RoadGeneratorProfileContext profileContext;
    private final ProfileEndpointHeightResolver profileEndpointHeightResolver;

    public RoadProfileSolveCoordinator(
            RoadGeneratorProfileContext profileContext,
            ProfileEndpointHeightResolver profileEndpointHeightResolver) {
        this.profileContext = profileContext;
        this.profileEndpointHeightResolver = profileEndpointHeightResolver;
    }

    public ProfileSolveResult solveStandalone(List<PathSegment> segments, TerrainSampler terrain) {
        return profileContext.solveStandalone(segments, terrain);
    }

    public ProfileSolveResult solveWithManualElevation(
            List<PathSegment> segments,
            TerrainSampler terrain,
            int manualRoadElevation) {
        return profileContext.solveWithManualElevation(segments, terrain, manualRoadElevation);
    }

    public ProfileSolveResult solveForEdge(
            List<PathSegment> segments,
            TerrainSampler terrain,
            RoadNetwork network,
            RoadEdge edge,
            RoadNode startNode,
            RoadNode endNode,
            boolean applyGradeSeparation,
            Map<String, Integer> networkNodeElevations) {
        Integer manualStartHeight = profileEndpointHeightResolver.resolve(
            startNode, network, edge, terrain, applyGradeSeparation, networkNodeElevations);
        Integer manualEndHeight = profileEndpointHeightResolver.resolve(
            endNode, network, edge, terrain, applyGradeSeparation, networkNodeElevations);
        return profileContext.solveEdgeProfile(
            segments,
            terrain,
            network,
            edge,
            startNode,
            endNode,
            manualStartHeight,
            manualEndHeight);
    }
}
