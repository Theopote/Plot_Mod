package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.core.terrain.TerrainSampler;

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

    RoadSystemConfig roadConfig();

    /** 边中心线长度（方块），供路口坡度可行域计算。 */
    default double edgeLengthBlocks(RoadNetwork network, RoadEdge edge) {
        if (network == null || edge == null) {
            return 0.0;
        }
        List<PathSegment> segments = samplePath(RoadPlanGeometry.resolveEdgeCenterline(network, edge));
        if (segments.isEmpty()) {
            return 0.0;
        }
        double canvasUnits = segments.stream().mapToDouble(segment -> segment.distance).sum();
        double unitsPerBlock = estimateCanvasUnitsPerBlock(segments);
        return unitsPerBlock > 0.0 ? canvasUnits / unitsPerBlock : 0.0;
    }

    default double estimateCanvasUnitsPerBlock(List<PathSegment> segments) {
        return 1.0;
    }

    default com.plot.plugin.road.RoadGradeSeparationEvaluation evaluateGradeSeparation(
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain) {
        return null;
    }
}
