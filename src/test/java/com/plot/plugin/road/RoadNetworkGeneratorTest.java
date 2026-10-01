package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.crossing.CrossingType;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadCrossingMaterializer;
import com.plot.plugin.road.crossing.RoadCrossingReconciler;
import com.plot.plugin.road.graph.RoadGraphQueries;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.profile.RoadProfileChartData;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.pipeline.EdgeGenerationOutcome;
import com.plot.plugin.road.pipeline.EdgeGenerationResult;
import com.plot.plugin.road.pipeline.RoadGenerationResultAssembler;
import com.plot.core.terrain.FlatTerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetworkGeneratorTest {

    @Test
    void resolveJunctionMaterialPrefersWidestConnectedEdge() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setSelectedMaterial("minecraft:white_concrete");
        config.setSelectedSidewalkMaterial("minecraft:gravel");

        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 10));
        RoadNode east = network.createNode(new Vec2d(10, 0));
        RoadNode west = network.createNode(new Vec2d(-10, 0));

        Road narrowRoad = network.createRoad();
        narrowRoad.setWidth(5);
        narrowRoad.setMaterial("minecraft:stone");
        network.createEdge(
            junction.getId(), north.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 10)), narrowRoad.getId());

        Road wideRoad = network.createRoad();
        wideRoad.setWidth(9);
        wideRoad.setMaterial("minecraft:oak_planks");
        wideRoad.setIncludeSidewalk(true);
        wideRoad.setSidewalkMaterial("minecraft:oak_planks");
        network.createEdge(
            junction.getId(), east.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), wideRoad.getId());

        network.createEdge(
            junction.getId(), west.getId(), List.of(new Vec2d(0, 0), new Vec2d(-10, 0)));

        assertEquals(
            "minecraft:oak_planks",
            RoadGenerationResultAssembler.resolveJunctionMaterial(junction, network, config, false));
        assertEquals(
            "minecraft:oak_planks",
            RoadGenerationResultAssembler.resolveJunctionMaterial(junction, network, config, true));
    }

    @Test
    void shouldGenerateJunctionReturnsFalseWhenConnectedEdgeFailed() {
        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 10));
        RoadNode east = network.createNode(new Vec2d(10, 0));
        RoadNode west = network.createNode(new Vec2d(-10, 0));

        network.createEdge(junction.getId(), north.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 10)));
        RoadEdge eastEdge = network.createEdge(
            junction.getId(), east.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        network.createEdge(junction.getId(), west.getId(), List.of(new Vec2d(0, 0), new Vec2d(-10, 0)));

        assertTrue(RoadNetworkGenerator.shouldGenerateJunction(junction, Set.of()));
        assertFalse(RoadNetworkGenerator.shouldGenerateJunction(junction, Set.of(eastEdge.getId())));
    }

    @Test
    void networkResultSeparatesFailedEdgesFromSuccessfulGeometry() {
        var networkResult = new RoadNetworkGenerator.NetworkGenerationResult();
        networkResult.recordEdgeOutcome("ok", EdgeGenerationResult.success(new com.plot.plugin.road.solid.RoadGenerationResult(10)));
        networkResult.recordEdgeOutcome("bad", EdgeGenerationResult.failed("boom"));

        assertEquals(1, networkResult.getEdgeResults().size());
        assertTrue(networkResult.getFailedEdgeIds().contains("bad"));
        assertEquals(1, networkResult.successEdgeCount());
        assertEquals(2, networkResult.totalEdgeCount());
        assertTrue(networkResult.hasPartialFailure());
        assertEquals(1, networkResult.getErrors().size());
    }

    @Test
    void generateAllDoesNotMutateLiveDerivedCenterline() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(6);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setIncludeDrainage(false);
        config.setPathSampleDistance(4.0);

        RoadGenerator generator = new RoadGenerator(config, com.plot.test.world.IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        RoadNetworkGenerator networkGenerator = new RoadNetworkGenerator(generator);
        FlatTerrainSampler terrain = new FlatTerrainSampler(64);

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(60, 0));
        RoadEdge edge = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(60, 0)), road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 12), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(60.0));
        road.setHorizontalAlignment(alignment);

        double liveYBefore = edge.getCenterlinePoints().getFirst().y;
        assertEquals(0.0, liveYBefore, 1e-6);

        RoadNetworkGenerator.NetworkGenerationResult result =
            networkGenerator.generateAll(network, terrain);

        assertEquals(0.0, edge.getCenterlinePoints().getFirst().y, 1e-6,
            "preview/generate must not write derived centerline back to live network");
        assertEquals(1, result.successEdgeCount());
    }

    @Test
    void generatePreviewMaterializesRegisteredCrossingsWithoutMutatingLiveNetwork() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(6);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setIncludeDrainage(false);
        config.setPathSampleDistance(4.0);
        config.setDefaultCrossingClearance(3.0);

        RoadGenerator generator = new RoadGenerator(
            config,
            com.plot.test.world.IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        RoadNetworkGenerator networkGenerator = new RoadNetworkGenerator(generator);
        FlatTerrainSampler terrain = new FlatTerrainSampler(70);

        RoadNetwork network = new RoadNetwork();
        RoadNetworkBuilder builder = new RoadNetworkBuilder();
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false), config);
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(5, 0), new Vec2d(5, 10)), false), config);
        RoadCrossingReconciler.reconcileCrossings(network);

        RoadCrossing crossing = network.getCrossings().values().iterator().next();
        String verticalRoadId = Math.abs(crossing.stationA() - 5.0) < Math.abs(crossing.stationB() - 5.0)
            ? crossing.roadBId() : crossing.roadAId();
        if (!isMostlyVerticalRoad(network, verticalRoadId)) {
            verticalRoadId = crossing.otherRoadId(verticalRoadId);
        }
        assertTrue(network.setCrossingGradeSeparation(
            crossing.id(), CrossingType.GRADE_SEPARATED, verticalRoadId, 3.0));

        int liveNodesBefore = network.getNodes().size();
        assertTrue(network.getNodes().values().stream().noneMatch(node -> node.getDegree() >= 3));

        RoadNetwork materialized = RoadCrossingMaterializer.materializeForSnapshot(network);
        RoadNode junction = materialized.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElse(null);
        assertNotNull(junction);
        assertTrue(RoadGraphQueries.isSimpleCrossing(junction, materialized));
        assertTrue(junction.isGradeSeparated());
        assertEquals(verticalRoadId, junction.getElevatedRoadId());

        RoadNetworkGenerator.NetworkGenerationResult result = networkGenerator.generateAll(network, terrain);
        assertEquals(liveNodesBefore, network.getNodes().size());
        assertTrue(network.getNodes().values().stream().noneMatch(node -> node.getDegree() >= 3));
        assertFalse(result.getJunctionResults().isEmpty());
        assertTrue(result.successEdgeCount() >= 4);
    }

    @Test
    void calculateProfileSamplingProducesChartDataWithoutPlacement() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setIncludeDrainage(false);

        RoadGenerator generator = new RoadGenerator(
            config,
            com.plot.test.world.IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        RoadNetworkGenerator networkGenerator = new RoadNetworkGenerator(generator);
        FlatTerrainSampler terrain = new FlatTerrainSampler(64);

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoadForAdopt(config);
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)),
            road.getId());

        RoadNetworkGenerator.ProfileSamplingResult sampling =
            networkGenerator.calculateProfileSampling(network, terrain);

        assertFalse(sampling.isEmpty());
        RoadGenerationResult edgeProfile = sampling.edgeResults().values().iterator().next();
        assertTrue(edgeProfile.hasProfileData());
        assertTrue(edgeProfile.placementRecords.isEmpty());

        RoadProfileChartData chart = sampling.roadProfiles().get(road.getId());
        assertNotNull(chart);
        assertTrue(chart.hasProfileData());
        assertNotNull(sampling.profileNetwork());
    }

    @Test
    void calculateProfileSamplingMatchesFullPreviewProfileSeries() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setIncludeDrainage(false);

        RoadGenerator generator = new RoadGenerator(
            config,
            com.plot.test.world.IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        RoadNetworkGenerator networkGenerator = new RoadNetworkGenerator(generator);
        FlatTerrainSampler terrain = new FlatTerrainSampler(72);

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoadForAdopt(config);
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(60, 0));
        RoadEdge edge = network.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(60, 0)),
            road.getId());

        RoadNetworkGenerator.ProfileSamplingResult sampling =
            networkGenerator.calculateProfileSampling(network, terrain);
        RoadNetworkGenerator.PreviewResult preview =
            networkGenerator.generatePreview(network, terrain);

        RoadGenerationResult sampled = sampling.edgeResults().get(edge.getId());
        RoadGenerationResult full = preview.edgeResults().get(edge.getId());
        assertNotNull(sampled);
        assertNotNull(full);
        assertEquals(sampled.profileDistances, full.profileDistances);
        assertEquals(sampled.profileGroundHeights, full.profileGroundHeights);
        assertEquals(sampled.profileTargetHeights, full.profileTargetHeights);
        assertEquals(sampled.profileGuideLine, full.profileGuideLine);
        assertFalse(preview.aggregate().placementRecords.isEmpty());
    }

    private static boolean isMostlyVerticalRoad(RoadNetwork network, String roadId) {
        Road road = network.getRoad(roadId);
        if (road == null || road.getOrderedSegmentIds().isEmpty()) {
            return false;
        }
        RoadEdge edge = network.getEdge(road.getOrderedSegmentIds().getFirst());
        if (edge == null || edge.getCenterlinePoints().size() < 2) {
            return false;
        }
        Vec2d start = edge.getCenterlinePoints().getFirst();
        Vec2d end = edge.getCenterlinePoints().getLast();
        return Math.abs(end.x - start.x) < Math.abs(end.y - start.y);
    }
}
