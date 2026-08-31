package com.plot.plugin.road.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.terrain.FlatTerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VariableCrossSectionGenerationTest {

    @Test
    void widerSegmentProducesMoreCarriagewayBlocks() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setRoadWidth(6);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setIncludeDrainage(false);
        config.setPathSampleDistance(5.0);

        RoadGenerator generator = new RoadGenerator(
            config, null, com.plot.infrastructure.event.block.BlockProjectionHandler.getInstance());
        FlatTerrainSampler terrain = new FlatTerrainSampler(64);

        RoadGenerationResult narrow = generateEdge(generator, buildRoadWithStationAt(200.0, 6), terrain, 0, 100);
        RoadGenerationResult wide = generateEdge(generator, buildRoadWithStationAt(100.0, 14), terrain, 100, 150);

        assertTrue(wide.roadBlocks.size() > narrow.roadBlocks.size());
    }

    @Test
    void crossSectionContextResolvesAlongChainage() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        RoadNetwork network = new RoadNetwork();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        RoadNode n3 = network.createNode(new Vec2d(150, 0));
        Road road = network.createRoad("r1");
        road.setWidth(6);
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(100.0, sectionWithWidth(12))
        )));
        RoadEdge tail = network.createEdge(
            n2.getId(), n3.getId(), List.of(new Vec2d(100, 0), new Vec2d(150, 0)), road.getId());
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        CrossSectionBuildContext context = CrossSectionBuildContext.forEdge(network, tail, config);
        assertTrue(context.isVariable());
        assertTrue(context.resolve(120.0).carriagewayWidth > context.resolve(80.0).carriagewayWidth);
    }

    private static RoadGenerationResult generateEdge(
            RoadGenerator generator,
            RoadNetwork network,
            FlatTerrainSampler terrain,
            double stationStart,
            double stationEnd) {
        RoadEdge edge = network.getEdges().values().stream()
            .filter(candidate -> {
                double start = com.plot.plugin.road.station.RoadStationing.segmentStartStation(
                    network, network.getRoad(candidate.getRoadId()), candidate.getId());
                return start >= stationStart - 1e-6 && start < stationEnd - 1e-6;
            })
            .findFirst()
            .orElseThrow();
        RoadNode start = network.getNode(edge.getStartNodeId());
        RoadNode end = network.getNode(edge.getEndNodeId());
        return generator.generateEdge(network, edge, start, end, terrain, null);
    }

    private static RoadNetwork buildRoadWithStationAt(double stationMeters, int widthAtStation) {
        RoadNetwork network = new RoadNetwork();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        RoadNode n3 = network.createNode(new Vec2d(150, 0));
        Road road = network.createRoad("var");
        road.setWidth(6);
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(stationMeters, sectionWithWidth(widthAtStation))
        )));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());
        network.createEdge(
            n2.getId(), n3.getId(), List.of(new Vec2d(100, 0), new Vec2d(150, 0)), road.getId());
        return network;
    }

    private static RoadCrossSection sectionWithWidth(int width) {
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setWidth(width);
        return section;
    }
}
