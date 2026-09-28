package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.test.world.IdentityCoordinateService;
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
        fixture.north().setManualElevation(72.0);
        fixture.south().setManualElevation(72.0);

        RoadGenerator generator = testGenerator(config);
        RoadGradeSeparationEvaluation evaluation = generator.evaluateGradeSeparation(
            fixture.junction(),
            fixture.network(),
            new FlatTerrainSampler(64));

        assertNotNull(evaluation);
        assertEquals(fixture.roadA().getId(), evaluation.recommendedElevatedRoadId());
        RoadGradeSeparationAlternative aOver = evaluation.alternativeFor(fixture.roadA().getId());
        RoadGradeSeparationAlternative bOver = evaluation.alternativeFor(fixture.roadB().getId());
        assertNotNull(aOver);
        assertNotNull(bOver);
        assertTrue(aOver.score() < bOver.score());
        assertTrue(bOver.exceedsSlopeLimit() || bOver.estimatedMaxGradePercent() > aOver.estimatedMaxGradePercent());
    }

    @Test
    void lockedChoiceSteepWhenManualSelectionExceedsSlope() {
        SimpleCrossFixture fixture = SimpleCrossFixture.create();
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setDefaultCrossingClearance(6.0);
        fixture.roadA().setMaxSlope(5f);
        fixture.roadB().setMaxSlope(20f);
        fixture.east().setManualElevation(80.0);
        fixture.west().setManualElevation(80.0);

        fixture.junction().setGradeSeparated(true);
        fixture.junction().setElevatedRoadId(fixture.roadA().getId());

        RoadGenerator generator = testGenerator(config);
        RoadGradeSeparationEvaluation evaluation = generator.evaluateGradeSeparation(
            fixture.junction(),
            fixture.network(),
            new FlatTerrainSampler(64));

        assertTrue(evaluation.isLockedChoiceSteep(fixture.junction()));
        assertEquals(fixture.roadB().getId(), evaluation.recommendedIfDifferentFromLock(fixture.junction()));
    }

    private static RoadGenerator testGenerator(RoadSystemConfig config) {
        return new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
    }

    private record SimpleCrossFixture(
            RoadNetwork network,
            RoadNode junction,
            RoadNode north,
            RoadNode south,
            RoadNode east,
            RoadNode west,
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
            return new SimpleCrossFixture(network, junction, north, south, east, west, roadA, roadB);
        }
    }
}
