package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.centerline.RoadCenterlineEditor;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.RoadSegmentOrdering;
import com.plot.plugin.road.model.facility.RoadFacilityKind;
import com.plot.plugin.road.model.facility.RoadFacilitySide;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.model.section.VariableCrossSectionTransitionMode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2.4.4 generation regressions MIG01–MIG02.
 */
class Phase244StationMigrationGenerationIT {

    @Test
    void mig01_splitEdgeThenMergeEdgeGenerationEquivalent() {
        RoadSystemConfig config = flatConfig();
        RoadNetwork network = new RoadNetwork();
        Road road = linear(network, "mig01", 2, 40.0);
        road.setCrossSection(section(8));
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(20.0, section(12))),
            VariableCrossSectionTransitionMode.STEP));
        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(5.0, 30.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT))));
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(80.0, 66.0))));

        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        FlatTerrainSampler terrain = new FlatTerrainSampler(64);
        Set<BlockPos> beforeRoad = generateLayer(generator, network, terrain, RoadSolidLayer.ROAD);
        Set<BlockPos> beforeMark = generateLayer(generator, network, terrain, RoadSolidLayer.MARKING);

        String edgeId = road.getOrderedSegmentIds().getFirst();
        assertTrue(RoadCenterlineEditor.splitAtLocalDistance(network, edgeId, 20.0).isSuccess());
        String midNode = network.getEdge(road.getOrderedSegmentIds().getFirst()).getEndNodeId();
        assertTrue(RoadCenterlineEditor.mergeThroughNode(network, midNode).isSuccess());

        Set<BlockPos> afterRoad = generateLayer(generator, network, terrain, RoadSolidLayer.ROAD);
        Set<BlockPos> afterMark = generateLayer(generator, network, terrain, RoadSolidLayer.MARKING);
        assertEquals(beforeRoad, afterRoad, "pavement set must match after split→merge edge");
        assertEquals(beforeMark, afterMark, "marking set must match after split→merge edge");
    }

    @Test
    void mig02_splitRoadThenMergeRoadGenerationEquivalent() {
        RoadSystemConfig config = flatConfig();
        RoadNetwork network = new RoadNetwork();
        Road road = linear(network, "mig02", 3, 30.0);
        road.setCrossSection(section(8));
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(45.0, 65.0),
            PointOfVerticalIntersection.of(90.0, 64.0))));
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(30.0, section(10))),
            VariableCrossSectionTransitionMode.STEP));
        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(10.0, 70.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.RIGHT))));

        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        FlatTerrainSampler terrain = new FlatTerrainSampler(64);
        Set<BlockPos> beforeRoad = generateLayer(generator, network, terrain, RoadSolidLayer.ROAD);
        Set<BlockPos> beforeMark = generateLayer(generator, network, terrain, RoadSolidLayer.MARKING);

        String splitSeg = RoadSegmentOrdering.orderedSegmentIds(network, road).get(1);
        String tailId = network.splitRoadBeforeSegment(road.getId(), splitSeg);
        assertNotNull(tailId);
        String merged = network.mergeRoadTailIntoHead(road.getId(), tailId);
        assertEquals(road.getId(), merged);

        Set<BlockPos> afterRoad = generateLayer(generator, network, terrain, RoadSolidLayer.ROAD);
        Set<BlockPos> afterMark = generateLayer(generator, network, terrain, RoadSolidLayer.MARKING);
        assertEquals(beforeRoad, afterRoad, "pavement set must match after split→merge road");
        assertEquals(beforeMark, afterMark, "marking set must match after split→merge road");
    }

    private static RoadSystemConfig flatConfig() {
        RoadSystemConfig config = new RoadSystemConfig("phase244-mig");
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setCenterLineStyle(CenterLineStyle.NONE);
        config.setLaneDividers(false);
        return config;
    }

    private static Road linear(RoadNetwork network, String name, int segments, double segLen) {
        Road road = network.createRoad(name);
        RoadNode prev = network.createNode(new Vec2d(0, 0));
        for (int i = 1; i <= segments; i++) {
            RoadNode next = network.createNode(new Vec2d(segLen * i, 0));
            network.createEdge(prev.getId(), next.getId(),
                List.of(prev.getPosition().copy(), next.getPosition().copy()), road.getId());
            prev = next;
        }
        return road;
    }

    private static RoadCrossSection section(int width) {
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setWidth(width);
        return section;
    }

    private static Set<BlockPos> generateLayer(
            RoadGenerator generator,
            RoadNetwork network,
            FlatTerrainSampler terrain,
            RoadSolidLayer layer) {
        Set<BlockPos> blocks = new HashSet<>();
        for (Road road : network.getRoads().values()) {
            for (String edgeId : road.getOrderedSegmentIds()) {
                RoadEdge edge = network.getEdge(edgeId);
                if (edge == null) {
                    continue;
                }
                RoadGenerationResult result = generator.generateEdge(
                    network,
                    edge,
                    network.getNode(edge.getStartNodeId()),
                    network.getNode(edge.getEndNodeId()),
                    terrain,
                    null);
                for (var entry : result.placementLayers.entrySet()) {
                    if (entry.getValue() == layer) {
                        blocks.add(entry.getKey());
                    }
                }
                if (layer == RoadSolidLayer.ROAD) {
                    blocks.addAll(result.roadBlocks);
                }
            }
        }
        return blocks;
    }
}
