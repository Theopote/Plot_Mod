package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadTestFixtures;
import com.plot.plugin.road.alignment.HorizontalAlignmentChainOriginAligner;
import com.plot.plugin.road.alignment.TurnDirection;
import com.plot.plugin.road.alignment.HorizontalAlignmentCenterlineConsistency;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.HorizontalAlignmentPolylineFitter;
import com.plot.plugin.road.alignment.HorizontalAlignmentTopologyValidator;
import com.plot.plugin.road.alignment.PlanCenterlineSample;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadLoopSeam;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2.4.1 station authority regressions S-R01–S-R15.
 */
class Phase241StationAuthorityTest {

    // --- S-R01: no HA → identity map ---
    @Test
    void sr01_noHa_identityDomainMap() {
        RoadNetwork network = straightRoad(100.0);
        Road road = network.getRoad("main");
        StationDomainMap map = RoadStationing.domainMap(network, road);
        assertTrue(map.isReliable());
        assertEquals(0.0, map.toCanonical(0.0), 1e-9);
        assertEquals(100.0, map.toCanonical(100.0), 1e-9);
        assertEquals(50.0, map.toInstance(50.0), 1e-9);
        assertEquals(50.0, RoadStationing.toCanonicalChainage(network, road, 50.0), 1e-9);
        assertEquals(50.0, RoadStationing.toInstanceChainage(network, road, 50.0), 1e-9);
    }

    // --- S-R02: HA matching length still piecewise-consistent ---
    @Test
    void sr02_matchingHa_roundTripAnchors() {
        RoadNetwork network = straightRoad(200.0);
        Road road = network.getRoad("main");
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0), 0.0,
            List.of(HorizontalAlignmentElement.tangent(200.0))));
        StationDomainMap map = RoadStationing.domainMap(network, road);
        assertTrue(map.isReliable());
        for (double s : List.of(0.0, 40.0, 100.0, 200.0)) {
            double c = map.toCanonical(s);
            assertEquals(s, c, 1e-6);
            assertEquals(s, map.toInstance(c), 1e-6);
        }
    }

    // --- S-R03: invalid inputs are NaN, never silent 0 ---
    @Test
    void sr03_invalidChainageConversionReturnsNaN() {
        RoadNetwork network = straightRoad(50.0);
        Road road = network.getRoad("main");
        assertTrue(Double.isNaN(RoadStationing.toCanonicalChainage(network, road, Double.NaN)));
        assertTrue(Double.isNaN(RoadStationing.toInstanceChainage(network, road, Double.POSITIVE_INFINITY)));
        assertTrue(Double.isNaN(RoadStationing.toCanonicalChainage(null, road, 10.0)));
        assertTrue(Double.isNaN(RoadStationing.toInstanceChainage(network, null, 10.0)));
        assertNotEquals(0.0, RoadStationing.toCanonicalChainage(network, road, Double.NaN));
    }

    // --- S-R04: divergent HA/instance lengths use piecewise map, not whole-road ratio ---
    @Test
    void sr04_divergentLengths_piecewiseNotWholeRoadRatio() {
        double instance = 100.0;
        double design = 120.0;
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("divergent");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(instance, 0));
        network.createEdge(a.getId(), b.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(instance, 0)), road.getId());
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0), 0.0,
            List.of(HorizontalAlignmentElement.tangent(design))));

        StationDomainMap map = RoadStationing.domainMap(network, road);
        assertTrue(map.isReliable());
        double midInstance = 50.0;
        double canonical = map.toCanonical(midInstance);
        // Collinear shortened instance must not keep identity overlap (50→50); use length-param map.
        double ratioMid = midInstance * (design / instance);
        assertEquals(0.0, map.toCanonical(0.0), 1e-6);
        assertEquals(design, map.toCanonical(instance), 1e-6);
        assertEquals(0.0, map.toInstance(0.0), 1e-6);
        assertEquals(instance, map.toInstance(design), 1e-6);
        assertEquals(ratioMid, canonical, 1e-4);
        assertEquals(midInstance, map.toInstance(canonical), 1e-4);
    }

    // --- S-R05: DesignElevationSource reads VA in canonical domain ---
    @Test
    void sr05_designElevationUsesCanonicalStation() {
        double instance = 100.0;
        double design = 120.0;
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("va");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(design, 76.0))));
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0), 0.0,
            List.of(HorizontalAlignmentElement.tangent(design))));
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(instance, 0));
        RoadEdge edge = network.createEdge(a.getId(), b.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(instance, 0)), road.getId());
        RoadTestFixtures.confirmEditedProfileGeometry(network, road);

        List<PathSegment> path = List.of(new PathSegment(new Vec2d(0, 0), new Vec2d(instance, 0)));
        DesignElevationSource source = DesignElevationSource.forEdge(network, edge, path);
        assertTrue(source.isActive());

        int atStart = source.elevationAtLocalDistance(0.0);
        int atEnd = source.elevationAtLocalDistance(instance);
        assertEquals(64, atStart);
        assertEquals(76, atEnd);

        // Mid-edge local → canonical via domain map, not instance mid of VA.
        int mid = source.elevationAtLocalDistance(instance / 2.0);
        assertTrue(mid > 64 && mid < 76);
    }

    // --- S-R06: LOOP seam slice selection for elevation ---
    @Test
    void sr06_loopSeamSliceElevationUsesCorrectTraversal() {
        RoadNetwork network = squareLoop();
        Road road = network.getRoad("loop");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        double total = RoadStationing.canonicalLength(network, road);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 60.0),
            PointOfVerticalIntersection.of(total, 80.0))));
        RoadTestFixtures.confirmEditedProfileGeometry(network, road);

        String seamEdgeId = road.getLoopSeam().segmentHintId();
        RoadEdge seamEdge = network.getEdge(seamEdgeId);
        List<OrientedRoadSegment> slices =
            RoadStationing.orientedSegmentsForEdge(network, road, seamEdgeId);
        assertTrue(slices.size() >= 2, "interior seam must split edge into slices");

        List<PathSegment> path = List.of(
            new PathSegment(seamEdge.getCenterlinePoints().getFirst(),
                seamEdge.getCenterlinePoints().getLast()));
        DesignElevationSource source = DesignElevationSource.forEdge(network, seamEdge, path);
        assertTrue(source.isActive());

        OrientedRoadSegment lateSlice = slices.stream()
            .max((a, b) -> Double.compare(a.startStation(), b.startStation()))
            .orElseThrow();
        double localNearEnd = lateSlice.geometryChainEnd() - 0.1;
        double canvas = localNearEnd; // sampledPathLength == edge length in this setup
        double chainage = source.mapLocalToChainage(canvas);
        assertTrue(chainage > total * 0.5,
            "late geometry-local on seam edge should map to late canonical station, got " + chainage);
    }

    // --- S-R07: short edge samples include exact start/end ---
    @Test
    void sr07_shortEdgeSamplesIncludeEndpoints() {
        RoadNetwork network = straightRoad(2.0);
        Road road = network.getRoad("main");
        RoadEdge edge = network.getEdge(road.getOrderedSegmentIds().getFirst());
        List<PlanCenterlineSample> samples =
            RoadPlanGeometry.resolveEdgeCenterlineSamples(network, edge, 5.0);
        assertTrue(samples.size() >= 2);
        assertEquals(0.0, samples.getFirst().canonicalStation(), 1e-6);
        assertEquals(2.0, samples.getLast().canonicalStation(), 1e-6);
        assertEquals(0.0, samples.getFirst().position().x, 1e-6);
        assertEquals(2.0, samples.getLast().position().x, 1e-6);
    }

    // --- S-R08: multi-slice returns per-slice lists; physical stays continuous ---
    @Test
    void sr08_multiSliceSamplesStaySeparateFromPhysical() {
        RoadNetwork network = squareLoop();
        Road road = network.getRoad("loop");
        RoadEdge seamEdge = network.getEdge(road.getLoopSeam().segmentHintId());
        List<List<PlanCenterlineSample>> slices =
            RoadPlanGeometry.resolveEdgeTraversalSliceSamples(network, seamEdge, 2.0);
        assertEquals(2, slices.size());
        for (List<PlanCenterlineSample> slice : slices) {
            assertTrue(slice.size() >= 2);
            double start = slice.getFirst().canonicalStation();
            double end = slice.getLast().canonicalStation();
            assertTrue(Math.abs(end - start) > 1e-6 || slice.size() == 2);
        }
        List<PlanCenterlineSample> physical =
            RoadPlanGeometry.resolvePhysicalEdgeCenterlineSamples(network, seamEdge, 2.0);
        assertTrue(physical.size() >= 2);
        // Physical samples form one continuous geometry local span, not a seam-rotated join.
        assertEquals(0.0, physical.getFirst().position().distance(
            seamEdge.getCenterlinePoints().getFirst()), 1e-6);
    }

    // --- S-R09: reverse storage — no double-mirror on curved polyline ---
    @Test
    void sr09_reverseCurvedBearingUsesGeometryLocalThenSign() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("curve");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(20, 20));
        // Stored reverse: geometry runs from b→a (down-left), but chain traverses a→b.
        RoadEdge edge = network.createEdge(
            b.getId(), a.getId(),
            List.of(new Vec2d(20, 20), new Vec2d(20, 0), new Vec2d(0, 0)),
            road.getId());
        OrientedRoadSegment oriented =
            RoadStationing.orientedSegment(network, road, edge.getId()).orElseThrow();
        assertFalse(oriented.forward());

        // Mid of second storage chord (20,0)→(0,0) is west; chain direction is east.
        double bearing = RoadPlanGeometry.bearingAtStation(network, road, 10.0).orElseThrow();
        assertEquals(0.0, bearing, 1e-3); // east along chain
    }

    // --- S-R10: forward vs reverse storage agree at same world point ---
    @Test
    void sr10_forwardAndReverseStorageAgreeOnWorldBearing() {
        RoadNetwork forwardNet = new RoadNetwork();
        Road forwardRoad = forwardNet.createRoad("fwd");
        RoadNode fa = forwardNet.createNode(new Vec2d(0, 0));
        RoadNode fb = forwardNet.createNode(new Vec2d(30, 10));
        forwardNet.createEdge(fa.getId(), fb.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(15, 0), new Vec2d(30, 10)),
            forwardRoad.getId());

        RoadNetwork reverseNet = new RoadNetwork();
        Road reverseRoad = reverseNet.createRoad("rev");
        RoadNode ra = reverseNet.createNode(new Vec2d(0, 0));
        RoadNode rb = reverseNet.createNode(new Vec2d(30, 10));
        reverseNet.createEdge(rb.getId(), ra.getId(),
            List.of(new Vec2d(30, 10), new Vec2d(15, 0), new Vec2d(0, 0)),
            reverseRoad.getId());

        double fwd = RoadPlanGeometry.bearingAtStation(forwardNet, forwardRoad, 5.0).orElseThrow();
        double rev = RoadPlanGeometry.bearingAtStation(reverseNet, reverseRoad, 5.0).orElseThrow();
        assertEquals(fwd, rev, 1e-6);
    }

    // --- S-R11: LOOP HA fit walks each physical edge once ---
    @Test
    void sr11_loopHaFitUsesPhysicalChainOncePerEdge() {
        RoadNetwork network = squareLoop();
        Road road = network.getRoad("loop");
        List<OrientedRoadSegment> physical =
            RoadStationing.physicalEdgeSegmentsForMaterialization(network, road);
        List<OrientedRoadSegment> traversal = RoadStationing.orientedSegments(network, road);
        assertTrue(traversal.size() > physical.size());
        Set<String> physicalIds = new HashSet<>();
        for (OrientedRoadSegment segment : physical) {
            assertTrue(physicalIds.add(segment.edgeId()), "physical chain must not double-visit edges");
        }
        assertEquals(road.getOrderedSegmentIds().size(), physicalIds.size());

        assertTrue(HorizontalAlignmentPolylineFitter.fit(network, road).isPresent());
    }

    // --- S-R12: HA topology/consistency compare in unified native↔canonical domain ---
    @Test
    void sr12_haValidatorsUseUnifiedDomain() {
        RoadNetwork linear = straightRoad(100.0);
        Road linearRoad = linear.getRoad("main");
        linearRoad.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0), 0.0,
            List.of(HorizontalAlignmentElement.tangent(100.0))));
        assertTrue(HorizontalAlignmentCenterlineConsistency.isEvaluable(linear, linearRoad));
        assertTrue(HorizontalAlignmentCenterlineConsistency.evaluate(linear, linearRoad).isConsistent());
        assertTrue(HorizontalAlignmentTopologyValidator.validate(linear, linearRoad).isEmpty());

        // LOOP: seam native↔canonical conversion is finite and round-trips at origin.
        RoadNetwork loop = squareLoopWithHa();
        Road loopRoad = loop.getRoad("loop");
        double offset = RoadPlanGeometry.seamHaNativeOffset(loop, loopRoad);
        assertTrue(Double.isFinite(offset));
        double nativeAtSeam = RoadPlanGeometry.haNativeStation(loop, loopRoad, 0.0);
        assertEquals(offset, nativeAtSeam, 1e-3);
        double back = RoadPlanGeometry.canonicalFromHaNative(
            loop, loopRoad, nativeAtSeam, RoadPlanGeometry.designLength(loop, loopRoad));
        assertEquals(0.0, back, 1e-3);
    }

    // --- S-R13: derived origin align does not rewrite authoritative HA bearing ---
    @Test
    void sr13_derivedRefreshDoesNotRewriteHaBearing() {
        RoadNetwork network = straightRoad(100.0);
        Road road = network.getRoad("main");
        double authoritativeBearing = 0.15;
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0.05, 0.0), authoritativeBearing,
            List.of(HorizontalAlignmentElement.tangent(100.0))));

        HorizontalAlignmentChainOriginAligner.alignToChainStart(network, road);
        assertEquals(authoritativeBearing, road.getHorizontalAlignment().getStartBearingRadians(), 1e-12);
        assertEquals(0.0, road.getHorizontalAlignment().getOrigin().x, 1e-6);

        HorizontalAlignmentChainOriginAligner.alignOriginAndBearingToChainStart(network, road);
        assertEquals(0.0, road.getHorizontalAlignment().getStartBearingRadians(), 1e-6);
    }

    // --- S-R14: ReadCache avoids repeated HA seam scans on large LOOP ---
    @Test
    void sr14_readCacheAvoidsRepeatedHaSeamScans() {
        RoadNetwork network = largeLoopWithHa(24);
        Road road = network.getRoads().values().iterator().next();
        var snapshot = network.snapshot();
        Road snapRoad = snapshot.getRoad(road.getId());
        var cache = new RoadStationingReadCache(snapshot);
        try (var ignored = cache.activate()) {
            for (int i = 0; i < 50; i++) {
                RoadPlanGeometry.seamHaNativeOffset(snapshot, snapRoad);
                RoadStationing.domainMap(snapshot, snapRoad);
                RoadStationing.pointAtStation(snapshot, snapRoad, i % 10);
            }
            assertEquals(1, cache.haSampleScanCount(snapRoad.getId()));
        }
    }

    // --- S-R15: isStationable rejects invalid geometry / unresolvable LOOP seam ---
    @Test
    void sr15_isStationableRejectsInvalidInputs() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("bad");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadEdge edge = network.createEdge(a.getId(), b.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(Double.NaN, 0)), road.getId());
        assertFalse(RoadStationing.isStationable(network, road));

        RoadNetwork zero = new RoadNetwork();
        Road zeroRoad = zero.createRoad("zero");
        RoadNode z0 = zero.createNode(new Vec2d(0, 0));
        RoadNode z1 = zero.createNode(new Vec2d(0, 0));
        zero.createEdge(z0.getId(), z1.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(0, 0)), zeroRoad.getId());
        assertFalse(RoadStationing.isStationable(zero, zeroRoad));

        RoadNetwork loop = squareLoop();
        Road loopRoad = loop.getRoad("loop");
        // Corrupt seam segment id so overlay cannot resolve.
        loopRoad.setLoopSeam(RoadLoopSeam.onSegment(new Vec2d(5, 0), "missing-edge", 0.5));
        assertFalse(RoadStationing.isStationable(loop, loopRoad));
        assertTrue(edge.getLength() > 0 || !Double.isFinite(edge.getCenterlinePoints().getLast().x));
    }

    private static RoadNetwork straightRoad(double length) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(length, 0));
        network.createEdge(a.getId(), b.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(length, 0)), road.getId());
        return network;
    }

    private static RoadNetwork squareLoop() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("loop");
        road.setTopologyMode(RoadTopologyMode.LOOP);
        RoadNode n0 = network.createNode(new Vec2d(0, 0));
        RoadNode n1 = network.createNode(new Vec2d(10, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 10));
        RoadNode n3 = network.createNode(new Vec2d(0, 10));
        RoadEdge e0 = network.createEdge(n0.getId(), n1.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n1.getId(), n2.getId(),
            List.of(new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());
        network.createEdge(n2.getId(), n3.getId(),
            List.of(new Vec2d(10, 10), new Vec2d(0, 10)), road.getId());
        network.createEdge(n3.getId(), n0.getId(),
            List.of(new Vec2d(0, 10), new Vec2d(0, 0)), road.getId());
        road.setLoopSeam(RoadLoopSeam.onSegment(new Vec2d(5, 0), e0.getId(), 0.5));
        return network;
    }

    private static RoadNetwork squareLoopWithHa() {
        double arcLen = Math.PI * 10.0 / 2.0;
        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(
            new Vec2d(0, 0),
            0.0,
            List.of(
                HorizontalAlignmentElement.tangent(10),
                HorizontalAlignmentElement.circularArc(arcLen, 10, TurnDirection.LEFT),
                HorizontalAlignmentElement.tangent(10),
                HorizontalAlignmentElement.circularArc(arcLen, 10, TurnDirection.LEFT),
                HorizontalAlignmentElement.tangent(10),
                HorizontalAlignmentElement.circularArc(arcLen, 10, TurnDirection.LEFT),
                HorizontalAlignmentElement.tangent(10),
                HorizontalAlignmentElement.circularArc(arcLen, 10, TurnDirection.LEFT)));
        RoadNetwork network = squareLoop();
        Road road = network.getRoad("loop");
        road.setHorizontalAlignment(alignment);
        return network;
    }

    private static RoadNetwork largeLoopWithHa(int sides) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("big-loop");
        road.setTopologyMode(RoadTopologyMode.LOOP);
        double radius = 40.0;
        RoadNode[] nodes = new RoadNode[sides];
        for (int i = 0; i < sides; i++) {
            double angle = 2.0 * Math.PI * i / sides;
            nodes[i] = network.createNode(new Vec2d(
                radius * Math.cos(angle), radius * Math.sin(angle)));
        }
        RoadEdge first = null;
        for (int i = 0; i < sides; i++) {
            RoadNode a = nodes[i];
            RoadNode b = nodes[(i + 1) % sides];
            RoadEdge edge = network.createEdge(a.getId(), b.getId(),
                List.of(a.getPosition().copy(), b.getPosition().copy()), road.getId());
            if (first == null) {
                first = edge;
            }
        }
        road.setLoopSeam(RoadLoopSeam.onSegment(
            first.getCenterlinePoints().getFirst().lerp(first.getCenterlinePoints().getLast(), 0.5),
            first.getId(),
            0.5));
        HorizontalAlignmentPolylineFitter.fit(network, road).ifPresent(fitted -> {
            road.setHorizontalAlignment(fitted);
            HorizontalAlignmentChainOriginAligner.alignOriginAndBearingToChainStart(network, road);
        });
        return network;
    }
}
