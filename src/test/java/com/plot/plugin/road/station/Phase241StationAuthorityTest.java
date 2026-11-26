package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadTestFixtures;
import com.plot.plugin.road.alignment.HorizontalAlignmentChainOriginAligner;
import com.plot.plugin.road.alignment.HorizontalAlignmentCenterlineConsistency;
import com.plot.plugin.road.alignment.HorizontalAlignmentCenterlineMaterializer;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.HorizontalAlignmentPolylineFitter;
import com.plot.plugin.road.alignment.PlanCenterlineSample;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.alignment.TurnDirection;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2.4.1 station authority + mapping stabilization regressions.
 */
class Phase241StationAuthorityTest {

    @Test
    void sr01_noHa_identityDomainMap() {
        RoadNetwork network = straightRoad(100.0);
        Road road = network.getRoad("main");
        StationDomainMap map = RoadStationing.domainMap(network, road);
        assertTrue(map.isReliable());
        assertEquals(0.0, map.toCanonical(0.0), 1e-9);
        assertEquals(100.0, map.toCanonical(100.0), 1e-9);
        assertEquals(50.0, map.toInstance(50.0), 1e-9);
    }

    @Test
    void sr03_invalidChainageConversionReturnsNaN() {
        RoadNetwork network = straightRoad(50.0);
        Road road = network.getRoad("main");
        assertTrue(Double.isNaN(RoadStationing.toCanonicalChainage(network, road, Double.NaN)));
        assertTrue(Double.isNaN(RoadStationing.toInstanceChainage(network, road, Double.POSITIVE_INFINITY)));
        assertNotEquals(0.0, RoadStationing.toCanonicalChainage(network, road, Double.NaN));
    }

    /** Collinear whole-road stretch is allowed after verification (not per-anchor guessing). */
    @Test
    void sr04_verifiedCollinearStretchUsesParametricMap() {
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
        assertEquals(60.0, map.toCanonical(50.0), 1e-4);
        assertEquals(50.0, map.toInstance(60.0), 1e-4);
    }

    @Test
    void kr01_reverseEdgeKeepsInteriorAnchors() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("rev");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(60, 40));
        // Stored reverse with bend; chain direction still a→b.
        network.createEdge(b.getId(), a.getId(),
            List.of(new Vec2d(60, 40), new Vec2d(60, 0), new Vec2d(30, 0), new Vec2d(0, 0)),
            road.getId());
        // HA matches the L-shaped chain (east then north) with equal total length.
        double turn = Math.PI / 2.0 * 1.0;
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0), 0.0,
            List.of(
                HorizontalAlignmentElement.tangent(60.0),
                HorizontalAlignmentElement.circularArc(turn, 1.0, TurnDirection.LEFT),
                HorizontalAlignmentElement.tangent(39.0))));

        OrientedRoadSegment oriented =
            RoadStationing.orientedSegment(network, road, road.getOrderedSegmentIds().getFirst()).orElseThrow();
        assertFalse(oriented.forward());

        // K01 core: chainLocal ascent yields ascending instance stations on a reverse edge.
        double prev = -1.0;
        int interiors = 0;
        for (double chainLocal = 0.0; chainLocal <= oriented.length() + 1e-9; chainLocal += 10.0) {
            double station = oriented.roadStationAtChainLocal(Math.min(chainLocal, oriented.length()));
            assertTrue(station + 1e-9 >= prev, "instance must increase with chainLocal on reverse edge");
            prev = station;
            if (chainLocal > 1e-6 && chainLocal < oriented.length() - 1e-6) {
                interiors++;
            }
        }
        assertTrue(interiors >= 3);

        StationDomainMap map = RoadStationing.domainMap(network, road);
        if (map.isReliable()) {
            assertTrue(map.anchorCount() >= 3, "count=" + map.anchorCount());
            Vec2d at30 = RoadPlanGeometry.instancePointAtInstanceChainage(network, road, 30.0).orElseThrow();
            assertEquals(30.0, at30.x, 0.5);
            assertEquals(0.0, at30.y, 0.5);
        }
    }

    @Test
    void kr02_localCurveShorteningDoesNotMoveUpstreamFacility() {
        // Instance: 0-40 straight, 40-60 shortened bulge (10 instead of 20), 60-100 → total 90
        // Design: 0-40 straight, 40-60 arc-like 20, 60-100 → total 100
        // Facility at instance 30 must map near canonical 30, not 27 (whole-road 90/100).
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("local");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(40, 0));
        RoadNode c = network.createNode(new Vec2d(50, 0));
        RoadNode d = network.createNode(new Vec2d(90, 0));
        network.createEdge(a.getId(), b.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(40, 0)), road.getId());
        network.createEdge(b.getId(), c.getId(),
            List.of(new Vec2d(40, 0), new Vec2d(50, 0)), road.getId());
        network.createEdge(c.getId(), d.getId(),
            List.of(new Vec2d(50, 0), new Vec2d(90, 0)), road.getId());

        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0), 0.0,
            List.of(
                HorizontalAlignmentElement.tangent(40),
                HorizontalAlignmentElement.tangent(20),
                HorizontalAlignmentElement.tangent(40))));

        StationDomainMap map = RoadStationing.domainMap(network, road);
        // Either reliable local anchors keep 30≈30, or map is unreliable (must not invent 27).
        if (map.isReliable()) {
            double canonical = map.toCanonical(30.0);
            assertEquals(30.0, canonical, 1.0,
                "upstream of local shorten must stay near 30, not whole-road ratio 27");
            assertNotEquals(27.0, canonical, 0.2);
        } else {
            assertTrue(Double.isNaN(RoadStationing.toCanonicalChainage(network, road, 30.0)));
        }
    }

    @Test
    void kr03_sameTotalDifferentLocalDistributionNotBlindIdentity() {
        // Same total length 100, but HA has a bump (extra length mid-path as arc) vs straight instance
        // Use two-edge instance with a corner vs single tangent HA of equal total — not collinear.
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("same-total");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(50, 0));
        RoadNode c = network.createNode(new Vec2d(50, 50));
        network.createEdge(a.getId(), b.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());
        network.createEdge(b.getId(), c.getId(),
            List.of(new Vec2d(50, 0), new Vec2d(50, 50)), road.getId());
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0), 0.0,
            List.of(HorizontalAlignmentElement.tangent(100.0))));

        StationDomainMap map = RoadStationing.domainMap(network, road);
        // L-shape vs single tangent: totals match but geometry does not — must not be blind identity.
        assertFalse(map.isReliable(),
            "same total length with different local shape must not be auto-identity");
        assertTrue(Double.isNaN(RoadStationing.toCanonicalChainage(network, road, 50.0)));
    }

    @Test
    void kr04_noInteriorCorrespondenceIsNotReliable() {
        // Instance far from HA: no projection within tolerance → unreliable endpoints-only.
        RoadNetwork network = straightRoad(100.0);
        Road road = network.getRoad("main");
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 50), 0.0,
            List.of(
                HorizontalAlignmentElement.tangent(40),
                HorizontalAlignmentElement.circularArc(20, 10, TurnDirection.LEFT),
                HorizontalAlignmentElement.tangent(40))));
        // Force design length ≠ instance so we don't take collinear stretch.
        // Actually lengths differ; non-colinear HA vs straight instance.
        StationDomainMap map = RoadStationing.domainMap(network, road);
        assertFalse(map.isReliable());
        assertTrue(Double.isNaN(RoadStationing.toCanonicalChainage(network, road, 50.0)));
    }

    @Test
    void kr05_repeatPassKeepsIndependentMappingOrder() {
        // Self-crossing-like: go east then west along nearly the same XY corridor on two edges.
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("out-back");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(50, 0));
        RoadNode c = network.createNode(new Vec2d(0, 1));
        network.createEdge(a.getId(), b.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());
        network.createEdge(b.getId(), c.getId(),
            List.of(new Vec2d(50, 0), new Vec2d(0, 1)), road.getId());
        HorizontalAlignmentPolylineFitter.fit(network, road).ifPresent(fitted -> {
            road.setHorizontalAlignment(fitted);
            HorizontalAlignmentChainOriginAligner.alignOriginAndBearingToChainStart(network, road);
        });

        StationDomainMap map = RoadStationing.domainMap(network, road);
        assertTrue(map.isReliable());
        double early = map.toCanonical(10.0);
        double late = map.toCanonical(60.0);
        assertTrue(late > early + 1.0, "second pass must map later than first pass");
        Vec2d pEarly = RoadPlanGeometry.instancePointAtInstanceChainage(network, road, 10.0).orElseThrow();
        Vec2d pLate = RoadPlanGeometry.instancePointAtInstanceChainage(network, road, 60.0).orElseThrow();
        assertEquals(10.0, pEarly.x, 0.5);
        assertTrue(pLate.x < 45.0);
    }

    @Test
    void kr06_loopSeamEdgeElevationSamplesBothSides() {
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
        assertEquals(2, slices.size());

        List<PathSegment> path = List.of(
            new PathSegment(seamEdge.getCenterlinePoints().getFirst(),
                seamEdge.getCenterlinePoints().getLast()));
        DesignElevationSource source = DesignElevationSource.forEdge(network, seamEdge, path);
        assertTrue(source.isActive());

        // Seam at 5: geometryLocal 7.5 is on the post-seam slice (near canonical 0),
        // geometryLocal 2.5 is on the pre-seam slice (near canonical end).
        double postSeamLocal = 7.5;
        double preSeamLocal = 2.5;
        double postSeamStation = source.mapLocalToChainage(postSeamLocal);
        double preSeamStation = source.mapLocalToChainage(preSeamLocal);
        assertTrue(Double.isFinite(postSeamStation), "post-seam station");
        assertTrue(Double.isFinite(preSeamStation), "pre-seam station");
        assertTrue(Math.abs(preSeamStation - postSeamStation) > total * 0.3,
            "pre/post seam locals must resolve to distant canonical stations, not a slice-scaled collapse");
        assertTrue(postSeamStation < total * 0.25, "geom 7.5 after seam → early canonical");
        assertTrue(preSeamStation > total * 0.75, "geom 2.5 before seam → late canonical");

        assertEquals(source.elevationAtChainage(postSeamStation),
            source.elevationAtLocalDistance(postSeamLocal));
        assertEquals(source.elevationAtChainage(preSeamStation),
            source.elevationAtLocalDistance(preSeamLocal));
    }

    @Test
    void kr07_seamMoveKeepsPhysicalHaNativeSamplingStable() {
        RoadNetwork network = squareLoopWithMatchingHa();
        Road road = network.getRoad("loop");
        RoadEdge first = network.getEdge(road.getOrderedSegmentIds().getFirst());
        List<PlanCenterlineSample> before =
            RoadPlanGeometry.resolvePhysicalEdgeCenterlineSamples(network, first, 2.0);
        assertTrue(before.size() >= 2);

        // Move seam along the same edge.
        road.setLoopSeam(RoadLoopSeam.onSegment(new Vec2d(7, 0), first.getId(), 0.7));
        List<PlanCenterlineSample> after =
            RoadPlanGeometry.resolvePhysicalEdgeCenterlineSamples(network, first, 2.0);
        assertEquals(before.size(), after.size(), 2);
        assertEquals(before.getFirst().position().x, after.getFirst().position().x, 0.5);
        assertEquals(before.getLast().position().x, after.getLast().position().x, 0.5);
    }

    @Test
    void kr08_shortEdgeAndNonIntegerVerticesAreSampled() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("short");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(3, 0));
        RoadEdge edge = network.createEdge(a.getId(), b.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(1.5, 1.0), new Vec2d(3, 0)), road.getId());

        List<PlanCenterlineSample> samples =
            RoadPlanGeometry.resolvePhysicalEdgeCenterlineSamples(network, edge, 5.0);
        assertTrue(samples.size() >= 3, "must include start, vertex, end despite spacing 5");
        assertEquals(0.0, samples.getFirst().canonicalStation(), 1e-6);
        assertEquals(edge.getLength(), samples.getLast().canonicalStation(), 1e-6);
        boolean hasVertex = samples.stream().anyMatch(s ->
            Math.abs(s.position().x - 1.5) < 0.05 && Math.abs(s.position().y - 1.0) < 0.05);
        assertTrue(hasVertex);
    }

    @Test
    void kr09_repeatedMaterializeDoesNotDriftAuthoritativeHa() {
        RoadNetwork network = straightRoad(100.0);
        Road road = network.getRoad("main");
        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(
            new Vec2d(0, 0), 0.0,
            List.of(HorizontalAlignmentElement.tangent(100.0)));
        road.setHorizontalAlignment(alignment);
        Vec2d origin = alignment.getOrigin().copy();
        double bearing = alignment.getStartBearingRadians();
        int elements = alignment.getElements().size();

        assertTrue(HorizontalAlignmentCenterlineMaterializer.materialize(network, road).isSuccess());
        assertTrue(HorizontalAlignmentCenterlineMaterializer.materialize(network, road).isSuccess());
        assertTrue(HorizontalAlignmentCenterlineMaterializer.materialize(network, road).isSuccess());

        assertEquals(origin.x, road.getHorizontalAlignment().getOrigin().x, 1e-9);
        assertEquals(origin.y, road.getHorizontalAlignment().getOrigin().y, 1e-9);
        assertEquals(bearing, road.getHorizontalAlignment().getStartBearingRadians(), 1e-12);
        assertEquals(elements, road.getHorizontalAlignment().getElements().size());
    }

    @Test
    void kr10_splitAfterHaRefitPreservesCrossingPhysicalPass() {
        RoadNetworkManager manager = new RoadNetworkManager(
            new RoadSystemConfig("phase241-kr10"), new RoadProjectStatus());
        RoadNetwork network = manager.getNetwork();
        Road road = network.createRoad("main");
        RoadNode n0 = network.createNode(new Vec2d(0, 0));
        RoadNode n1 = network.createNode(new Vec2d(50, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        RoadEdge e0 = network.createEdge(n0.getId(), n1.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());
        RoadEdge e1 = network.createEdge(n1.getId(), n2.getId(),
            List.of(new Vec2d(50, 0), new Vec2d(100, 0)), road.getId());
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0), 0.0,
            List.of(HorizontalAlignmentElement.tangent(100.0))));

        Road cross = network.createRoad("cross");
        network.createEdge(
            network.createNode(new Vec2d(25, -10)).getId(),
            network.createNode(new Vec2d(25, 10)).getId(),
            List.of(new Vec2d(25, -10), new Vec2d(25, 10)),
            cross.getId());
        RoadCrossing design = RoadCrossing.gradeSeparated(
            road.getId(), 25.0, cross.getId(), 10.0, new Vec2d(25, 0), cross.getId(), 4.0);
        network.registerCrossing(design);

        String newRoadId = manager.splitRoadBeforeSegment(road.getId(), e1.getId());
        assertNotNull(newRoadId);
        assertFalse(manager.getNetwork().consumeCrossingMigrationFailure());

        RoadCrossing kept = manager.getNetwork().getCrossing(design.id());
        assertNotNull(kept);
        assertEquals(4.0, kept.crossingClearance(), 1e-6);
        assertEquals(new Vec2d(25, 0).x, kept.position().x, 0.5);
        assertEquals(new Vec2d(25, 0).y, kept.position().y, 0.5);
        assertTrue(kept.involvesRoad(road.getId()) || kept.involvesRoad(newRoadId));
        assertTrue(kept.involvesRoad(cross.getId()));
        assertNotNull(e0);
    }

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
    }

    @Test
    void sr09_reverseCurvedBearingUsesGeometryLocalThenSign() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("curve");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(20, 20));
        RoadEdge edge = network.createEdge(
            b.getId(), a.getId(),
            List.of(new Vec2d(20, 20), new Vec2d(20, 0), new Vec2d(0, 0)),
            road.getId());
        assertFalse(RoadStationing.orientedSegment(network, road, edge.getId()).orElseThrow().forward());
        double bearing = RoadPlanGeometry.bearingAtStation(network, road, 10.0).orElseThrow();
        assertEquals(0.0, bearing, 1e-3);
    }

    @Test
    void sr11_loopHaFitUsesPhysicalChainOncePerEdge() {
        RoadNetwork network = squareLoop();
        Road road = network.getRoad("loop");
        List<OrientedRoadSegment> physical =
            RoadStationing.physicalEdgeSegmentsForMaterialization(network, road);
        Set<String> physicalIds = new HashSet<>();
        for (OrientedRoadSegment segment : physical) {
            assertTrue(physicalIds.add(segment.edgeId()));
        }
        assertEquals(road.getOrderedSegmentIds().size(), physicalIds.size());
        assertTrue(HorizontalAlignmentPolylineFitter.fit(network, road).isPresent());
    }

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

    @Test
    void sr15_isStationableRejectsInvalidInputs() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("bad");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        network.createEdge(a.getId(), b.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(Double.NaN, 0)), road.getId());
        assertFalse(RoadStationing.isStationable(network, road));

        RoadNetwork loop = squareLoop();
        Road loopRoad = loop.getRoad("loop");
        loopRoad.setLoopSeam(RoadLoopSeam.onSegment(new Vec2d(5, 0), "missing-edge", 0.5));
        assertFalse(RoadStationing.isStationable(loop, loopRoad));
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

    private static RoadNetwork squareLoopWithMatchingHa() {
        RoadNetwork network = squareLoop();
        Road road = network.getRoad("loop");
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0), 0.0,
            List.of(
                HorizontalAlignmentElement.tangent(10),
                HorizontalAlignmentElement.tangent(10),
                HorizontalAlignmentElement.tangent(10),
                HorizontalAlignmentElement.tangent(10))));
        HorizontalAlignmentChainOriginAligner.alignOriginAndBearingToChainStart(network, road);
        // Rebuild HA as four sides with bearings via fit for physical match.
        HorizontalAlignmentPolylineFitter.fit(network, road).ifPresent(road::setHorizontalAlignment);
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
