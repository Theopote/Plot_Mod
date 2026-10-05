package com.plot.plugin.road.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.crossing.CrossingType;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadCrossingReconciler;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.solid.RoadGenerationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadProfileSamplingCrossingTest {

    private RoadSystemConfig config;
    private RoadNetworkGenerator networkGenerator;
    private FlatTerrainSampler terrain;
    private RoadNetwork network;
    private RoadCrossing crossing;
    private String roadAId;
    private String roadBId;
    private Set<String> liveEdgeIds;

    @BeforeEach
    void setUp() {
        config = new RoadSystemConfig("road_system");
        config.setRoadWidth(6);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setPathSampleDistance(4.0);
        config.setDefaultCrossingClearance(3.0);

        RoadGenerator generator = new RoadGenerator(
            config,
            com.plot.test.world.IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        networkGenerator = new RoadNetworkGenerator(generator);
        terrain = new FlatTerrainSampler(70);

        network = new RoadNetwork();
        RoadNetworkBuilder builder = new RoadNetworkBuilder();
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false), config);
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(5, 0), new Vec2d(5, 10)), false), config);
        RoadCrossingReconciler.reconcileCrossings(network);

        crossing = network.getCrossings().values().iterator().next();
        roadAId = crossing.roadAId();
        roadBId = crossing.roadBId();

        String verticalRoadId = Math.abs(crossing.stationA() - 5.0) < Math.abs(crossing.stationB() - 5.0)
            ? crossing.roadBId() : crossing.roadAId();
        if (!isMostlyVerticalRoad(network, verticalRoadId)) {
            verticalRoadId = crossing.otherRoadId(verticalRoadId);
        }
        assertTrue(network.setCrossingGradeSeparation(
            crossing.id(), CrossingType.GRADE_SEPARATED, verticalRoadId, 3.0));

        liveEdgeIds = network.getEdges().keySet();
        assertTrue(network.getNodes().values().stream().noneMatch(node -> node.getDegree() >= 3));
    }

    @Test
    void calculateProfileSamplingWithCrossingProducesChartForBothRoads() {
        RoadNetworkGenerator.ProfileSamplingResult sampling =
            networkGenerator.calculateProfileSampling(network, terrain);

        assertFalse(sampling.isEmpty());
        assertNotNull(sampling.profileNetwork());
        assertTrue(sampling.profileNetwork().getNodes().values().stream()
            .anyMatch(node -> node.getDegree() >= 3));

        RoadProfileChartData chartA = sampling.roadProfiles().get(roadAId);
        RoadProfileChartData chartB = sampling.roadProfiles().get(roadBId);
        assertNotNull(chartA);
        assertNotNull(chartB);
        assertTrue(chartA.hasProfileData());
        assertTrue(chartB.hasProfileData());

        assertTrue(network.getNodes().values().stream().noneMatch(node -> node.getDegree() >= 3));
    }

    @Test
    void fullPreviewWithCrossingProducesChartForBothRoads() {
        RoadNetworkGenerator.PreviewResult preview =
            networkGenerator.generatePreview(network, terrain);

        assertNotNull(preview.profileNetwork());
        assertFalse(preview.roadProfiles().isEmpty());

        RoadProfileChartData chartA = preview.roadProfiles().get(roadAId);
        RoadProfileChartData chartB = preview.roadProfiles().get(roadBId);
        assertNotNull(chartA);
        assertNotNull(chartB);
        assertTrue(chartA.hasProfileData());
        assertTrue(chartB.hasProfileData());
        assertFalse(preview.aggregate().placementRecords.isEmpty());
    }

    @Test
    void profileUsesSamplingSnapshotAfterCrossingEdgeSplit() {
        RoadNetworkGenerator.ProfileSamplingResult sampling =
            networkGenerator.calculateProfileSampling(network, terrain);

        Set<String> profileEdgeIds = sampling.profileNetwork().getEdges().keySet();
        assertNotEquals(liveEdgeIds, profileEdgeIds);
        assertTrue(profileEdgeIds.size() > liveEdgeIds.size());

        for (String edgeId : liveEdgeIds) {
            assertFalse(profileEdgeIds.contains(edgeId));
        }

        RoadProfileChartData chartA = sampling.roadProfiles().get(roadAId);
        RoadProfileChartData chartB = sampling.roadProfiles().get(roadBId);
        assertNotNull(chartA);
        assertNotNull(chartB);
        assertTrue(chartA.hasProfileData());
        assertTrue(chartB.hasProfileData());

        for (String profileEdgeId : profileEdgeIds) {
            RoadGenerationResult edgeResult = sampling.edgeResults().get(profileEdgeId);
            assertNotNull(edgeResult, "missing edge result for profile edge " + profileEdgeId);
        }
    }

    @Test
    void profileSamplingDoesNotMutateLiveNetwork() {
        List<String> liveSegmentIdsBefore = new ArrayList<>();
        for (Road road : network.getRoads().values()) {
            liveSegmentIdsBefore.addAll(road.getOrderedSegmentIds());
        }
        int liveNodeCountBefore = network.getNodes().size();
        int liveEdgeCountBefore = network.getEdges().size();

        networkGenerator.calculateProfileSampling(network, terrain);

        assertEquals(liveNodeCountBefore, network.getNodes().size());
        assertEquals(liveEdgeCountBefore, network.getEdges().size());
        assertEquals(liveEdgeIds, network.getEdges().keySet());

        assertEquals(liveSegmentIdsBefore, collectAllSegmentIds(network));
    }

    private static List<String> collectAllSegmentIds(RoadNetwork network) {
        List<String> segmentIds = new ArrayList<>();
        for (Road road : network.getRoads().values()) {
            segmentIds.addAll(road.getOrderedSegmentIds());
        }
        return segmentIds;
    }

    private static boolean isMostlyVerticalRoad(RoadNetwork network, String roadId) {
        Road road = network.getRoad(roadId);
        if (road == null || road.getOrderedSegmentIds().isEmpty()) {
            return false;
        }
        var edge = network.getEdge(road.getOrderedSegmentIds().getFirst());
        if (edge == null || edge.getCenterlinePoints().size() < 2) {
            return false;
        }
        Vec2d start = edge.getCenterlinePoints().getFirst();
        Vec2d end = edge.getCenterlinePoints().getLast();
        return Math.abs(end.x - start.x) < Math.abs(end.y - start.y);
    }
}
