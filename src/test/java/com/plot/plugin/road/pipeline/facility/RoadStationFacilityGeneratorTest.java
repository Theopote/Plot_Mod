package com.plot.plugin.road.pipeline.facility;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.facility.RoadFacilityKind;
import com.plot.plugin.road.model.facility.RoadFacilitySide;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.terrain.FlatTerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadStationFacilityGeneratorTest {

    @Test
    void generateAlongStationsPlacesGuardrailAndRetainingWall() {
        RoadSolidModel solids = new RoadSolidModel();
        Road road = new Road("r1");
        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(0.0, 20.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT),
            new StationFacilityRun(
                0.0, 20.0, RoadFacilityKind.RETAINING_WALL, RoadFacilitySide.RIGHT, null, 2.0)
        )));

        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(20, 0));
        List<PathSegment> segments = List.of(segment);
        List<SegmentHeightInfo> heightInfos = List.of(new SegmentHeightInfo(segment, 64, 64, 64, 64, 0.0));

        RoadStationFacilityGenerator.generateAlongStations(
            solids,
            segments,
            heightInfos,
            CrossSectionBuildContext.fixed(sectionWithoutExtras()),
            road,
            0.0,
            20.0,
            20.0,
            StationFacilityJunctionTrim.FacilityEndpointTrim.NONE,
            1.0,
            DesignElevationSource.inactive(),
            material -> material,
            (center, targetY) -> targetY);

        assertTrue(solids.count(RoadSolidLayer.GUARDRAIL) > 0);
        assertTrue(solids.count(RoadSolidLayer.RETAINING_WALL) >= 4);
    }

    @Test
    void generateEdgeUsesStationGatedDrainageRange() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setIncludeDrainage(true);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setPathSampleDistance(5.0);

        RoadGenerator generator = new RoadGenerator(
            config, null, com.plot.infrastructure.event.block.BlockProjectionHandler.getInstance());
        FlatTerrainSampler terrain = new FlatTerrainSampler(64);

        RoadNetwork partialNetwork = buildRoadWithDrainageRange(30.0, 60.0);
        RoadNetwork fullNetwork = buildRoadWithDrainageRange(0.0, 100.0);

        RoadGenerationResult partial = generateSingleEdge(generator, partialNetwork, terrain);
        RoadGenerationResult full = generateSingleEdge(generator, fullNetwork, terrain);

        assertTrue(partial.sidewalkBlocks.size() > 0);
        assertTrue(full.sidewalkBlocks.size() > partial.sidewalkBlocks.size());
    }

    @Test
    void generateEdgePlacesGuardrailBlocks() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setIncludeDrainage(false);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setPathSampleDistance(5.0);

        RoadGenerator generator = new RoadGenerator(
            config, null, com.plot.infrastructure.event.block.BlockProjectionHandler.getInstance());

        RoadNetwork network = new RoadNetwork();
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(40, 0));
        Road road = network.createRoad("guard");
        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(0.0, 40.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.BOTH)
        )));
        RoadEdge edge = network.createEdge(
            start.getId(), end.getId(), List.of(new Vec2d(0, 0), new Vec2d(40, 0)), road.getId());

        RoadGenerationResult result = generator.generateEdge(
            network, edge, start, end, new FlatTerrainSampler(64), null);

        long guardrailPlacements = result.placementRecords.values().stream()
            .map(record -> record.newBlockId)
            .filter(id -> id.contains("fence"))
            .count();
        assertTrue(guardrailPlacements > 0);
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

    private static RoadNetwork buildRoadWithDrainageRange(double startStation, Double endStation) {
        RoadNetwork network = new RoadNetwork();
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(100, 0));
        Road road = network.createRoad("drain");
        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(startStation, endStation, RoadFacilityKind.DRAINAGE, RoadFacilitySide.BOTH)
        )));
        network.createEdge(
            start.getId(), end.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());
        return network;
    }

    private static ResolvedCrossSection sectionWithoutExtras() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setRoadWidth(6);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setIncludeDrainage(false);
        return ResolvedCrossSection.fromConfig(config);
    }
}
