package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.terrain.TerrainSampler;

import java.util.List;

/**
 * Host callbacks for profile / node-elevation strategies (implemented by {@link RoadGeneratorProfileContext}).
 */
public interface ProfileEdgeContext {
    List<PathSegment> samplePath(List<Vec2d> pathPoints);

    ProfileSolveResult solveEdgeProfile(
            List<PathSegment> segments,
            TerrainSampler terrain,
            RoadNetwork network,
            RoadEdge edge,
            RoadNode startNode,
            RoadNode endNode,
            Integer manualStartHeight,
            Integer manualEndHeight);

    int groundHeightAtNode(TerrainSampler terrain, RoadNode node, RoadNetwork network);

    double defaultCrossingClearance();
}
