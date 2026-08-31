package com.plot.plugin.road.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.terrain.FlatTerrainSampler;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class VerticalAlignmentGenerationTest {

    @Test
    void generationUsesDesignProfileInsteadOfFlatTerrain() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setRoadWidth(6);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setIncludeDrainage(false);
        config.setPathSampleDistance(10.0);

        RoadGenerator generator = new RoadGenerator(
            config, null, com.plot.infrastructure.event.block.BlockProjectionHandler.getInstance());
        FlatTerrainSampler terrain = new FlatTerrainSampler(64);

        RoadNetwork withAlignment = buildRoadWithVerticalAlignment(80.0, 100.0);
        RoadNetwork withoutAlignment = buildRoadWithoutVerticalAlignment();

        RoadGenerationResult design = generateSingleEdge(generator, withAlignment, terrain);
        RoadGenerationResult terrainDriven = generateSingleEdge(generator, withoutAlignment, terrain);

        assertFalse(design.roadBlocks.isEmpty());
        int designY = design.roadBlocks.getFirst().getY();
        int terrainY = terrainDriven.roadBlocks.getFirst().getY();
        assertEquals(80, designY);
        assertEquals(64, terrainY);
    }

    private static RoadNetwork buildRoadWithVerticalAlignment(double startElevation, double endElevation) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("design");
        road.setWidth(6);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, startElevation),
            PointOfVerticalIntersection.of(100.0, endElevation)
        )));
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());
        return network;
    }

    private static RoadNetwork buildRoadWithoutVerticalAlignment() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("terrain");
        road.setWidth(6);
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());
        return network;
    }

    private static RoadGenerationResult generateSingleEdge(
            RoadGenerator generator,
            RoadNetwork network,
            FlatTerrainSampler terrain) {
        RoadEdge edge = network.getEdges().values().iterator().next();
        RoadNode start = network.getNode(edge.getStartNodeId());
        RoadNode end = network.getNode(edge.getEndNodeId());
        return generator.generateEdge(network, edge, start, end, terrain, null);
    }
}
