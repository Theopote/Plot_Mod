package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.profile.GradeSeparationPolicy;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadGradeSeparationEvaluatorTest {

    @Test
    void prefersLowerGradeAlternativeWhenOneRequiresLargeLift() {
        SimpleCrossFixture fixture = SimpleCrossFixture.create();
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setDefaultCrossingClearance(4.0);
        fixture.roadA().setMaxSlope(8f);
        fixture.roadB().setMaxSlope(8f);

        GradeSeparationPolicy.NaturalRoadHeightAtNode naturalRoadHeight =
            (node, network, terrainSampler, roadId) -> fixture.roadA().getId().equals(roadId) ? 80 : 64;

        RoadGradeSeparationEvaluation evaluation = RoadGradeSeparationEvaluator.evaluate(
            fixture.junction(),
            fixture.network(),
            config,
            new FlatTerrainSampler(64),
            naturalRoadHeight);

        assertNotNull(evaluation);
        assertEquals(fixture.roadA().getId(), evaluation.recommendedElevatedRoadId());
        RoadGradeSeparationAlternative aOver = evaluation.alternativeFor(fixture.roadA().getId());
        RoadGradeSeparationAlternative bOver = evaluation.alternativeFor(fixture.roadB().getId());
        assertNotNull(aOver);
        assertNotNull(bOver);
        assertTrue(aOver.score() <= bOver.score());
        assertFalse(aOver.exceedsSlopeLimit());
    }

    @Test
    void lockedChoiceSteepWhenManualSelectionExceedsSlope() {
        SimpleCrossFixture fixture = SimpleCrossFixture.create();
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setDefaultCrossingClearance(6.0);
        fixture.roadA().setMaxSlope(5f);
        fixture.roadB().setMaxSlope(20f);

        GradeSeparationPolicy.NaturalRoadHeightAtNode naturalRoadHeight =
            (node, network, terrainSampler, roadId) -> fixture.roadA().getId().equals(roadId) ? 64 : 80;

        fixture.junction().setGradeSeparated(true);
        fixture.junction().setElevatedRoadId(fixture.roadA().getId());

        RoadGradeSeparationEvaluation evaluation = RoadGradeSeparationEvaluator.evaluate(
            fixture.junction(),
            fixture.network(),
            config,
            new FlatTerrainSampler(64),
            naturalRoadHeight);

        assertTrue(evaluation.isLockedChoiceSteep(fixture.junction()));
        assertEquals(fixture.roadB().getId(), evaluation.recommendedIfDifferentFromLock(fixture.junction()));
    }

    private record SimpleCrossFixture(
            RoadNetwork network,
            RoadNode junction,
            Road roadA,
            Road roadB) {
        static SimpleCrossFixture create() {
            RoadNetwork network = new RoadNetwork();
            RoadNode junction = network.createNode(new Vec2d(0, 0));
            RoadNode north = network.createNode(new Vec2d(0, 40));
            RoadNode south = network.createNode(new Vec2d(0, -40));
            RoadNode east = network.createNode(new Vec2d(40, 0));
            RoadNode west = network.createNode(new Vec2d(-40, 0));
            Road roadA = network.createRoad("road-a");
            Road roadB = network.createRoad("road-b");
            network.createEdge(
                junction.getId(), north.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 40)), roadA.getId());
            network.createEdge(
                junction.getId(), south.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, -40)), roadA.getId());
            network.createEdge(
                junction.getId(), east.getId(), List.of(new Vec2d(0, 0), new Vec2d(40, 0)), roadB.getId());
            network.createEdge(
                junction.getId(), west.getId(), List.of(new Vec2d(0, 0), new Vec2d(-40, 0)), roadB.getId());
            return new SimpleCrossFixture(network, junction, roadA, roadB);
        }
    }
}
