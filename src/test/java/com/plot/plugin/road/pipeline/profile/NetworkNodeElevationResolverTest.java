package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.RoadTestFixtures;
import com.plot.plugin.road.RoadTestFixtures;
import com.plot.plugin.road.model.ManualElevationOrigin;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkNodeElevationResolverTest {

    @Test
    void internalJunctionUsesDesignProfileElevation() {
        RoadNetwork network = buildTwoSegmentRoad();
        NetworkNodeElevationResolver resolver = createResolver();

        Map<String, Integer> elevations = resolver.resolve(network, new FlatTerrainSampler(64), null);

        RoadNode middle = network.getNodes().values().stream()
            .filter(node -> node.getConnectedEdgeIds().size() == 2)
            .findFirst()
            .orElseThrow();
        assertEquals(90, elevations.get(middle.getId()));
    }

    @Test
    void endpointHeightMatchesVerticalAlignmentAtNode() {
        RoadNetwork network = buildTwoSegmentRoad();
        RoadEdge firstEdge = network.getEdge(
            RoadStationing.orderedSegments(network, network.getRoad("design")).getFirst());
        RoadNode start = network.getNode(firstEdge.getStartNodeId());

        assertTrue(VerticalAlignmentEndpointHeight.atNode(network, firstEdge, start).isPresent());
        assertEquals(80, VerticalAlignmentEndpointHeight.atNode(network, firstEdge, start).getAsInt());
    }

    @Test
    void endpointHeightRespectsSegmentChainDirection() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("design");
        road.setVerticalMode(com.plot.plugin.road.vertical.RoadVerticalMode.MANUAL_PROFILE);
        road.setVerticalAlignment(new RoadVerticalAlignment(java.util.List.of(
            PointOfVerticalIntersection.of(0.0, 80.0),
            PointOfVerticalIntersection.of(100.0, 100.0)
        )));
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(50, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), java.util.List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());
        RoadEdge reversedTail = network.createEdge(
            n3.getId(), n2.getId(), java.util.List.of(new Vec2d(100, 0), new Vec2d(50, 0)), road.getId());
        RoadTestFixtures.confirmEditedProfileGeometry(network, road);

        assertFalse(RoadStationing.segmentFlowsWithGeometry(network, road, reversedTail.getId()));
        RoadNode junction = network.getNode(n2.getId());
        assertEquals(90, VerticalAlignmentEndpointHeight.atNode(network, reversedTail, junction).getAsInt());
    }

    /** VA-A08: conflicting EDITED endpoints must not be averaged into a fake junction height. */
    @Test
    void conflictingEditedDesignsDoNotAverage() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("a");
        roadA.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        roadA.setVerticalAlignment(new RoadVerticalAlignment(java.util.List.of(
            PointOfVerticalIntersection.of(0.0, 80.0),
            PointOfVerticalIntersection.of(40.0, 80.0))));
        Road roadB = network.createRoad("b");
        roadB.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        roadB.setVerticalAlignment(new RoadVerticalAlignment(java.util.List.of(
            PointOfVerticalIntersection.of(0.0, 70.0),
            PointOfVerticalIntersection.of(40.0, 70.0))));
        RoadNode shared = network.createNode(new Vec2d(0, 0));
        RoadNode endA = network.createNode(new Vec2d(40, 0));
        RoadNode endB = network.createNode(new Vec2d(0, 40));
        network.createEdge(
            shared.getId(), endA.getId(),
            java.util.List.of(new Vec2d(0, 0), new Vec2d(40, 0)), roadA.getId());
        network.createEdge(
            shared.getId(), endB.getId(),
            java.util.List.of(new Vec2d(0, 0), new Vec2d(0, 40)), roadB.getId());
        RoadTestFixtures.confirmEditedProfileGeometry(network, roadA);
        RoadTestFixtures.confirmEditedProfileGeometry(network, roadB);

        AtomicBoolean logged = new AtomicBoolean(false);
        Map<String, Integer> elevations = createResolver().resolve(
            network,
            new FlatTerrainSampler(64),
            (nodeId, samples, spread, resolvedHeight) -> logged.set(true));

        assertNull(elevations.get(shared.getId()),
            "conflicting EDITED designs must not produce an averaged junction height");
        assertTrue(logged.get(), "conflict should be reported to mismatch logger");
    }

    /** VA-A06: USER lock conflicting with EDITED design refuses silent resolution. */
    @Test
    void userLockConflictingWithEditedDesignIsUnresolved() {
        RoadNetwork network = buildTwoSegmentRoad();
        RoadNode middle = network.getNodes().values().stream()
            .filter(node -> node.getConnectedEdgeIds().size() == 2)
            .findFirst()
            .orElseThrow();
        middle.setManualElevation(70.0, ManualElevationOrigin.USER);

        Map<String, Integer> elevations = createResolver().resolve(
            network, new FlatTerrainSampler(64), null);

        assertNull(elevations.get(middle.getId()),
            "USER lock vs EDITED Y90 must not silently pick either value");
    }

    /** VB04: two EDITED junction conflict surfaces as hard conflict (not silent omit). */
    @Test
    void conflictingEditedDesignsReportHardConflict() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("a");
        roadA.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        roadA.setVerticalAlignment(new RoadVerticalAlignment(java.util.List.of(
            PointOfVerticalIntersection.of(0.0, 80.0),
            PointOfVerticalIntersection.of(40.0, 80.0))));
        Road roadB = network.createRoad("b");
        roadB.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        roadB.setVerticalAlignment(new RoadVerticalAlignment(java.util.List.of(
            PointOfVerticalIntersection.of(0.0, 70.0),
            PointOfVerticalIntersection.of(40.0, 70.0))));
        RoadNode shared = network.createNode(new Vec2d(0, 0));
        RoadNode endA = network.createNode(new Vec2d(40, 0));
        RoadNode endB = network.createNode(new Vec2d(0, 40));
        network.createEdge(
            shared.getId(), endA.getId(),
            java.util.List.of(new Vec2d(0, 0), new Vec2d(40, 0)), roadA.getId());
        network.createEdge(
            shared.getId(), endB.getId(),
            java.util.List.of(new Vec2d(0, 0), new Vec2d(0, 40)), roadB.getId());
        RoadTestFixtures.confirmEditedProfileGeometry(network, roadA);
        RoadTestFixtures.confirmEditedProfileGeometry(network, roadB);

        NetworkNodeElevationResult result = createResolver().resolveDetailed(
            network, new FlatTerrainSampler(64), null);

        assertTrue(result.hasHardConflicts());
        assertTrue(result.conflictNodeIds().contains(shared.getId()));
        assertNull(result.heights().get(shared.getId()));
    }

    /** VB05: USER lock vs EDITED is a hard conflict. */
    @Test
    void userLockVsEditedReportsHardConflict() {
        RoadNetwork network = buildTwoSegmentRoad();
        RoadNode middle = network.getNodes().values().stream()
            .filter(node -> node.getConnectedEdgeIds().size() == 2)
            .findFirst()
            .orElseThrow();
        middle.setManualElevation(70.0, ManualElevationOrigin.USER);

        NetworkNodeElevationResult result = createResolver().resolveDetailed(
            network, new FlatTerrainSampler(64), null);

        assertTrue(result.hasHardConflicts());
        assertTrue(result.conflictNodeIds().contains(middle.getId()));
        assertNull(result.heights().get(middle.getId()));
    }

    /** VB06: propagated lock yields to valid EDITED design Y. */
    @Test
    void propagatedLockYieldsToEditedDesign() {
        RoadNetwork network = buildTwoSegmentRoad();
        RoadNode middle = network.getNodes().values().stream()
            .filter(node -> node.getConnectedEdgeIds().size() == 2)
            .findFirst()
            .orElseThrow();
        middle.setManualElevation(70.0, ManualElevationOrigin.PROFILE_PROPAGATED);

        NetworkNodeElevationResult result = createResolver().resolveDetailed(
            network, new FlatTerrainSampler(64), null);

        assertFalse(result.conflictNodeIds().contains(middle.getId()));
        assertEquals(90, result.heights().get(middle.getId()));
    }

    /** VB06 pass-2: PROFILE_PROPAGATED forced endpoint yields to design. */
    @Test
    void resolveForcedEndpointHeightPropagatedYieldsToDesign() {
        RoadNetwork network = buildTwoSegmentRoad();
        RoadEdge firstEdge = network.getEdge(
            RoadStationing.orderedSegments(network, network.getRoad("design")).getFirst());
        RoadNode middle = network.getNodes().values().stream()
            .filter(node -> node.getConnectedEdgeIds().size() == 2)
            .findFirst()
            .orElseThrow();
        middle.setManualElevation(70.0, ManualElevationOrigin.PROFILE_PROPAGATED);

        RoadSystemConfig config = new RoadSystemConfig("test");
        RoadGeneratorProfileContext profileContext =
            new RoadGeneratorProfileContext(config, (pathPoints, segments) -> 1.0);
        GradeSeparationPolicy gradeSeparation = new GradeSeparationPolicy(profileContext);
        Integer forced = gradeSeparation.resolveForcedEndpointHeight(
            middle, network, firstEdge, new FlatTerrainSampler(64), false,
            (n, net, t, roadId) -> 64, (e, n, net, t) -> 64);

        assertEquals(90, forced);
    }

    /** VC06: EDITED + AUTO + old propagated lock — both edges use first-pass Y. */
    @Test
    void editedAndAutoWithPropagatedLockShareResolvedY() {
        RoadNetwork network = new RoadNetwork();
        Road edited = network.createRoad("edited");
        edited.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        edited.setVerticalAlignment(new RoadVerticalAlignment(java.util.List.of(
            PointOfVerticalIntersection.of(0.0, 90.0),
            PointOfVerticalIntersection.of(40.0, 90.0))));
        Road auto = network.createRoad("auto");
        auto.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        RoadNode shared = network.createNode(new Vec2d(0, 0));
        RoadNode endEdited = network.createNode(new Vec2d(40, 0));
        RoadNode endAuto = network.createNode(new Vec2d(0, 40));
        RoadEdge editedEdge = network.createEdge(
            shared.getId(), endEdited.getId(),
            java.util.List.of(new Vec2d(0, 0), new Vec2d(40, 0)), edited.getId());
        RoadEdge autoEdge = network.createEdge(
            shared.getId(), endAuto.getId(),
            java.util.List.of(new Vec2d(0, 0), new Vec2d(0, 40)), auto.getId());
        RoadTestFixtures.confirmEditedProfileGeometry(network, edited);
        shared.setManualElevation(70.0, ManualElevationOrigin.PROFILE_PROPAGATED);

        NetworkNodeElevationResolver resolver = createResolver();
        NetworkNodeElevationResult pass1 = resolver.resolveDetailed(
            network, new FlatTerrainSampler(64), null);
        assertEquals(90, pass1.heights().get(shared.getId()));

        RoadSystemConfig config = new RoadSystemConfig("test");
        RoadGeneratorProfileContext profileContext =
            new RoadGeneratorProfileContext(config, (pathPoints, segments) -> 1.0);
        GradeSeparationPolicy gradeSeparation = new GradeSeparationPolicy(profileContext);
        NodeTargetHeightResolver nodeTargetHeights =
            new NodeTargetHeightResolver(profileContext, gradeSeparation);
        ProfileEndpointHeightResolver endpoints =
            new ProfileEndpointHeightResolver(gradeSeparation, nodeTargetHeights);

        Integer editedY = endpoints.resolve(
            shared, network, editedEdge, new FlatTerrainSampler(64), false, pass1.heights());
        Integer autoY = endpoints.resolve(
            shared, network, autoEdge, new FlatTerrainSampler(64), false, pass1.heights());
        assertEquals(90, editedY);
        assertEquals(90, autoY);
    }

    /** VC07: CROSSING lock yields to EDITED like pass-1. */
    @Test
    void crossingLockYieldsToEditedDesign() {
        RoadNetwork network = buildTwoSegmentRoad();
        RoadNode middle = network.getNodes().values().stream()
            .filter(node -> node.getConnectedEdgeIds().size() == 2)
            .findFirst()
            .orElseThrow();
        middle.setManualElevation(70.0, ManualElevationOrigin.CROSSING);

        NetworkNodeElevationResult result = createResolver().resolveDetailed(
            network, new FlatTerrainSampler(64), null);
        assertEquals(90, result.heights().get(middle.getId()));

        RoadEdge firstEdge = network.getEdge(
            RoadStationing.orderedSegments(network, network.getRoad("design")).getFirst());
        RoadSystemConfig config = new RoadSystemConfig("test");
        RoadGeneratorProfileContext profileContext =
            new RoadGeneratorProfileContext(config, (pathPoints, segments) -> 1.0);
        GradeSeparationPolicy gradeSeparation = new GradeSeparationPolicy(profileContext);
        Integer forced = gradeSeparation.resolveForcedEndpointHeight(
            middle, network, firstEdge, new FlatTerrainSampler(64), false,
            (n, net, t, roadId) -> 64, (e, n, net, t) -> 64);
        assertEquals(90, forced);
    }

    /** VC12: USER vs multi-EDITED conflict keeps all distinct sample Y values. */
    @Test
    void userVsMultiEditedConflictKeepsCompleteSamples() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("a");
        roadA.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        roadA.setVerticalAlignment(new RoadVerticalAlignment(java.util.List.of(
            PointOfVerticalIntersection.of(0.0, 80.0),
            PointOfVerticalIntersection.of(40.0, 80.0))));
        Road roadB = network.createRoad("b");
        roadB.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        roadB.setVerticalAlignment(new RoadVerticalAlignment(java.util.List.of(
            PointOfVerticalIntersection.of(0.0, 90.0),
            PointOfVerticalIntersection.of(40.0, 90.0))));
        RoadNode shared = network.createNode(new Vec2d(0, 0));
        RoadNode endA = network.createNode(new Vec2d(40, 0));
        RoadNode endB = network.createNode(new Vec2d(0, 40));
        network.createEdge(
            shared.getId(), endA.getId(),
            java.util.List.of(new Vec2d(0, 0), new Vec2d(40, 0)), roadA.getId());
        network.createEdge(
            shared.getId(), endB.getId(),
            java.util.List.of(new Vec2d(0, 0), new Vec2d(0, 40)), roadB.getId());
        RoadTestFixtures.confirmEditedProfileGeometry(network, roadA);
        RoadTestFixtures.confirmEditedProfileGeometry(network, roadB);
        shared.setManualElevation(70.0, ManualElevationOrigin.USER);

        NetworkNodeElevationResult result = createResolver().resolveDetailed(
            network, new FlatTerrainSampler(64), null);
        assertTrue(result.hasHardConflicts());
        List<Integer> samples = result.conflictSamples().get(shared.getId());
        assertTrue(samples.contains(70));
        assertTrue(samples.contains(80));
        assertTrue(samples.contains(90));
        assertFalse(samples.equals(List.of(70, 70)));
    }

    /** VB09: compatible AUTO + EDITED junction still resolves. */
    @Test
    void compatibleAutoAndEditedJunctionResolves() {
        RoadNetwork network = new RoadNetwork();
        Road edited = network.createRoad("edited");
        edited.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        edited.setVerticalAlignment(new RoadVerticalAlignment(java.util.List.of(
            PointOfVerticalIntersection.of(0.0, 80.0),
            PointOfVerticalIntersection.of(40.0, 80.0))));
        Road auto = network.createRoad("auto");
        auto.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        RoadNode shared = network.createNode(new Vec2d(0, 0));
        RoadNode endEdited = network.createNode(new Vec2d(40, 0));
        RoadNode endAuto = network.createNode(new Vec2d(0, 40));
        network.createEdge(
            shared.getId(), endEdited.getId(),
            java.util.List.of(new Vec2d(0, 0), new Vec2d(40, 0)), edited.getId());
        network.createEdge(
            shared.getId(), endAuto.getId(),
            java.util.List.of(new Vec2d(0, 0), new Vec2d(0, 40)), auto.getId());
        RoadTestFixtures.confirmEditedProfileGeometry(network, edited);

        NetworkNodeElevationResult result = createResolver().resolveDetailed(
            network, new FlatTerrainSampler(64), null);

        assertFalse(result.hasHardConflicts());
        assertEquals(80, result.heights().get(shared.getId()));
    }

    private static NetworkNodeElevationResolver createResolver() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        RoadGeneratorProfileContext profileContext =
            new RoadGeneratorProfileContext(config, (pathPoints, segments) -> 1.0);
        GradeSeparationPolicy gradeSeparation = new GradeSeparationPolicy(profileContext);
        NodeTargetHeightResolver nodeTargetHeights =
            new NodeTargetHeightResolver(profileContext, gradeSeparation);
        return new NetworkNodeElevationResolver(profileContext, gradeSeparation, nodeTargetHeights);
    }

    private static RoadNetwork buildTwoSegmentRoad() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("design");
        road.setVerticalMode(com.plot.plugin.road.vertical.RoadVerticalMode.MANUAL_PROFILE);
        road.setVerticalAlignment(new RoadVerticalAlignment(java.util.List.of(
            PointOfVerticalIntersection.of(0.0, 80.0),
            PointOfVerticalIntersection.of(100.0, 100.0)
        )));
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(50, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), java.util.List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());
        network.createEdge(
            n2.getId(), n3.getId(), java.util.List.of(new Vec2d(50, 0), new Vec2d(100, 0)), road.getId());
        RoadTestFixtures.confirmEditedProfileGeometry(network, road);
        return network;
    }
}
