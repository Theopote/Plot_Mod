package com.plot.plugin.road.model;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.centerline.RoadCenterlineEditor;
import com.plot.plugin.road.model.facility.RoadFacilityKind;
import com.plot.plugin.road.model.facility.RoadFacilitySide;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadCrossSectionEngineeringEquality;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.model.section.VariableCrossSectionResolver;
import com.plot.plugin.road.station.RoadDesignDirection;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.terrain.FlatTerrainSampler;
import com.plot.plugin.road.terrain.TerrainSampler;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 端到端工作流：认领 → 桩号 → HA/VA/VCS/设施 → 预览 → 图编辑 → 整路反向 → 持久化 → 再生成。
 */
class RoadPhase2WorkflowTest {

    private static final double ROAD_LENGTH = 300.0;
    private static final int TYPICAL_WIDTH = 6;
    private static final double VCS_STATION = 100.0;
    private static final int VCS_WIDTH = 10;
    private static final double FACILITY_START = 150.0;
    private static final double FACILITY_END = 220.0;
    private static final double SPLIT_STATION = 150.0;

    private RoadSystemConfig config;
    private RoadGenerator generator;
    private TerrainSampler terrain;
    private RoadNetworkBuilder networkBuilder;

    @BeforeEach
    void setUp() {
        config = new RoadSystemConfig("phase2-workflow");
        config.setRoadWidth(TYPICAL_WIDTH);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setIncludeDrainage(false);
        config.setPathSampleDistance(10.0);
        generator = new RoadGenerator(
            config,
            null,
            com.plot.infrastructure.event.block.BlockProjectionHandler.getInstance());
        terrain = new FlatTerrainSampler(64);
        networkBuilder = new RoadNetworkBuilder();
    }

    @Test
    void fullPhase2DesignWorkflowSurvivesGraphEditsSerializationAndRegeneration() throws Exception {
        RoadNetwork network = adoptThreeHundredMeterRoad();
        Road road = singleAdoptedRoad(network);
        establishOrientedStationing(network, road);

        applyPhase2EngineeringData(road);
        assertPhase2EngineeringSemantics(network, road);

        RoadGenerationResult previewBeforeEdits = generateRoad(network, road);
        assertTrue(previewBeforeEdits.placementRecords.size() > 0
            || !previewBeforeEdits.roadBlocks.isEmpty());

        splitRoadAtMidStation(network, road);
        reverseTailEdgeStorage(network, road);

        RoadGenerationResult previewAfterGraphEdits = generateRoad(network, road);
        assertPhysicalDesignUnchangedAfterGraphEdits(previewBeforeEdits, previewAfterGraphEdits, SPLIT_STATION);
        assertPhase2EngineeringSemantics(network, road);

        assertTrue(RoadCenterlineEditor.reverseRoad(network, road).isSuccess());

        assertMirroredStationDataAfterWholeRoadReverse(network, road);

        String json = network.toJson();
        RoadNetwork restored = RoadNetwork.parseSnapshot(json);
        Road restoredRoad = singleAdoptedRoad(restored);
        assertPhase2RoadEngineeringEquals(road, restoredRoad);

        RoadGenerationResult previewAfterRoadReverse = generateRoad(network, road);
        RoadGenerationResult previewAfterDeserialize = generateRoad(restored, restoredRoad);
        assertGenerationEquivalent(previewAfterRoadReverse, previewAfterDeserialize);
    }

    private RoadNetwork adoptThreeHundredMeterRoad() {
        RoadNetwork network = new RoadNetwork();
        PolylineShape path = new PolylineShape(
            List.of(
                new Vec2d(0, 0),
                new Vec2d(100, 0),
                new Vec2d(200, 0),
                new Vec2d(300, 0)),
            false);
        networkBuilder.adoptShape(network, path, config);
        return network;
    }

    private static Road singleAdoptedRoad(RoadNetwork network) {
        assertEquals(1, network.getRoads().size());
        return network.getRoads().values().iterator().next();
    }

    private static void establishOrientedStationing(RoadNetwork network, Road road) {
        assertTrue(RoadStationing.isStationable(network, road));
        assertEquals(ROAD_LENGTH, RoadStationing.canonicalLength(network, road), 1e-6);

        RoadDesignDirection direction = road.designDirection(network).orElseThrow();
        assertEquals(direction.entryNodeId(),
            RoadStationing.chainEntryNodeId(network, road).orElseThrow());
        assertEquals(direction.exitNodeId(),
            RoadStationing.chainExitNodeId(network, road).orElseThrow());

        Vec2d chainOrigin = RoadStationing.chainOrigin(network, road).orElseThrow();
        assertEquals(0.0, chainOrigin.x, 1e-6);
        assertEquals(0.0, chainOrigin.y, 1e-6);

        assertEquals(1, road.orientedSegments(network).size());
        assertTrue(road.orientedSegments(network).getFirst().forward());
    }

    private void applyPhase2EngineeringData(Road road) {
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0),
            0.0,
            List.of(HorizontalAlignmentElement.tangent(ROAD_LENGTH))));

        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(ROAD_LENGTH, 67.0)
        )));

        RoadCrossSection typical = new RoadCrossSection();
        typical.getCarriageway().setWidth(TYPICAL_WIDTH);
        road.setCrossSection(typical);
        road.setWidth(TYPICAL_WIDTH);

        RoadCrossSection wider = new RoadCrossSection();
        wider.getCarriageway().setWidth(VCS_WIDTH);
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(VCS_STATION, wider)
        )));

        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(
                FACILITY_START,
                FACILITY_END,
                RoadFacilityKind.GUARDRAIL,
                RoadFacilitySide.LEFT)
        )));
    }

    private static void assertPhase2EngineeringSemantics(RoadNetwork network, Road road) {
        assertEquals(VCS_WIDTH, widthOf(VariableCrossSectionResolver.resolveTemplate(road, VCS_STATION + 5.0)));
        assertEquals(TYPICAL_WIDTH, widthOf(VariableCrossSectionResolver.resolveTemplate(road, VCS_STATION - 5.0)));

        OptionalDouble elevation = VerticalAlignmentGeometry.elevationAt(
            road.getVerticalAlignment(),
            FACILITY_START + 10.0);
        assertTrue(elevation.isPresent());
        assertEquals(65.6, elevation.getAsDouble(), 1e-6);

        assertEquals(1, road.getStationFacilities().sortedRuns().size());
        StationFacilityRun run = road.getStationFacilities().sortedRuns().getFirst();
        assertEquals(FACILITY_START, run.getStartStation(), 1e-6);
        assertEquals(FACILITY_END, run.getEndStation(), 1e-6);
        assertEquals(RoadFacilitySide.LEFT, run.getSide());
        assertEquals(RoadFacilityKind.GUARDRAIL, run.getKind());

        assertNotNull(road.getHorizontalAlignment());
        assertEquals(ROAD_LENGTH, road.getHorizontalAlignment().totalLength(), 1e-6);
    }

    private RoadGenerationResult generateRoad(RoadNetwork network, Road road) {
        Map<String, Integer> nodeElevations = generator.resolveNetworkNodeElevations(network, terrain);
        RoadGenerationResult aggregate = new RoadGenerationResult(0.0);
        for (String edgeId : RoadSegmentOrdering.orderedSegmentIds(network, road)) {
            RoadEdge edge = network.getEdge(edgeId);
            RoadNode start = network.getNode(edge.getStartNodeId());
            RoadNode end = network.getNode(edge.getEndNodeId());
            RoadGenerationResult edgeResult = generator.generateEdge(
                network, edge, start, end, terrain, nodeElevations);
            aggregate.mergeFrom(edgeResult);
        }
        return aggregate;
    }

    private static void splitRoadAtMidStation(RoadNetwork network, Road road) {
        String edgeId = RoadSegmentOrdering.orderedSegmentIds(network, road).getFirst();
        assertTrue(RoadCenterlineEditor.splitAtRoadStation(
            network, road, edgeId, SPLIT_STATION).isSuccess());
        assertEquals(2, road.getSegmentIds().size());
        assertEquals(ROAD_LENGTH, RoadStationing.canonicalLength(network, road), 1e-6);
    }

    private static void reverseTailEdgeStorage(RoadNetwork network, Road road) {
        List<String> ordered = RoadSegmentOrdering.orderedSegmentIds(network, road);
        String tailEdgeId = ordered.get(ordered.size() - 1);
        assertTrue(RoadCenterlineEditor.reverseEdge(network, tailEdgeId).isSuccess());
        assertFalse(RoadStationing.orientedSegment(network, road, tailEdgeId).orElseThrow().forward());
    }

    private static void assertMirroredStationDataAfterWholeRoadReverse(RoadNetwork network, Road road) {
        double total = RoadStationing.canonicalLength(network, road);
        assertEquals(ROAD_LENGTH, total, 1e-6);

        List<StationCrossSection> stations = road.getVariableCrossSections().sortedStations();
        assertEquals(2, stations.size());
        assertEquals(0.0, stations.get(0).getStation(), 1e-6);
        assertEquals(VCS_WIDTH, widthOf(stations.get(0).getCrossSection()));
        assertEquals(ROAD_LENGTH - VCS_STATION, stations.get(1).getStation(), 1e-6);
        assertEquals(TYPICAL_WIDTH, widthOf(stations.get(1).getCrossSection()));

        assertEquals(VCS_WIDTH, widthOf(VariableCrossSectionResolver.resolveTemplate(road, 50.0)));
        assertEquals(TYPICAL_WIDTH, widthOf(VariableCrossSectionResolver.resolveTemplate(road, 250.0)));

        StationFacilityRun mirrored = road.getStationFacilities().sortedRuns().getFirst();
        assertEquals(ROAD_LENGTH - FACILITY_END, mirrored.getStartStation(), 1e-6);
        assertEquals(ROAD_LENGTH - FACILITY_START, mirrored.getEndStation(), 1e-6);
        assertEquals(RoadFacilitySide.RIGHT, mirrored.getSide());

        List<PointOfVerticalIntersection> pvis = road.getVerticalAlignment().getPvis();
        assertEquals(2, pvis.size());
        assertEquals(0.0, pvis.get(0).getStation(), 1e-6);
        assertEquals(ROAD_LENGTH, pvis.get(1).getStation(), 1e-6);
        assertEquals(67.0, pvis.get(0).getElevation(), 1e-6);
        assertEquals(64.0, pvis.get(1).getElevation(), 1e-6);
    }

    private static void assertPhase2RoadEngineeringEquals(Road expected, Road actual) {
        assertEquals(expected.getTopologyMode(), actual.getTopologyMode());
        assertHorizontalAlignmentEquals(expected.getHorizontalAlignment(), actual.getHorizontalAlignment());
        assertVerticalAlignmentEquals(expected.getVerticalAlignment(), actual.getVerticalAlignment());
        assertVariableCrossSectionsEquals(expected.getVariableCrossSections(), actual.getVariableCrossSections());
        assertStationFacilitiesEquals(expected.getStationFacilities(), actual.getStationFacilities());
    }

    private static void assertHorizontalAlignmentEquals(
            RoadHorizontalAlignment expected,
            RoadHorizontalAlignment actual) {
        assertNotNull(actual);
        assertEquals(expected.getOrigin().x, actual.getOrigin().x, 1e-6);
        assertEquals(expected.getOrigin().y, actual.getOrigin().y, 1e-6);
        assertEquals(expected.getStartBearingRadians(), actual.getStartBearingRadians(), 1e-9);
        assertEquals(expected.totalLength(), actual.totalLength(), 1e-6);
        assertEquals(expected.getElements().size(), actual.getElements().size());
    }

    private static void assertVerticalAlignmentEquals(
            RoadVerticalAlignment expected,
            RoadVerticalAlignment actual) {
        assertNotNull(actual);
        List<PointOfVerticalIntersection> expectedPvis = expected.getPvis();
        List<PointOfVerticalIntersection> actualPvis = actual.getPvis();
        assertEquals(expectedPvis.size(), actualPvis.size());
        for (int i = 0; i < expectedPvis.size(); i++) {
            assertEquals(expectedPvis.get(i).getStation(), actualPvis.get(i).getStation(), 1e-6);
            assertEquals(expectedPvis.get(i).getElevation(), actualPvis.get(i).getElevation(), 1e-6);
        }
    }

    private static void assertVariableCrossSectionsEquals(
            RoadVariableCrossSections expected,
            RoadVariableCrossSections actual) {
        assertNotNull(actual);
        assertEquals(expected.stationCount(), actual.stationCount());
        List<StationCrossSection> expectedStations = expected.sortedStations();
        List<StationCrossSection> actualStations = actual.sortedStations();
        for (int i = 0; i < expectedStations.size(); i++) {
            assertEquals(expectedStations.get(i).getStation(), actualStations.get(i).getStation(), 1e-6);
            assertTrue(RoadCrossSectionEngineeringEquality.equals(
                expectedStations.get(i).getCrossSection(),
                actualStations.get(i).getCrossSection()));
        }
    }

    private static void assertStationFacilitiesEquals(
            RoadStationFacilities expected,
            RoadStationFacilities actual) {
        assertNotNull(actual);
        assertEquals(expected.runCount(), actual.runCount());
        List<StationFacilityRun> expectedRuns = expected.sortedRuns();
        List<StationFacilityRun> actualRuns = actual.sortedRuns();
        for (int i = 0; i < expectedRuns.size(); i++) {
            StationFacilityRun exp = expectedRuns.get(i);
            StationFacilityRun act = actualRuns.get(i);
            assertEquals(exp.getStartStation(), act.getStartStation(), 1e-6);
            assertEquals(exp.getEndStation(), act.getEndStation());
            assertEquals(exp.getKind(), act.getKind());
            assertEquals(exp.getSide(), act.getSide());
        }
    }

    /**
     * 图编辑（split / reverse edge）后物理设计应不变；内部 degree-2 节点附近允许极小采样差异。
     */
    private static void assertPhysicalDesignUnchangedAfterGraphEdits(
            RoadGenerationResult before,
            RoadGenerationResult after,
            double... tolerantChainageStations) {
        for (Map.Entry<BlockPos, BlockRecord> entry : before.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockRecord other = after.placementRecords.get(pos);
            assertNotNull(other, "missing block at " + pos);
            if (!entry.getValue().newBlockId.equals(other.newBlockId)) {
                assertTrue(
                    isNearTolerantChainage(pos, tolerantChainageStations, 5.0),
                    "material changed at " + pos + ": "
                        + entry.getValue().newBlockId + " -> " + other.newBlockId);
            }
        }
        int extraOutsideTolerance = 0;
        for (BlockPos pos : after.placementRecords.keySet()) {
            if (before.placementRecords.containsKey(pos)) {
                continue;
            }
            if (!isNearTolerantChainage(pos, tolerantChainageStations, 5.0)) {
                extraOutsideTolerance++;
            }
        }
        assertEquals(0, extraOutsideTolerance, "unexpected extra blocks outside split tolerance");
    }

    private static boolean isNearTolerantChainage(BlockPos pos, double[] chainageStations, double toleranceMeters) {
        if (chainageStations == null) {
            return false;
        }
        for (double station : chainageStations) {
            if (Math.abs(pos.getX() - station) <= toleranceMeters + 1e-6) {
                return true;
            }
        }
        return false;
    }

    private static void assertGenerationEquivalent(RoadGenerationResult expected, RoadGenerationResult actual) {
        assertEquals(generationFingerprint(expected), generationFingerprint(actual));
        assertEquals(expected.roadBlocks.size(), actual.roadBlocks.size());
        assertEquals(expected.placementRecords.size(), actual.placementRecords.size());
        assertEquals(expected.cutVolume, actual.cutVolume);
        assertEquals(expected.fillVolume, actual.fillVolume);
    }

    private static String generationFingerprint(RoadGenerationResult result) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockRecord> entry : result.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            lines.add(pos.getX() + "," + pos.getY() + "," + pos.getZ()
                + "=" + entry.getValue().newBlockId);
        }
        Collections.sort(lines);
        return String.join("\n", lines);
    }

    private static int widthOf(RoadCrossSection section) {
        Integer width = section.getCarriageway().getWidth();
        return width != null ? width : 0;
    }
}
