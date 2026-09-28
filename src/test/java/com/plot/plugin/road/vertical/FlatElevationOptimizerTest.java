package com.plot.plugin.road.vertical;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlatElevationOptimizerTest {
    private static final RoadSystemConfig CONFIG = new RoadSystemConfig("test");

    @Test
    void flatTerrainPrefersMatchingElevation() {
        RoadNetwork network = straightRoad(80.0);
        Road road = network.getRoads().values().iterator().next();
        FlatElevationRecommendation recommendation = FlatElevationOptimizer.evaluate(
            network, road, new FlatTerrainSampler(64), CONFIG);

        assertTrue(recommendation.hasRecommendation());
        assertEquals(64, recommendation.best().elevation());
        assertEquals(0, recommendation.best().estimatedChangedBlocks());
    }

    @Test
    void rampTerrainPrefersBalancedElevationOverExtremes() {
        RoadNetwork network = straightRoad(100.0);
        Road road = network.getRoads().values().iterator().next();
        TerrainSampler ramp = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return (int) Math.round(60.0 + (planPoint.x / 100.0) * 20.0);
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return y <= sampleSurfaceY(new Vec2d(worldX, worldZ));
            }
        };

        FlatElevationRecommendation recommendation = FlatElevationOptimizer.evaluate(
            network, road, ramp, CONFIG);
        assertTrue(recommendation.hasRecommendation());
        assertTrue(recommendation.best().elevation() >= 66 && recommendation.best().elevation() <= 74);
        assertTrue(recommendation.best().estimatedChangedBlocks() >= 0);
        assertTrue(recommendation.alternatives().size() >= 1);
    }

    @Test
    void stageBRefinesMetricsWithCompiledProfile() {
        RoadNetwork network = straightRoad(100.0);
        Road road = network.getRoads().values().iterator().next();
        String endNodeId = network.getEdges().values().iterator().next().getEndNodeId();
        road.setFlatVerticalIntent(new FlatVerticalIntent(64.0, Map.of(endNodeId, 70.0)));
        road.setVerticalMode(RoadVerticalMode.FLAT);

        TerrainSampler terrain = new FlatTerrainSampler(64);
        double maxGrade = road.getEffectiveMaxSlope(CONFIG);
        FlatVerticalIntent intent = road.getFlatVerticalIntent().copy();
        var costConfig = com.plot.plugin.road.RoadConstructionEvaluator.RoadConstructionCostConfig.from(CONFIG);
        double roadLength = com.plot.plugin.road.station.RoadStationing.canonicalLength(network, road);

        FlatElevationCandidate stageB = FlatElevationRefinementEvaluator.refine(
            network, road, terrain, CONFIG, 64, maxGrade, intent, costConfig, roadLength, 0.0);

        assertTrue(stageB.feasible());
        assertTrue(stageB.estimatedChangedBlocks() > 0);
    }

    @Test
    void junctionConstraintPrefersSharedElevation() {
        RoadNetwork network = straightRoad(100.0);
        Road road = network.getRoads().values().iterator().next();
        String endNodeId = network.getEdges().values().iterator().next().getEndNodeId();
        road.setFlatVerticalIntent(new FlatVerticalIntent(64.0, Map.of(endNodeId, 74.0)));
        road.setVerticalMode(RoadVerticalMode.FLAT);

        FlatElevationRecommendation recommendation = FlatElevationOptimizer.evaluate(
            network, road, new FlatTerrainSampler(64), CONFIG);
        assertTrue(recommendation.hasRecommendation());
        assertTrue(recommendation.best().elevation() >= 64);
        assertTrue(recommendation.best().elevation() <= 74);
    }

    private static RoadNetwork straightRoad(double length) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(length, 0));
        network.createEdge(start.getId(), end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(length, 0)), road.getId());
        return network;
    }
}
