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
import com.plot.plugin.road.pipeline.EdgeGenerationResult;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2.4.4 generation regressions MIG01–MIG02 (non-empty pavement/marking/guardrail).
 */
class Phase244StationMigrationGenerationIT {

    @Test
    void mig01_splitEdgeThenMergeEdgeGenerationEquivalent() {
        RoadSystemConfig config = markedConfig();
        RoadNetwork network = new RoadNetwork();
        Road road = linear(network, "mig01", 2, 40.0);
        seedEngineering(network, road, 80.0);
        assertTrue(ResolvedProfileAuthority.hasValidEditedProfile(network, road));
        assertTrue(ResolvedProfileAuthority.usesEditedProfileForGeneration(network, road));
        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        FlatTerrainSampler terrain = new FlatTerrainSampler(64);

        assertAllEdgesSuccess(generator, network, terrain);
        Set<BlockPos> beforeRoad = generateLayer(generator, network, terrain, RoadSolidLayer.ROAD);
        Set<BlockPos> beforeMark = generateLayer(generator, network, terrain, RoadSolidLayer.MARKING);
        Set<BlockPos> beforeRail = generateLayer(generator, network, terrain, RoadSolidLayer.GUARDRAIL);
        assertFalse(beforeRoad.isEmpty(), "pavement must be non-empty");
        assertFalse(beforeMark.isEmpty(), "markings must be non-empty");
        assertFalse(beforeRail.isEmpty(), "guardrails must be non-empty");

        String roadId = road.getId();
        String edgeId = road.getOrderedSegmentIds().getFirst();
        assertTrue(RoadCenterlineEditor.splitAtLocalDistance(network, edgeId, 20.0).isSuccess());
        assertAllEdgesSuccess(generator, network, terrain);
        Road afterSplit = network.getRoad(roadId);
        assertNotNull(afterSplit);
        assertTrue(ResolvedProfileAuthority.hasValidEditedProfile(network, afterSplit),
            "Split Edge must rebind EDITED without test compensation");
        String midNode = network.getEdge(afterSplit.getOrderedSegmentIds().getFirst()).getEndNodeId();
        assertTrue(RoadCenterlineEditor.mergeThroughNode(network, midNode).isSuccess());
        assertAllEdgesSuccess(generator, network, terrain);
        Road afterMerge = network.getRoad(roadId);
        assertNotNull(afterMerge);
        assertTrue(ResolvedProfileAuthority.hasValidEditedProfile(network, afterMerge),
            "Merge Edge must rebind EDITED without test compensation");
        assertTrue(ResolvedProfileAuthority.usesEditedProfileForGeneration(network, afterMerge),
            "generation must use EDITED, not AUTO fallback");

        Set<BlockPos> afterRoad = generateLayer(generator, network, terrain, RoadSolidLayer.ROAD);
        Set<BlockPos> afterMark = generateLayer(generator, network, terrain, RoadSolidLayer.MARKING);
        Set<BlockPos> afterRail = generateLayer(generator, network, terrain, RoadSolidLayer.GUARDRAIL);
        assertEquals(beforeRoad, afterRoad, "pavement set must match after split→merge edge");
        assertEquals(beforeMark, afterMark, "marking set must match after split→merge edge");
        assertEquals(beforeRail, afterRail, "guardrail set must match after split→merge edge");
    }

    @Test
    void mig02_splitRoadThenMergeRoadGenerationEquivalent() {
        RoadSystemConfig config = markedConfig();
        RoadNetwork network = new RoadNetwork();
        Road road = linear(network, "mig02", 3, 30.0);
        seedEngineering(network, road, 90.0);
        assertTrue(ResolvedProfileAuthority.hasValidEditedProfile(network, road));
        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        FlatTerrainSampler terrain = new FlatTerrainSampler(64);

        assertAllEdgesSuccess(generator, network, terrain);
        Set<BlockPos> beforeRoad = generateLayer(generator, network, terrain, RoadSolidLayer.ROAD);
        Set<BlockPos> beforeMark = generateLayer(generator, network, terrain, RoadSolidLayer.MARKING);
        Set<BlockPos> beforeRail = generateLayer(generator, network, terrain, RoadSolidLayer.GUARDRAIL);
        assertFalse(beforeRoad.isEmpty());
        assertFalse(beforeMark.isEmpty());
        assertFalse(beforeRail.isEmpty());

        String splitSeg = RoadSegmentOrdering.orderedSegmentIds(network, road).get(1);
        String tailId = network.splitRoadBeforeSegment(road.getId(), splitSeg, config);
        assertNotNull(tailId);
        assertAllEdgesSuccess(generator, network, terrain);
        assertTrue(ResolvedProfileAuthority.hasValidEditedProfile(network, network.getRoad(road.getId())));
        assertTrue(ResolvedProfileAuthority.hasValidEditedProfile(network, network.getRoad(tailId)));
        String merged = network.mergeRoadTailIntoHead(road.getId(), tailId, config);
        assertEquals(road.getId(), merged);
        assertAllEdgesSuccess(generator, network, terrain);
        Road after = network.getRoad(road.getId());
        assertTrue(ResolvedProfileAuthority.hasValidEditedProfile(network, after));
        assertTrue(ResolvedProfileAuthority.usesEditedProfileForGeneration(network, after));

        Set<BlockPos> afterRoad = generateLayer(generator, network, terrain, RoadSolidLayer.ROAD);
        Set<BlockPos> afterMark = generateLayer(generator, network, terrain, RoadSolidLayer.MARKING);
        Set<BlockPos> afterRail = generateLayer(generator, network, terrain, RoadSolidLayer.GUARDRAIL);
        assertEquals(beforeRoad, afterRoad);
        assertEquals(beforeMark, afterMark);
        assertEquals(beforeRail, afterRail);
    }

    private static void seedEngineering(RoadNetwork network, Road road, double length) {
        RoadCrossSection section = section(8);
        section.getCarriageway().setLaneCount(2);
        section.getCarriageway().applyUniformDefaultLaneWidth(4);
        section.getMarkings().setCenterLineStyle(CenterLineStyle.SINGLE_DASHED);
        section.getMarkings().setLaneDividers(true);
        road.setCrossSection(section);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(length * 0.5, 68.0),
            PointOfVerticalIntersection.of(length, 64.0))));
        ResolvedProfileAuthority.markEditedProfileConfirmed(network, road);
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(length * 0.3, section(10))),
            VariableCrossSectionTransitionMode.STEP));
        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(5.0, length * 0.8, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT))));
    }

    private static RoadSystemConfig markedConfig() {
        RoadSystemConfig config = new RoadSystemConfig("phase244-mig");
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setCenterLineStyle(CenterLineStyle.SINGLE_DASHED);
        config.setLaneDividers(true);
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

    private static void assertAllEdgesSuccess(
            RoadGenerator generator,
            RoadNetwork network,
            FlatTerrainSampler terrain) {
        for (Road road : network.getRoads().values()) {
            for (String edgeId : road.getOrderedSegmentIds()) {
                RoadEdge edge = network.getEdge(edgeId);
                if (edge == null) {
                    continue;
                }
                EdgeGenerationResult outcome = generator.generateEdgeOutcome(
                    network,
                    edge,
                    network.getNode(edge.getStartNodeId()),
                    network.getNode(edge.getEndNodeId()),
                    terrain,
                    null);
                assertTrue(outcome.isSuccess(), "edge " + edgeId + ": " + outcome.message());
            }
        }
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
