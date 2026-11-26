package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.RoadTestFixtures;
import com.plot.plugin.road.alignment.HorizontalAlignmentChainOriginAligner;
import com.plot.plugin.road.alignment.HorizontalAlignmentCenterlineConsistency;
import com.plot.plugin.road.alignment.HorizontalAlignmentCenterlineMaterializer;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.HorizontalAlignmentGeometry;
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
import com.plot.plugin.road.pipeline.EdgeGenerationOutcome;
import com.plot.plugin.road.pipeline.EdgeGenerationResult;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        assertEquals(0.0, map.toNative(0.0), 1e-9);
        assertEquals(100.0, map.toNative(100.0), 1e-9);
        assertEquals(50.0, map.toPhysical(50.0), 1e-9);
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
        assertEquals(60.0, map.toNative(50.0), 1e-4);
        assertEquals(50.0, map.toPhysical(60.0), 1e-4);
    }

    @Test
    void kr01_reverseEdgeKeepsInteriorAnchors() {
        // HA: east 60 + north 40. Instance stored reverse, sampled from HA so map is reliable.
        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(
            new Vec2d(0, 0), 0.0,
            List.of(
                HorizontalAlignmentElement.tangent(60.0),
                HorizontalAlignmentElement.circularArc(Math.PI / 2.0 * 1.0, 1.0, TurnDirection.LEFT),
                HorizontalAlignmentElement.tangent(39.0)));
        List<Vec2d> forward = sampleHaPoints(alignment, 5.0);
        List<Vec2d> reverse = new java.util.ArrayList<>();
        for (int i = forward.size() - 1; i >= 0; i--) {
            reverse.add(forward.get(i).copy());
        }
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("rev");
        RoadNode a = network.createNode(forward.getFirst().copy());
        RoadNode b = network.createNode(forward.getLast().copy());
        network.createEdge(b.getId(), a.getId(), reverse, road.getId());
        road.setHorizontalAlignment(alignment);

        OrientedRoadSegment oriented =
            RoadStationing.orientedSegment(network, road, road.getOrderedSegmentIds().getFirst()).orElseThrow();
        assertFalse(oriented.forward());

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

        assertNotNull(road.getHorizontalAlignment());
        StationDomainMap map = RoadStationing.domainMap(network, road);
        assertTrue(map.isReliable(), "reverse-edge sampled from HA must be reliable");
        assertTrue(map.anchorCount() >= 3, "count=" + map.anchorCount());
        Vec2d at30 = RoadPlanGeometry.instancePointAtInstanceChainage(network, road, 30.0).orElseThrow();
        assertEquals(30.0, at30.x, 0.75);
        assertEquals(0.0, at30.y, 0.75);
    }

    @Test
    void kr02_localCurveShorteningDoesNotMoveUpstreamFacility() {
        // HA mid arc length ~20; instance mid edge is a chord (~10) — must stay reliable piecewise.
        double midArc = Math.PI * 10.0 / 2.0; // quarter circle r=10 → ~15.7
        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(
            new Vec2d(0, 0), 0.0,
            List.of(
                HorizontalAlignmentElement.tangent(40.0),
                HorizontalAlignmentElement.circularArc(midArc, 10.0, TurnDirection.LEFT),
                HorizontalAlignmentElement.tangent(40.0)));
        List<Vec2d> full = sampleHaPoints(alignment, 1.0);
        int i40 = indexNearStation(full, alignment, 40.0);
        int iMidEnd = indexNearStation(full, alignment, 40.0 + midArc);
        List<Vec2d> edge0 = new ArrayList<>(full.subList(0, i40 + 1));
        List<Vec2d> edge1 = List.of(full.get(i40).copy(), full.get(iMidEnd).copy()); // chord shorten
        List<Vec2d> edge2 = new ArrayList<>(full.subList(iMidEnd, full.size()));

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("local");
        RoadNode a = network.createNode(edge0.getFirst().copy());
        RoadNode b = network.createNode(edge0.getLast().copy());
        RoadNode c = network.createNode(edge1.getLast().copy());
        RoadNode d = network.createNode(edge2.getLast().copy());
        network.createEdge(a.getId(), b.getId(), edge0, road.getId());
        network.createEdge(b.getId(), c.getId(), edge1, road.getId());
        network.createEdge(c.getId(), d.getId(), edge2, road.getId());
        road.setHorizontalAlignment(alignment);

        StationDomainMap map = RoadStationing.domainMap(network, road);
        assertTrue(map.isReliable(), "local mid-shorten with matching HA endpoints must be reliable");
        double nativeAt30 = map.toNative(30.0);
        assertEquals(30.0, nativeAt30, 1.0,
            "upstream of local shorten must stay near 30, not whole-road ratio");
        double wholeRoadRatio = 30.0 * map.nativeTotal() / map.instanceTotal();
        assertTrue(Math.abs(nativeAt30 - wholeRoadRatio) > 0.5
                || Math.abs(nativeAt30 - 30.0) < 0.5);
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
        // Out-and-back: HA + instance both sampled so two physical passes map independently.
        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(
            new Vec2d(0, 0), 0.0,
            List.of(
                HorizontalAlignmentElement.tangent(50.0),
                HorizontalAlignmentElement.circularArc(Math.PI * 5.0, 5.0, TurnDirection.LEFT),
                HorizontalAlignmentElement.tangent(50.0)));
        List<Vec2d> all = sampleHaPoints(alignment, 2.0);
        int mid = all.size() / 2;
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("out-back");
        RoadNode a = network.createNode(all.getFirst().copy());
        RoadNode b = network.createNode(all.get(mid).copy());
        RoadNode c = network.createNode(all.getLast().copy());
        network.createEdge(a.getId(), b.getId(), all.subList(0, mid + 1), road.getId());
        network.createEdge(b.getId(), c.getId(), all.subList(mid, all.size()), road.getId());
        road.setHorizontalAlignment(alignment);
        assertNotNull(road.getHorizontalAlignment());
        StationDomainMap map = RoadStationing.domainMap(network, road);
        assertTrue(map.isReliable());
        double early = map.toNative(10.0);
        double lateStation = map.instanceTotal() - 10.0;
        double late = map.toNative(lateStation);
        assertTrue(late > early + 1.0, "second pass must map later than first pass");
        Vec2d pEarly = RoadPlanGeometry.instancePointAtInstanceChainage(network, road, 10.0).orElseThrow();
        Vec2d pLate = RoadPlanGeometry.instancePointAtInstanceChainage(network, road, lateStation).orElseThrow();
        assertEquals(10.0, pEarly.x, 0.75);
        assertTrue(pLate.x < 45.0, "return pass x=" + pLate.x);
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
            source.elevationAtLocalDistance(postSeamLocal).orElseThrow());
        assertEquals(source.elevationAtChainage(preSeamStation),
            source.elevationAtLocalDistance(preSeamLocal).orElseThrow());
    }

    @Test
    void kr07_seamMoveKeepsPhysicalHaNativeSamplingStable() {
        RoadNetwork network = squareLoopWithMatchingHa();
        Road road = network.getRoad("loop");
        RoadEdge first = network.getEdge(road.getOrderedSegmentIds().getFirst());
        StationDomainMap debugMap = RoadStationing.domainMap(network, road);
        assertTrue(debugMap.isReliable(),
            "map unreliable anchors=" + debugMap.anchorCount()
                + " phys=" + debugMap.instanceTotal()
                + " native=" + debugMap.nativeTotal()
                + " design=" + RoadPlanGeometry.designLength(network, road)
                + " ha=" + (road.getHorizontalAlignment() != null));
        List<PlanCenterlineSample> before =
            RoadPlanGeometry.resolvePhysicalEdgeCenterlineSamples(network, first, 2.0);
        assertTrue(before.size() >= 2);

        // Move seam along the same edge — physical HA-native samples must stay put.
        double midFrac = 0.7;
        Vec2d seamPos = first.getCenterlinePoints().getFirst()
            .lerp(first.getCenterlinePoints().getLast(), midFrac);
        road.setLoopSeam(RoadLoopSeam.onSegment(seamPos, first.getId(), midFrac));
        List<PlanCenterlineSample> after =
            RoadPlanGeometry.resolvePhysicalEdgeCenterlineSamples(network, first, 2.0);
        assertTrue(after.size() >= 2);
        assertEquals(before.getFirst().position().x, after.getFirst().position().x, 0.5);
        assertEquals(before.getFirst().position().y, after.getFirst().position().y, 0.5);
        assertEquals(before.getLast().position().x, after.getLast().position().x, 0.5);
        assertEquals(before.getLast().position().y, after.getLast().position().y, 0.5);
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
        // Circular curve (not just tangent) — repeated materialize must not drift HA.
        double radius = 40.0;
        double arcLen = Math.PI * radius / 2.0;
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(
            new Vec2d(0, 0), 0.0,
            List.of(HorizontalAlignmentElement.circularArc(arcLen, radius, TurnDirection.LEFT)));
        road.setHorizontalAlignment(alignment);
        List<Vec2d> seed = HorizontalAlignmentGeometry.sample(alignment, 0.5);
        RoadNode a = network.createNode(seed.getFirst().copy());
        RoadNode b = network.createNode(seed.getLast().copy());
        network.createEdge(a.getId(), b.getId(), seed, road.getId());
        assertTrue(RoadStationing.domainMap(network, road).isReliable(),
            "arc seed must map; instance=" + RoadPlanGeometry.instanceLength(network, road)
                + " design=" + RoadPlanGeometry.designLength(network, road));
        assertTrue(HorizontalAlignmentCenterlineConsistency.isMaterializable(network, road),
            "instance=" + RoadPlanGeometry.instanceLength(network, road)
                + " design=" + RoadPlanGeometry.designLength(network, road));

        Vec2d origin = alignment.getOrigin().copy();
        double bearing = alignment.getStartBearingRadians();
        int elements = alignment.getElements().size();
        Vec2d endBefore = seed.getLast().copy();

        assertTrue(HorizontalAlignmentCenterlineMaterializer.materialize(network, road).isSuccess());
        assertTrue(HorizontalAlignmentCenterlineMaterializer.materialize(network, road).isSuccess());
        assertTrue(HorizontalAlignmentCenterlineMaterializer.materialize(network, road).isSuccess());

        assertEquals(origin.x, road.getHorizontalAlignment().getOrigin().x, 1e-9);
        assertEquals(origin.y, road.getHorizontalAlignment().getOrigin().y, 1e-9);
        assertEquals(bearing, road.getHorizontalAlignment().getStartBearingRadians(), 1e-12);
        assertEquals(elements, road.getHorizontalAlignment().getElements().size());
        RoadEdge edge = network.getEdge(road.getOrderedSegmentIds().getFirst());
        var endPose = com.plot.plugin.road.alignment.HorizontalAlignmentGeometry
            .poseAt(road.getHorizontalAlignment(), arcLen).orElseThrow();
        assertEquals(endPose.x(), edge.getCenterlinePoints().getLast().x, 0.05);
        assertEquals(endPose.y(), edge.getCenterlinePoints().getLast().y, 0.05);
        assertEquals(endBefore.x, edge.getCenterlinePoints().getLast().x, 0.5);
        assertEquals(endBefore.y, edge.getCenterlinePoints().getLast().y, 0.5);
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
        assertNotNull(kept.roadAId());
        assertNotNull(kept.roadBId());
        assertTrue(Double.isFinite(kept.stationA()));
        assertTrue(Double.isFinite(kept.stationB()));
        assertEquals(cross.getId(), kept.elevatedRoadId());
        assertNotNull(e0);
    }

    @Test
    void mr01_circularLoopSeamInsideEdge_mapReliableAcrossNativeZero() {
        RoadNetwork network = squareLoopWithMatchingHa();
        Road road = network.getRoad("loop");
        assertNotNull(road.getHorizontalAlignment());
        StationDomainMap map = RoadStationing.domainMap(network, road);
        assertTrue(map.isReliable());
        double design = RoadPlanGeometry.designLength(network, road);
        double instanceTotal = RoadPlanGeometry.instanceLength(network, road);

        double nearEndPhysical = map.instanceTotal() - 1.0;
        assertTrue(map.toNative(nearEndPhysical) > design * 0.7);
        assertTrue(map.toNative(1.0) < design * 0.3);

        // Canonical conversion across native zero (seam mid-edge): stations on either side of wrap.
        double beforeWrap = RoadStationing.toCanonicalChainage(network, road, instanceTotal - 0.5);
        double afterWrap = RoadStationing.toCanonicalChainage(network, road, 0.5);
        assertTrue(Double.isFinite(beforeWrap) && Double.isFinite(afterWrap));
        assertTrue(Math.abs(beforeWrap - afterWrap) > design * 0.25,
            "before=" + beforeWrap + " after=" + afterWrap);
        assertEquals(design, RoadStationing.toCanonicalChainage(network, road, instanceTotal), 1e-3);
        assertEquals(0.0, RoadStationing.toCanonicalChainage(network, road, 0.0), 1e-3);
    }

    @Test
    void mr02_seamMoveAndReverseEdge_physicalPointStable() {
        // Build LOOP with first edge storage-reversed from the start (stable topology).
        double radius = 20.0 / Math.PI;
        double circumference = 2.0 * Math.PI * radius;
        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(
            new Vec2d(radius, 0.0),
            Math.PI / 2.0,
            List.of(HorizontalAlignmentElement.circularArc(
                circumference, radius, TurnDirection.LEFT)));
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("loop");
        road.setTopologyMode(RoadTopologyMode.LOOP);
        road.setHorizontalAlignment(alignment);
        int sides = 4;
        double sideLen = circumference / sides;
        RoadNode[] nodes = new RoadNode[sides];
        for (int i = 0; i < sides; i++) {
            var pose = HorizontalAlignmentGeometry.poseAt(alignment, i * sideLen).orElseThrow();
            nodes[i] = network.createNode(new Vec2d(pose.x(), pose.y()));
        }
        RoadEdge reversedEdge = null;
        Vec2d probe = null;
        for (int i = 0; i < sides; i++) {
            double startNative = i * sideLen;
            double endNative = (i + 1) * sideLen;
            List<Vec2d> points = new ArrayList<>();
            for (double s = startNative; s <= endNative + 1e-9; s += 2.0) {
                var pose = HorizontalAlignmentGeometry.poseAt(alignment, Math.min(s, endNative)).orElseThrow();
                points.add(new Vec2d(pose.x(), pose.y()));
            }
            var endPose = HorizontalAlignmentGeometry.poseAt(alignment, endNative).orElseThrow();
            Vec2d endPt = new Vec2d(endPose.x(), endPose.y());
            if (points.isEmpty() || points.getLast().distance(endPt) > 1e-6) {
                points.add(endPt);
            }
            if (i == 0) {
                probe = points.get(points.size() / 2).copy();
                List<Vec2d> reversed = new ArrayList<>();
                for (int p = points.size() - 1; p >= 0; p--) {
                    reversed.add(points.get(p).copy());
                }
                reversedEdge = network.createEdge(
                    nodes[1].getId(), nodes[0].getId(), reversed, road.getId());
            } else {
                network.createEdge(
                    nodes[i].getId(), nodes[(i + 1) % sides].getId(), points, road.getId());
            }
        }
        assertNotNull(reversedEdge);
        assertNotNull(probe);
        road.setLoopSeam(RoadLoopSeam.onSegment(
            reversedEdge.getCenterlinePoints().getFirst()
                .lerp(reversedEdge.getCenterlinePoints().getLast(), 0.3),
            reversedEdge.getId(),
            0.3));

        assertTrue(RoadStationing.physicalOrientedSegment(network, road, reversedEdge.getId()).isPresent(),
            "storage-reversed edge must appear on the physical materialization walk");
        // Prefer a reliable map; if chord/projection jitter refuses it, physical queries must still work.
        StationDomainMap map = RoadStationing.domainMap(network, road);
        double chainage = RoadStationing.chainageAtPosition(network, road, probe).orElseThrow();
        assertTrue(Double.isFinite(chainage), "chainage after reverse+seam move");
        if (map.isReliable()) {
            assertTrue(Double.isFinite(map.toNative(
                RoadStationing.rotatedToPhysical(network, road, chainage))));
        }
        List<PlanCenterlineSample> samples =
            RoadPlanGeometry.resolvePhysicalEdgeCenterlineSamples(network, reversedEdge, 2.0);
        assertTrue(samples.size() >= 2);
        assertEquals(probe.x, samples.get(samples.size() / 2).position().x, 1.5);
    }

    @Test
    void mr03_repeatedCircularMaterializeDoesNotDrift() {
        RoadNetwork network = squareLoopWithMatchingHa();
        Road road = network.getRoad("loop");
        assertTrue(HorizontalAlignmentCenterlineConsistency.isMaterializable(network, road));
        RoadHorizontalAlignment ha = road.getHorizontalAlignment();
        Vec2d origin = ha.getOrigin().copy();
        double bearing = ha.getStartBearingRadians();
        int elements = ha.getElements().size();
        List<Vec2d> endsBefore = new ArrayList<>();
        for (String id : road.getOrderedSegmentIds()) {
            endsBefore.add(network.getEdge(id).getCenterlinePoints().getLast().copy());
        }
        for (int i = 0; i < 10; i++) {
            assertTrue(HorizontalAlignmentCenterlineMaterializer.materialize(network, road).isSuccess());
        }
        assertEquals(origin.x, road.getHorizontalAlignment().getOrigin().x, 1e-9);
        assertEquals(origin.y, road.getHorizontalAlignment().getOrigin().y, 1e-9);
        assertEquals(bearing, road.getHorizontalAlignment().getStartBearingRadians(), 1e-12);
        assertEquals(elements, road.getHorizontalAlignment().getElements().size());
        for (int i = 0; i < endsBefore.size(); i++) {
            Vec2d after = network.getEdge(road.getOrderedSegmentIds().get(i)).getCenterlinePoints().getLast();
            assertEquals(endsBefore.get(i).x, after.x, 0.5);
            assertEquals(endsBefore.get(i).y, after.y, 0.5);
        }
    }

    @Test
    void mr04_unreliableMapManualVa_skipsFabricatedZeroElevation() {
        RoadNetwork network = straightRoad(100.0);
        Road road = network.getRoad("main");
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 80), 0.0,
            List.of(
                HorizontalAlignmentElement.tangent(40),
                HorizontalAlignmentElement.circularArc(20, 10, TurnDirection.LEFT),
                HorizontalAlignmentElement.tangent(40))));
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 60.0),
            PointOfVerticalIntersection.of(100.0, 80.0))));
        RoadTestFixtures.confirmEditedProfileGeometry(network, road);

        assertTrue(ResolvedProfileAuthority.usesEditedProfileForGeneration(network, road));
        assertFalse(RoadStationing.domainMap(network, road).isReliable());

        RoadEdge edge = network.getEdge(road.getOrderedSegmentIds().getFirst());
        RoadNode start = network.getNode(edge.getStartNodeId());
        RoadNode end = network.getNode(edge.getEndNodeId());
        RoadGenerator generator = new RoadGenerator(
            new RoadSystemConfig("phase241-mr04"),
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        EdgeGenerationResult outcome = generator.generateEdgeOutcome(
            network, edge, start, end, new FlatTerrainSampler(64), Map.of());
        assertEquals(EdgeGenerationOutcome.FAILED, outcome.outcome(), outcome.message());
        assertTrue(outcome.message().toLowerCase().contains("domain map")
            || outcome.message().toLowerCase().contains("vertical"),
            outcome.message());

        RoadNetworkGenerator networkGenerator = new RoadNetworkGenerator(generator);
        var all = networkGenerator.generateAll(network, new FlatTerrainSampler(64));
        assertTrue(all.hasPartialFailure(), "confirmed VA + unreliable map must refuse build");
    }

    @Test
    void mr05_straightInstanceSingleArcHa_notCollinearStretch() {
        RoadNetwork network = straightRoad(100.0);
        Road road = network.getRoad("main");
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0), 0.0,
            List.of(HorizontalAlignmentElement.circularArc(
                100.0, 100.0 / (Math.PI / 2.0), TurnDirection.LEFT))));
        StationDomainMap map = RoadStationing.domainMap(network, road);
        assertFalse(map.isReliable(), "single-arc HA vs straight instance must not pass collinear stretch");
        assertTrue(Double.isNaN(map.toNative(50.0)) || !map.isReliable());
    }

    @Test
    void ambiguousCriticalAnchorWithoutMonotonicIdentityIsUnreliable() {
        // Sharp figure-eight polyline + fitted (shortened) HA: critical corners lack a unique
        // monotonic native identity → map must refuse rather than enlarge match tolerance.
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("ambiguous-eight");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        network.createEdge(
            n1.getId(),
            n2.getId(),
            List.of(
                new Vec2d(0, 0),
                new Vec2d(10, 10),
                new Vec2d(0, 10),
                new Vec2d(10, 0)),
            road.getId());
        var fitted = HorizontalAlignmentPolylineFitter.fit(network, road);
        assertTrue(fitted.isPresent());
        road.setHorizontalAlignment(fitted.get());
        HorizontalAlignmentChainOriginAligner.alignOriginAndBearingToChainStart(network, road);
        StationDomainMap map = RoadStationing.domainMap(network, road);
        assertFalse(map.isReliable(), "ambiguous/unmatched critical anchors must stay unreliable");
    }

    @Test
    void mr06_localLengtheningAndSelfCrossingPassesIndependent() {
        // Phase21 figure-eight polyline → fit HA, then resample instance onto HA so both
        // passes lie on design geometry (fit arcs shorten corners; map needs spatial match).
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("figure-eight");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadEdge edge = network.createEdge(
            n1.getId(),
            n2.getId(),
            List.of(
                new Vec2d(0, 0),
                new Vec2d(10, 10),
                new Vec2d(0, 10),
                new Vec2d(10, 0)),
            road.getId());
        var fitted = HorizontalAlignmentPolylineFitter.fit(network, road);
        assertTrue(fitted.isPresent(), "figure-eight must fit HA");
        road.setHorizontalAlignment(fitted.get());
        HorizontalAlignmentChainOriginAligner.alignOriginAndBearingToChainStart(network, road);
        RoadHorizontalAlignment ha = road.getHorizontalAlignment();
        List<Vec2d> onHa = HorizontalAlignmentGeometry.sample(ha, 1.0);
        assertTrue(onHa.size() >= 4);
        n1.setPosition(onHa.getFirst().copy());
        n2.setPosition(onHa.getLast().copy());
        edge.setCenterlinePoints(onHa);

        StationDomainMap map = RoadStationing.domainMap(network, road);
        assertTrue(map.isReliable(), "self-crossing with fitted HA must map under order constraint");
        double early = map.toNative(map.instanceTotal() * 0.2);
        double late = map.toNative(map.instanceTotal() * 0.8);
        assertTrue(late > early + 1.0, "second pass native must follow first under order constraint");
        // Two physical stations that share nearly the same XY must still resolve to distinct natives.
        Vec2d midA = RoadPlanGeometry.instancePointAtInstanceChainage(
            network, road, map.instanceTotal() * 0.25).orElseThrow();
        Vec2d midB = RoadPlanGeometry.instancePointAtInstanceChainage(
            network, road, map.instanceTotal() * 0.75).orElseThrow();
        double nativeA = map.toNative(map.instanceTotal() * 0.25);
        double nativeB = map.toNative(map.instanceTotal() * 0.75);
        assertTrue(nativeB > nativeA + 1.0);
        assertTrue(midA.distance(midB) < 8.0 || nativeB > nativeA + map.nativeTotal() * 0.3);
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

    private static List<Vec2d> sampleHaPoints(RoadHorizontalAlignment alignment, double spacing) {
        double total = com.plot.plugin.road.alignment.HorizontalAlignmentGeometry.totalLength(alignment);
        List<Vec2d> points = new ArrayList<>();
        for (double s = 0.0; s <= total + 1e-9; s += spacing) {
            double clamped = Math.min(s, total);
            var pose = com.plot.plugin.road.alignment.HorizontalAlignmentGeometry
                .poseAt(alignment, clamped).orElseThrow();
            Vec2d p = new Vec2d(pose.x(), pose.y());
            if (points.isEmpty() || points.getLast().distance(p) > 1e-6) {
                points.add(p);
            }
        }
        return points;
    }

    private static int indexNearStation(
            List<Vec2d> samples,
            RoadHorizontalAlignment alignment,
            double nativeStation) {
        var pose = com.plot.plugin.road.alignment.HorizontalAlignmentGeometry
            .poseAt(alignment, nativeStation).orElseThrow();
        Vec2d target = new Vec2d(pose.x(), pose.y());
        int best = 0;
        double bestDist = Double.POSITIVE_INFINITY;
        for (int i = 0; i < samples.size(); i++) {
            double d = samples.get(i).distance(target);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
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
        // Circle HA + physical edges sampled from it (equal length, spatially matched).
        double radius = 20.0 / Math.PI; // circumference 40
        double circumference = 2.0 * Math.PI * radius;
        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(
            new Vec2d(radius, 0.0),
            Math.PI / 2.0,
            List.of(HorizontalAlignmentElement.circularArc(
                circumference, radius, TurnDirection.LEFT)));

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("loop");
        road.setTopologyMode(RoadTopologyMode.LOOP);
        road.setHorizontalAlignment(alignment);

        int sides = 4;
        double sideLen = circumference / sides;
        RoadNode[] nodes = new RoadNode[sides];
        for (int i = 0; i < sides; i++) {
            double nativeStation = i * sideLen;
            var pose = com.plot.plugin.road.alignment.HorizontalAlignmentGeometry
                .poseAt(alignment, nativeStation).orElseThrow();
            nodes[i] = network.createNode(new Vec2d(pose.x(), pose.y()));
        }
        RoadEdge first = null;
        for (int i = 0; i < sides; i++) {
            double startNative = i * sideLen;
            double endNative = (i + 1) * sideLen;
            List<Vec2d> points = new java.util.ArrayList<>();
            for (double s = startNative; s <= endNative + 1e-9; s += 2.0) {
                double clamped = Math.min(s, endNative);
                var pose = com.plot.plugin.road.alignment.HorizontalAlignmentGeometry
                    .poseAt(alignment, clamped).orElseThrow();
                points.add(new Vec2d(pose.x(), pose.y()));
            }
            var endPose = com.plot.plugin.road.alignment.HorizontalAlignmentGeometry
                .poseAt(alignment, endNative).orElseThrow();
            Vec2d endPt = new Vec2d(endPose.x(), endPose.y());
            if (points.isEmpty() || points.getLast().distance(endPt) > 1e-6) {
                points.add(endPt);
            }
            RoadEdge edge = network.createEdge(
                nodes[i].getId(), nodes[(i + 1) % sides].getId(), points, road.getId());
            if (first == null) {
                first = edge;
            }
        }
        double seamNative = sideLen * 0.5;
        var seamPose = com.plot.plugin.road.alignment.HorizontalAlignmentGeometry
            .poseAt(alignment, seamNative).orElseThrow();
        road.setLoopSeam(RoadLoopSeam.onSegment(
            new Vec2d(seamPose.x(), seamPose.y()), first.getId(), 0.5));
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
