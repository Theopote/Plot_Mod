package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.terrain.FlatTerrainSampler;
import com.plot.plugin.road.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GradeSeparationPolicyTest {

    private static final ProfileEdgeContext STUB_CONTEXT = new ProfileEdgeContext() {
        @Override
        public List<PathSegment> samplePath(List<Vec2d> pathPoints) {
            return List.of();
        }

        @Override
        public ProfileSolveResult solveEdgeProfile(
                List<PathSegment> segments,
                TerrainSampler terrain,
                RoadNetwork network,
                RoadEdge edge,
                RoadNode startNode,
                RoadNode endNode,
                Integer manualStartHeight,
                Integer manualEndHeight) {
            return ProfileSolveResult.empty();
        }

        @Override
        public int groundHeightAtNode(TerrainSampler terrain, RoadNode node, RoadNetwork network) {
            return TerrainSampler.DEFAULT_SEA_LEVEL;
        }

        @Override
        public double defaultCrossingClearance() {
            return 5.0;
        }
    };

    @Test
    void returnsManualElevatedRoadIdWithoutSamplingTerrain() {
        RoadNode node = new RoadNode(new Vec2d(0, 0));
        node.setGradeSeparated(true);
        node.setElevatedRoadId("road-a");

        GradeSeparationPolicy policy = new GradeSeparationPolicy(STUB_CONTEXT);
        TerrainSampler terrain = new FlatTerrainSampler(64);
        RoadNetwork network = new RoadNetwork();

        assertEquals(
            "road-a",
            policy.resolveElevatedRoadId(node, network, terrain, (n, net, t, roadId) -> 0));
    }

    @Test
    void nonGradeSeparatedNodeHasNoElevatedRoad() {
        RoadNode node = new RoadNode(new Vec2d(0, 0));
        GradeSeparationPolicy policy = new GradeSeparationPolicy(STUB_CONTEXT);

        assertNull(policy.resolveElevatedRoadId(
            node, null, null, (n, network, terrain, roadId) -> 64));
    }
}
