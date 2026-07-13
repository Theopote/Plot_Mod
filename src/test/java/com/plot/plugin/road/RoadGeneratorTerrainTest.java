package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.terrain.FlatTerrainSampler;
import com.plot.plugin.road.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadGeneratorTerrainTest {

    @Test
    void targetHeightAtNodeUsesFlatTerrainElevation() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        RoadGenerator generator = new RoadGenerator(config, null);
        TerrainSampler terrain = new FlatTerrainSampler(72);

        RoadNetwork network = new RoadNetwork();
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(20, 0));
        var edge = network.createEdge(start.getId(), end.getId(), List.of(new Vec2d(0, 0), new Vec2d(20, 0)));

        assertEquals(72, generator.getTargetHeightAtNode(edge, start, network, terrain));
        assertEquals(72, generator.getTargetHeightAtNode(edge, end, network, terrain));
    }

    @Test
    void manualElevationOverridesTerrainSampling() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        RoadGenerator generator = new RoadGenerator(config, null);
        TerrainSampler terrain = new FlatTerrainSampler(64);

        RoadNetwork network = new RoadNetwork();
        RoadNode start = network.createNode(new Vec2d(0, 0));
        start.setManualElevation(88.0);
        RoadNode end = network.createNode(new Vec2d(15, 0));
        var edge = network.createEdge(start.getId(), end.getId(), List.of(new Vec2d(0, 0), new Vec2d(15, 0)));

        assertEquals(88, generator.getTargetHeightAtNode(edge, start, network, terrain));
    }

    @Test
    void junctionTargetHeightAveragesConnectedEdgesOnFlatTerrain() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        RoadGenerator generator = new RoadGenerator(config, null);
        TerrainSampler terrain = new FlatTerrainSampler(70);

        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 12));
        RoadNode east = network.createNode(new Vec2d(12, 0));
        RoadNode west = network.createNode(new Vec2d(-12, 0));

        network.createEdge(junction.getId(), north.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 12)));
        network.createEdge(junction.getId(), east.getId(), List.of(new Vec2d(0, 0), new Vec2d(12, 0)));
        network.createEdge(junction.getId(), west.getId(), List.of(new Vec2d(0, 0), new Vec2d(-12, 0)));

        assertEquals(70, generator.computeJunctionTargetHeight(junction, network, terrain));
    }

    @Test
    void generateFromPathPointsPlacesRoadAtFlatTerrainElevation() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setIncludeSidewalk(false);
        RoadGenerator generator = new RoadGenerator(config, null);

        RoadGenerationResult result = generator.generateFromPathPoints(
            List.of(new Vec2d(0, 0), new Vec2d(8, 0)),
            new FlatTerrainSampler(64));

        assertFalse(result.roadBlocks.isEmpty());
        assertTrue(result.roadBlocks.stream().allMatch(pos -> pos.getY() == 64));
        assertTrue(result.pathLength > 0);
    }

    @Test
    void targetHeightDoesNotFollowSteepTerrainFully() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setMaxSlope(10.0f);
        RoadGenerator generator = new RoadGenerator(config, null);
        TerrainSampler risingTerrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return 64 + (int) Math.round(planPoint.x);
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return true;
            }
        };

        RoadNetwork network = new RoadNetwork();
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(100, 0));
        var edge = network.createEdge(start.getId(), end.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)));

        int endHeight = generator.getTargetHeightAtNode(edge, end, network, risingTerrain);
        int naturalGround = risingTerrain.sampleSurfaceY(end.getPosition());
        assertTrue(endHeight < naturalGround);
        assertTrue(endHeight >= risingTerrain.sampleSurfaceY(start.getPosition()));
    }

    @Test
    void junctionGeneratorUsesTerrainSamplerWithoutWorld() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        RoadGenerator generator = new RoadGenerator(config, null);
        RoadJunctionGenerator junctionGenerator = new RoadJunctionGenerator(generator);
        TerrainSampler terrain = new FlatTerrainSampler(65);

        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 10));
        RoadNode east = network.createNode(new Vec2d(10, 0));
        RoadNode west = network.createNode(new Vec2d(-10, 0));

        network.createEdge(junction.getId(), north.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 10)));
        network.createEdge(junction.getId(), east.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        network.createEdge(junction.getId(), west.getId(), List.of(new Vec2d(0, 0), new Vec2d(-10, 0)));

        RoadJunctionGenerator.JunctionBlocks blocks = junctionGenerator.generateJunction(junction, network, terrain);

        assertFalse(blocks.isEmpty());
        assertTrue(blocks.getSolids().primitives().stream().allMatch(p -> p.elevation() == 65));
    }

    @Test
    void gradeSeparationElevatesCrossingRoadByClearance() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setDefaultCrossingClearance(3.0);
        RoadGenerator generator = new RoadGenerator(config, null);
        TerrainSampler terrain = new FlatTerrainSampler(70);

        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 12));
        RoadNode south = network.createNode(new Vec2d(0, -12));
        RoadNode east = network.createNode(new Vec2d(12, 0));
        RoadNode west = network.createNode(new Vec2d(-12, 0));

        Road roadA = network.createRoad("road-a");
        roadA.setName("Main");
        Road roadB = network.createRoad("road-b");
        roadB.setName("Cross");

        var northEdge = network.createEdge(
            junction.getId(), north.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 12)), roadA.getId());
        network.createEdge(
            junction.getId(), south.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, -12)), roadA.getId());
        var eastEdge = network.createEdge(
            junction.getId(), east.getId(), List.of(new Vec2d(0, 0), new Vec2d(12, 0)), roadB.getId());
        network.createEdge(
            junction.getId(), west.getId(), List.of(new Vec2d(0, 0), new Vec2d(-12, 0)), roadB.getId());

        assertTrue(network.setNodeGradeSeparation(junction.getId(), true, roadB.getId(), 3.0));

        int underpassHeight = generator.getTargetHeightAtNode(northEdge, junction, network, terrain);
        int elevatedHeight = generator.getTargetHeightAtNode(eastEdge, junction, network, terrain);

        assertEquals(70, underpassHeight);
        assertEquals(73, elevatedHeight);
    }

    @Test
    void gradeSeparationKeepsHeightsConsistentOnBothSubEdges() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setDefaultCrossingClearance(4.0);
        RoadGenerator generator = new RoadGenerator(config, null);
        TerrainSampler terrain = new FlatTerrainSampler(68);

        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 10));
        RoadNode south = network.createNode(new Vec2d(0, -10));
        RoadNode east = network.createNode(new Vec2d(10, 0));
        RoadNode west = network.createNode(new Vec2d(-10, 0));

        Road roadA = network.createRoad("road-a");
        Road roadB = network.createRoad("road-b");

        network.createEdge(
            junction.getId(), north.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 10)), roadA.getId());
        var southEdge = network.createEdge(
            junction.getId(), south.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, -10)), roadA.getId());
        var eastEdge = network.createEdge(
            junction.getId(), east.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), roadB.getId());
        var westEdge = network.createEdge(
            junction.getId(), west.getId(), List.of(new Vec2d(0, 0), new Vec2d(-10, 0)), roadB.getId());

        network.setNodeGradeSeparation(junction.getId(), true, roadB.getId(), 4.0);

        int eastAtJunction = generator.getTargetHeightAtNode(eastEdge, junction, network, terrain);
        int westAtJunction = generator.getTargetHeightAtNode(westEdge, junction, network, terrain);
        int southAtJunction = generator.getTargetHeightAtNode(southEdge, junction, network, terrain);

        assertEquals(eastAtJunction, westAtJunction);
        assertEquals(68, southAtJunction);
        assertEquals(72, eastAtJunction);
    }

    @Test
    void manualElevationOverridesGradeSeparation() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setDefaultCrossingClearance(3.0);
        RoadGenerator generator = new RoadGenerator(config, null);
        TerrainSampler terrain = new FlatTerrainSampler(70);

        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setManualElevation(80.0);
        RoadNode north = network.createNode(new Vec2d(0, 12));
        RoadNode south = network.createNode(new Vec2d(0, -12));
        RoadNode east = network.createNode(new Vec2d(12, 0));
        RoadNode west = network.createNode(new Vec2d(-12, 0));
        Road roadA = network.createRoad("road-a");
        Road roadB = network.createRoad("road-b");

        var eastEdge = network.createEdge(
            junction.getId(), east.getId(), List.of(new Vec2d(0, 0), new Vec2d(12, 0)), roadB.getId());
        network.createEdge(
            junction.getId(), west.getId(), List.of(new Vec2d(0, 0), new Vec2d(-12, 0)), roadB.getId());
        var northEdge = network.createEdge(
            junction.getId(), north.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 12)), roadA.getId());
        network.createEdge(
            junction.getId(), south.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, -12)), roadA.getId());
        network.setNodeGradeSeparation(junction.getId(), true, roadB.getId(), 3.0);

        assertEquals(80, generator.getTargetHeightAtNode(eastEdge, junction, network, terrain));
        assertEquals(80, generator.getTargetHeightAtNode(northEdge, junction, network, terrain));
    }

    @Test
    void resolveElevatedRoadIdAutoSelectsHigherNaturalRoad() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        RoadGenerator generator = new RoadGenerator(config, null);
        TerrainSampler terrain = directionalTerrain(64, 80);

        SimpleCrossFixture fixture = SimpleCrossFixture.create();
        fixture.network().setNodeGradeSeparation(fixture.junction().getId(), true, null, 3.0);

        assertEquals(fixture.roadB().getId(), generator.resolveElevatedRoadId(
            fixture.junction(), fixture.network(), terrain));
    }

    @Test
    void resolveElevatedRoadIdAutoFollowsTerrainSwap() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        RoadGenerator generator = new RoadGenerator(config, null);
        TerrainSampler terrain = directionalTerrain(80, 64);

        SimpleCrossFixture fixture = SimpleCrossFixture.create();
        fixture.network().setNodeGradeSeparation(fixture.junction().getId(), true, null, 3.0);

        assertEquals(fixture.roadA().getId(), generator.resolveElevatedRoadId(
            fixture.junction(), fixture.network(), terrain));
    }

    @Test
    void gradeSeparationRejectedForNonSimpleCrossing() {
        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 10));
        RoadNode east = network.createNode(new Vec2d(10, 0));
        RoadNode west = network.createNode(new Vec2d(-10, 0));
        Road roadA = network.createRoad("road-a");
        Road roadB = network.createRoad("road-b");
        network.createEdge(junction.getId(), north.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 10)), roadA.getId());
        network.createEdge(junction.getId(), east.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), roadB.getId());
        network.createEdge(junction.getId(), west.getId(), List.of(new Vec2d(0, 0), new Vec2d(-10, 0)), roadB.getId());

        assertFalse(network.setNodeGradeSeparation(junction.getId(), true, roadA.getId(), 3.0));
        assertFalse(junction.isGradeSeparated());
    }

    @Test
    void staleElevatedRoadIdIgnoredWhenGradeSeparationDisabled() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        RoadGenerator generator = new RoadGenerator(config, null);
        TerrainSampler terrain = new FlatTerrainSampler(70);

        SimpleCrossFixture fixture = SimpleCrossFixture.create();
        fixture.junction().setGradeSeparated(false);
        fixture.junction().setElevatedRoadId(fixture.roadB().getId());

        int underpassHeight = generator.getTargetHeightAtNode(
            fixture.edgeForRoad(fixture.roadA()), fixture.junction(), fixture.network(), terrain);
        int crossingHeight = generator.getTargetHeightAtNode(
            fixture.edgeForRoad(fixture.roadB()), fixture.junction(), fixture.network(), terrain);

        assertEquals(70, underpassHeight);
        assertEquals(70, crossingHeight);
    }

    private static TerrainSampler directionalTerrain(int horizontalHeight, int verticalHeight) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return horizontalHeight;
            }

            @Override
            public int sampleCrossSectionGroundY(Vec2d center, Vec2d tangent, double halfWidth) {
                if (tangent != null && Math.abs(tangent.x) >= Math.abs(tangent.y)) {
                    return horizontalHeight;
                }
                return verticalHeight;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return true;
            }
        };
    }

    private record SimpleCrossFixture(
            RoadNetwork network,
            RoadNode junction,
            Road roadA,
            Road roadB) {
        static SimpleCrossFixture create() {
            RoadNetwork network = new RoadNetwork();
            RoadNode junction = network.createNode(new Vec2d(0, 0));
            RoadNode north = network.createNode(new Vec2d(0, 12));
            RoadNode south = network.createNode(new Vec2d(0, -12));
            RoadNode east = network.createNode(new Vec2d(12, 0));
            RoadNode west = network.createNode(new Vec2d(-12, 0));
            Road roadA = network.createRoad("road-a");
            Road roadB = network.createRoad("road-b");
            network.createEdge(
                junction.getId(), north.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 12)), roadA.getId());
            network.createEdge(
                junction.getId(), south.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, -12)), roadA.getId());
            network.createEdge(
                junction.getId(), east.getId(), List.of(new Vec2d(0, 0), new Vec2d(12, 0)), roadB.getId());
            network.createEdge(
                junction.getId(), west.getId(), List.of(new Vec2d(0, 0), new Vec2d(-12, 0)), roadB.getId());
            return new SimpleCrossFixture(network, junction, roadA, roadB);
        }

        com.plot.plugin.road.model.RoadEdge edgeForRoad(Road road) {
            return network.getEdgesAtNode(junction.getId()).stream()
                .filter(edge -> road.getId().equals(edge.getRoadId()))
                .findFirst()
                .orElseThrow();
        }
    }
}
