package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        @Override
        public com.plot.plugin.config.RoadSystemConfig roadConfig() {
            return null;
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

    @Test
    void stackGradeSeparatedUpperArmGetsForcedClearanceHeight() {
        RoadNetwork network = new RoadNetwork();
        var road = network.createRoad("loop");
        RoadNode junction = network.createNode(new Vec2d(5, 5));
        junction.setManualElevation(64.0);
        junction.setStackGradeSeparated(true);
        junction.setStackCrossingClearance(4.0);
        RoadNode farA = network.createNode(new Vec2d(0, 0));
        RoadNode farB = network.createNode(new Vec2d(10, 0));
        RoadEdge lower = network.createEdge(
            junction.getId(), farA.getId(),
            List.of(new Vec2d(5, 5), new Vec2d(0, 0)), road.getId());
        RoadEdge upper = network.createEdge(
            junction.getId(), farB.getId(),
            List.of(new Vec2d(5, 5), new Vec2d(10, 0)), road.getId());
        junction.addStackLowerEdgeId(lower.getId());
        junction.addStackUpperEdgeId(upper.getId());

        GradeSeparationPolicy policy = new GradeSeparationPolicy(STUB_CONTEXT);
        TerrainSampler terrain = new FlatTerrainSampler(64);

        assertEquals(
            68,
            policy.resolveElevatedCrossingHeight(
                upper, junction, network, terrain,
                (n, net, t, roadId) -> 64,
                (edge, node, net, t) -> 64));
        assertNull(
            policy.resolveElevatedCrossingHeight(
                lower, junction, network, terrain,
                (n, net, t, roadId) -> 64,
                (edge, node, net, t) -> 64));
        assertTrue(policy.shouldExcludeElevatedRoadFromNaturalConsensus(
            junction, upper, network, terrain, (n, net, t, roadId) -> 64));
        assertFalse(policy.shouldExcludeElevatedRoadFromNaturalConsensus(
            junction, lower, network, terrain, (n, net, t, roadId) -> 64));
    }
}
