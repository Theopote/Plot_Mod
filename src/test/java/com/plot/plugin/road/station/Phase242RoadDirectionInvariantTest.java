package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.HorizontalAlignmentGeometry;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.alignment.TurnDirection;
import com.plot.plugin.road.centerline.CenterlineEditResult;
import com.plot.plugin.road.centerline.CenterlineEditStatus;
import com.plot.plugin.road.centerline.RoadCenterlineEditor;
import com.plot.plugin.road.crossing.CrossingType;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadCrossingReconciler;
import com.plot.plugin.road.crossing.RoadStackCrossing;
import com.plot.plugin.road.crossing.RoadStackCrossingReconciler;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadLoopSeam;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.model.section.Lane;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.model.section.VariableCrossSectionResolver;
import com.plot.plugin.road.pipeline.profile.ProfileCompileInputsFingerprint;
import com.plot.plugin.road.ui.editor.CenterlineEditorApplier;
import com.plot.plugin.road.ui.editor.RoadNetworkEditorSession;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2.4.2 road design-direction + reverse invariants (DR01–DR21).
 */
class Phase242RoadDirectionInvariantTest {

    private static final double EPS = 1e-3;

    @Test
    void dr01_reverseEdgePreservesPhysicalXyAlongChain() {
        RoadNetwork network = twoSegmentRoad();
        Road road = network.getRoad("main");
        List<Vec2d> before = sampleXy(network, road);

        String edgeId = road.getOrderedSegmentIds().getFirst();
        assertTrue(RoadCenterlineEditor.reverseEdge(network, edgeId).isSuccess());

        List<Vec2d> after = sampleXy(network, road);
        assertEquals(before.size(), after.size());
        for (int i = 0; i < before.size(); i++) {
            assertEquals(before.get(i).x, after.get(i).x, EPS);
            assertEquals(before.get(i).y, after.get(i).y, EPS);
        }
    }

    @Test
    void dr02_reverseEdgePreservesCanonicalStations() {
        RoadNetwork network = twoSegmentRoad();
        Road road = network.getRoad("main");
        double midBefore = 40.0;
        Vec2d atMid = RoadStationing.pointAtStation(network, road, midBefore).orElseThrow();

        assertTrue(RoadCenterlineEditor.reverseEdge(network, road.getOrderedSegmentIds().getFirst()).isSuccess());

        Vec2d after = RoadStationing.pointAtStation(network, road, midBefore).orElseThrow();
        assertEquals(atMid.x, after.x, EPS);
        assertEquals(atMid.y, after.y, EPS);
    }

    @Test
    void dr03_reverseEdgeRemapsSeamLocalFractionInGeometryStorageDomain() {
        RoadNetwork network = loopRoad();
        Road road = network.getRoad("loop");
        String edgeId = road.getOrderedSegmentIds().getFirst();
        road.setLoopSeam(RoadLoopSeam.onSegment(new Vec2d(5, 0), edgeId, 0.25));
        Vec2d seamXyBefore = RoadStationing.pointAtStation(
            network, road, RoadStationing.physicalSeamStation(network, road)).orElseThrow().copy();

        assertTrue(RoadCenterlineEditor.reverseEdge(network, edgeId).isSuccess());

        RoadLoopSeam seam = road.getLoopSeam();
        assertEquals(0.75, seam.localFraction(), EPS);
        assertEquals(edgeId, seam.segmentHintId());
        Vec2d seamXyAfter = RoadStationing.pointAtStation(
            network, road, RoadStationing.physicalSeamStation(network, road)).orElseThrow();
        assertEquals(seamXyBefore.x, seamXyAfter.x, EPS);
        assertEquals(seamXyBefore.y, seamXyAfter.y, EPS);
        assertEquals(seamXyBefore.x, seam.position().x, 0.5);
        assertEquals(seamXyBefore.y, seam.position().y, 0.5);
    }

    @Test
    void dr04_reverseEdgeDoesNotInvalidateEditedVa() {
        RoadNetwork network = straightRoad(100.0);
        Road road = network.getRoad("main");
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 60.0),
            PointOfVerticalIntersection.of(100.0, 70.0))));
        ResolvedProfileAuthority.markEditedProfileConfirmed(network, road);
        int hashBefore = ProfileCompileInputsFingerprint.geometryHash(network, road);

        assertTrue(RoadCenterlineEditor.reverseEdge(
            network, road.getOrderedSegmentIds().getFirst()).isSuccess());

        assertEquals(hashBefore, ProfileCompileInputsFingerprint.geometryHash(network, road));
        assertFalse(ResolvedProfileAuthority.invalidateEditedProfileIfStale(network, road));
        assertEquals(ProfileSource.EDITED, road.getEffectiveProfileSource());
    }

    @Test
    void dr05_reverseRoadFlipsDesignEntryBinding() {
        RoadNetwork network = straightRoad(100.0);
        Road road = network.getRoad("main");
        RoadDesignDirectionAuthority.ensureBound(network, road);
        String entryBefore = road.getDesignEntryNodeId();
        String exitBefore = RoadStationing.orientedSegments(network, road).getLast().exitNodeId();

        assertTrue(RoadCenterlineEditor.reverseRoad(network, road).isSuccess());

        assertEquals(exitBefore, road.getDesignEntryNodeId());
        assertNotEquals(entryBefore, road.getDesignEntryNodeId());
    }

    @Test
    void dr06_reverseRoadMirrorsCanonicalStations() {
        RoadNetwork network = straightRoad(100.0);
        Road road = network.getRoad("main");
        Vec2d at20 = RoadStationing.pointAtStation(network, road, 20.0).orElseThrow();

        assertTrue(RoadCenterlineEditor.reverseRoad(network, road).isSuccess());

        Vec2d at80 = RoadStationing.pointAtStation(network, road, 80.0).orElseThrow();
        assertEquals(at20.x, at80.x, EPS);
        assertEquals(at20.y, at80.y, EPS);
    }

    @Test
    void dr07_reverseRoadPreservesPhysicalXySamples() {
        RoadNetwork network = twoSegmentRoad();
        Road road = network.getRoad("main");
        List<Vec2d> before = sampleXy(network, road);

        assertTrue(RoadCenterlineEditor.reverseRoad(network, road).isSuccess());

        List<Vec2d> after = sampleXy(network, road);
        // Station walk is mirrored; compare set of sampled XY by mirroring index.
        assertEquals(before.size(), after.size());
        for (int i = 0; i < before.size(); i++) {
            Vec2d expected = before.get(i);
            Vec2d actual = after.get(before.size() - 1 - i);
            assertEquals(expected.x, actual.x, EPS);
            assertEquals(expected.y, actual.y, EPS);
        }
    }

    @Test
    void dr08_reverseRoadMirrorsVaPhysicalElevations() {
        RoadNetwork network = straightRoad(100.0);
        Road road = network.getRoad("main");
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 60.0),
            PointOfVerticalIntersection.of(40.0, 75.0),
            PointOfVerticalIntersection.of(100.0, 66.0))));

        double z40 = VerticalAlignmentGeometry.elevationAt(road.getVerticalAlignment(), 40.0).orElseThrow();
        assertTrue(RoadCenterlineEditor.reverseRoad(network, road).isSuccess());
        double z60 = VerticalAlignmentGeometry.elevationAt(road.getVerticalAlignment(), 60.0).orElseThrow();
        assertEquals(z40, z60, EPS);
    }

    @Test
    void dr09_reverseRoadKeepsEditedAfterRebind() {
        RoadNetwork network = straightRoad(100.0);
        Road road = network.getRoad("main");
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 60.0),
            PointOfVerticalIntersection.of(100.0, 70.0))));
        ResolvedProfileAuthority.markEditedProfileConfirmed(network, road);

        assertTrue(RoadCenterlineEditor.reverseRoad(network, road).isSuccess());

        assertEquals(ProfileSource.EDITED, road.getEffectiveProfileSource());
        assertFalse(ResolvedProfileAuthority.invalidateEditedProfileIfStale(network, road));
        assertEquals(
            road.getEditedGeometrySignature(),
            ProfileCompileInputsFingerprint.geometryHash(network, road));
    }

    @Test
    void dr10_reverseRoadRefusesSpiralHa() {
        RoadNetwork network = straightRoad(100.0);
        Road road = network.getRoad("main");
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0), 0.0,
            List.of(HorizontalAlignmentElement.spiral(40.0, 20.0))));

        CenterlineEditResult result = RoadCenterlineEditor.reverseRoad(network, road);
        assertFalse(result.isSuccess());
        assertEquals(CenterlineEditStatus.ALIGNMENT_STATIONS_INVALID, result.status());
        assertEquals(0.0, RoadStationing.pointAtStation(network, road, 0.0).orElseThrow().x, EPS);
    }

    @Test
    void dr11_crossingStationPreservedOnReverseEdgeMirroredOnReverseRoad() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("a");
        Road roadB = network.createRoad("b");
        RoadEdge edgeA = network.createEdge(
            network.createNode(new Vec2d(0, 5)).getId(),
            network.createNode(new Vec2d(10, 5)).getId(),
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)),
            roadA.getId());
        network.createEdge(
            network.createNode(new Vec2d(1, 0)).getId(),
            network.createNode(new Vec2d(1, 10)).getId(),
            List.of(new Vec2d(1, 0), new Vec2d(1, 10)),
            roadB.getId());
        RoadCrossingReconciler.reconcileCrossings(network);
        RoadCrossing original = network.getCrossings().values().iterator().next();
        double stationBefore = original.stationOn(roadA.getId());
        String crossingId = original.id();
        Vec2d positionBefore = original.position().copy();

        assertTrue(RoadCenterlineEditor.reverseEdge(network, edgeA.getId()).isSuccess());
        RoadCrossing afterEdge = network.getCrossing(crossingId);
        assertNotNull(afterEdge);
        assertEquals(stationBefore, afterEdge.stationOn(roadA.getId()), 0.5);
        assertEquals(positionBefore.x, afterEdge.position().x, 0.5);
        assertEquals(positionBefore.y, afterEdge.position().y, 0.5);

        double length = RoadStationing.canonicalLength(network, roadA);
        assertTrue(RoadCenterlineEditor.reverseRoad(network, roadA).isSuccess());
        RoadCrossing afterRoad = network.getCrossing(crossingId);
        assertNotNull(afterRoad);
        assertEquals(length - stationBefore, afterRoad.stationOn(roadA.getId()), 0.5);
        assertEquals(1.0, afterRoad.position().x, EPS);
        assertEquals(crossingId, afterRoad.id());
    }

    @Test
    void dr12_doubleReverseRoadRestoresStationsAndEntry() {
        RoadNetwork network = straightRoad(80.0);
        Road road = network.getRoad("main");
        RoadCrossSection asymmetric = asymmetricSection(3, 4, 5);
        road.setCrossSection(asymmetric.copy());
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(40.0, asymmetricSection(6, 7, 8)))));
        RoadDesignDirectionAuthority.ensureBound(network, road);
        String entry = road.getDesignEntryNodeId();
        Vec2d at10 = RoadStationing.pointAtStation(network, road, 10.0).orElseThrow();
        List<Integer> defaultLanes = laneWidths(road.getCrossSection());
        List<Integer> vcsLanes = laneWidths(VariableCrossSectionResolver.resolveTemplate(road, 50.0));

        assertTrue(RoadCenterlineEditor.reverseRoad(network, road).isSuccess());
        assertTrue(RoadCenterlineEditor.reverseRoad(network, road).isSuccess());

        assertEquals(entry, road.getDesignEntryNodeId());
        Vec2d restored = RoadStationing.pointAtStation(network, road, 10.0).orElseThrow();
        assertEquals(at10.x, restored.x, EPS);
        assertEquals(at10.y, restored.y, EPS);
        assertEquals(defaultLanes, laneWidths(road.getCrossSection()));
        assertEquals(vcsLanes, laneWidths(VariableCrossSectionResolver.resolveTemplate(road, 50.0)));
    }

    @Test
    void dr13_jsonRoundTripPreservesDesignEntryAndSeam() {
        RoadNetwork network = loopRoad();
        Road road = network.getRoad("loop");
        RoadDesignDirectionAuthority.ensureBound(network, road);
        String edgeId = road.getDesignEntryEdgeId();
        road.setLoopSeam(RoadLoopSeam.onSegment(new Vec2d(5, 0), edgeId, 0.4));
        String entry = road.getDesignEntryNodeId();

        RoadNetwork restored = network.snapshot();
        // Round-trip through JSON as well (snapshot is memory copy; JSON is persistence).
        try {
            restored = RoadNetwork.fromJson(network.toJson());
        } catch (com.plot.plugin.road.model.RoadNetworkFormatException ex) {
            throw new AssertionError(ex);
        }
        Road restoredRoad = restored.getRoad("loop");
        assertEquals(entry, restoredRoad.getDesignEntryNodeId());
        assertEquals(edgeId, restoredRoad.getDesignEntryEdgeId());
        assertEquals(0.4, restoredRoad.getLoopSeam().localFraction(), EPS);
    }

    @Test
    void dr14_spiralPreflightLeavesNetworkUnchanged_andManagerUndoRestores() {
        RoadNetworkManager manager = new RoadNetworkManager(
            new RoadSystemConfig("test"), new RoadProjectStatus());
        RoadNetwork network = manager.getNetwork();
        Road road = network.createRoad("main");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(100, 0));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0), 0.0,
            List.of(HorizontalAlignmentElement.spiral(50.0, 25.0))));
        RoadDesignDirectionAuthority.ensureBound(network, road);
        String entry = road.getDesignEntryNodeId();
        String jsonBefore = network.toJson();

        assertFalse(manager.reverseRoad(road).isSuccess());
        assertEquals(entry, road.getDesignEntryNodeId());
        assertEquals(jsonBefore, network.toJson());

        // Real migrate failure (uncapturable crossing) also leaves live unchanged.
        RoadNetworkManager migrateManager = new RoadNetworkManager(
            new RoadSystemConfig("test"), new RoadProjectStatus());
        RoadNetwork migrateNetwork = migrateManager.getNetwork();
        Road migrateRoad = migrateNetwork.createRoad("main");
        RoadNode ma = migrateNetwork.createNode(new Vec2d(0, 0));
        RoadNode mb = migrateNetwork.createNode(new Vec2d(100, 0));
        migrateNetwork.createEdge(
            ma.getId(), mb.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), migrateRoad.getId());
        migrateNetwork.registerCrossing(RoadCrossing.atGrade(
            migrateRoad.getId(), 999.0, "missing-b", 1.0, new Vec2d(50, 0)));
        String jsonWithBadCrossing = migrateNetwork.toJson();
        CenterlineEditResult migrateFail = migrateManager.reverseRoad(migrateRoad);
        assertFalse(migrateFail.isSuccess());
        assertEquals(CenterlineEditStatus.CROSSING_MIGRATION_FAILED, migrateFail.status());
        assertEquals(jsonWithBadCrossing, migrateNetwork.toJson());

        // Successful reverse is undoable via manager history.
        RoadNetworkManager undoManager = new RoadNetworkManager(
            new RoadSystemConfig("test"), new RoadProjectStatus());
        RoadNetwork undoNetwork = undoManager.getNetwork();
        Road undoRoad = undoNetwork.createRoad("main");
        RoadNode ua = undoNetwork.createNode(new Vec2d(0, 0));
        RoadNode ub = undoNetwork.createNode(new Vec2d(100, 0));
        undoNetwork.createEdge(
            ua.getId(), ub.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), undoRoad.getId());
        RoadDesignDirectionAuthority.ensureBound(undoNetwork, undoRoad);
        String entryOk = undoRoad.getDesignEntryNodeId();
        assertTrue(undoManager.reverseRoad(undoRoad).isSuccess());
        assertNotEquals(entryOk, undoManager.getNetwork().getRoad(undoRoad.getId()).getDesignEntryNodeId());
        assertTrue(undoManager.canUndo());
        undoManager.undo();
        assertEquals(entryOk, undoManager.getNetwork().getRoad(undoRoad.getId()).getDesignEntryNodeId());
    }

    @Test
    void dr15_asymmetricDefaultAndVcsPreservePhysicalLaneLayout() {
        RoadNetwork network = straightRoad(100.0);
        Road road = network.getRoad("main");
        RoadCrossSection defaultSection = asymmetricSection(3, 4, 5);
        RoadCrossSection vcsSection = asymmetricSection(6, 7, 8);
        road.setCrossSection(defaultSection.copy());
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(40.0, vcsSection.copy()))));

        List<Integer> defaultBefore = laneWidths(road.getCrossSection());
        List<Integer> earlyBefore = laneWidths(VariableCrossSectionResolver.resolveTemplate(road, 20.0));
        List<Integer> lateBefore = laneWidths(VariableCrossSectionResolver.resolveTemplate(road, 60.0));
        double length = RoadStationing.canonicalLength(network, road);

        assertTrue(RoadCenterlineEditor.reverseRoad(network, road).isSuccess());

        assertEquals(reversed(defaultBefore), laneWidths(road.getCrossSection()));
        assertEquals(reversed(earlyBefore),
            laneWidths(VariableCrossSectionResolver.resolveTemplate(road, length - 20.0)));
        assertEquals(reversed(lateBefore),
            laneWidths(VariableCrossSectionResolver.resolveTemplate(road, length - 60.0)));

        assertTrue(RoadCenterlineEditor.reverseRoad(network, road).isSuccess());
        assertEquals(defaultBefore, laneWidths(road.getCrossSection()));
        assertEquals(earlyBefore, laneWidths(VariableCrossSectionResolver.resolveTemplate(road, 20.0)));
        assertEquals(lateBefore, laneWidths(VariableCrossSectionResolver.resolveTemplate(road, 60.0)));
    }

    @Test
    void dr16_pickSeamOnStorageReversedEdgePersistsGeometryFraction() {
        RoadNetworkManager manager = new RoadNetworkManager(
            new RoadSystemConfig("test"), new RoadProjectStatus());
        RoadNetwork network = manager.getNetwork();
        Road road = network.createRoad("loop");
        road.setTopologyMode(RoadTopologyMode.LOOP);
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(10, 10));
        RoadNode d = network.createNode(new Vec2d(0, 10));
        RoadEdge edgeAb = network.createEdge(
            a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());
        network.createEdge(c.getId(), d.getId(), List.of(new Vec2d(10, 10), new Vec2d(0, 10)), road.getId());
        network.createEdge(d.getId(), a.getId(), List.of(new Vec2d(0, 10), new Vec2d(0, 0)), road.getId());

        assertTrue(manager.reverseEdge(edgeAb.getId()).isSuccess());
        Vec2d click = new Vec2d(7.5, 0.0);
        RoadLoopSeamService.SeamProjection projection =
            RoadLoopSeamService.projectForPick(network, road, click);
        assertNotNull(projection);
        assertEquals(edgeAb.getId(), projection.segmentId());

        manager.beginLoopSeamCanvasPick(road.getId());
        assertTrue(manager.tryApplyLoopSeamPick(click));
        RoadLoopSeam seam = network.getRoad(road.getId()).getLoopSeam();
        assertNotNull(seam);
        assertEquals(edgeAb.getId(), seam.segmentHintId());
        assertEquals(projection.geometryLocalFraction(), seam.localFraction(), EPS);
        assertEquals(click.x, seam.position().x, 0.25);
        assertEquals(click.y, seam.position().y, 0.25);
    }

    @Test
    void dr17_uncapturableCrossingMigrateFailsAndLeavesLiveUnchanged() {
        RoadNetwork network = twoSegmentRoad();
        Road road = network.getRoad("main");
        String edgeId = road.getOrderedSegmentIds().getFirst();
        network.registerCrossing(RoadCrossing.atGrade(
            road.getId(), 999.0, "ghost", 1.0, new Vec2d(25, 0)));
        String jsonBefore = network.toJson();

        CenterlineEditResult edgeFail = RoadCenterlineEditor.reverseEdge(network, edgeId);
        assertFalse(edgeFail.isSuccess());
        assertEquals(CenterlineEditStatus.CROSSING_MIGRATION_FAILED, edgeFail.status());
        assertEquals(jsonBefore, network.toJson());

        CenterlineEditResult roadFail = RoadCenterlineEditor.reverseRoad(network, road);
        assertFalse(roadFail.isSuccess());
        assertEquals(CenterlineEditStatus.CROSSING_MIGRATION_FAILED, roadFail.status());
        assertEquals(jsonBefore, network.toJson());
    }

    @Test
    void dr18_curvedHaReverseKeepsCanonicalInstanceNativePhysicalXy() {
        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(
            new Vec2d(0, 0), 0.0,
            List.of(
                HorizontalAlignmentElement.tangent(20.0),
                HorizontalAlignmentElement.circularArc(Math.PI / 2.0 * 20.0, 20.0, TurnDirection.LEFT),
                HorizontalAlignmentElement.tangent(20.0)));
        // Dense HA sampling keeps instance polyline near the design curve.
        List<Vec2d> points = sampleHaPoints(alignment, 0.5);
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("curve");
        RoadNode start = network.createNode(points.getFirst().copy());
        RoadNode end = network.createNode(points.getLast().copy());
        network.createEdge(start.getId(), end.getId(), points, road.getId());
        road.setHorizontalAlignment(alignment);

        StationDomainMap mapBefore = RoadStationing.domainMap(network, road);
        assertTrue(mapBefore.isReliable());
        double length = RoadStationing.canonicalLength(network, road);
        double[] samples = {10.0, length * 0.35, length * 0.65};
        List<Vec2d> beforeXy = new ArrayList<>();
        for (double s : samples) {
            Vec2d canonicalXy = RoadStationing.pointAtStation(network, road, s).orElseThrow();
            Vec2d instanceXy = RoadStationing.instancePointAtStation(network, road, s).orElseThrow();
            double instance = RoadStationing.toInstanceChainage(network, road, s);
            double nativeStation = mapBefore.toNative(instance);
            Vec2d nativeXy = HorizontalAlignmentGeometry.poseAt(alignment, nativeStation)
                .map(pose -> new Vec2d(pose.x(), pose.y())).orElseThrow();
            assertEquals(canonicalXy.x, instanceXy.x, 1.0);
            assertEquals(canonicalXy.y, instanceXy.y, 1.0);
            assertEquals(canonicalXy.x, nativeXy.x, 1.0);
            assertEquals(canonicalXy.y, nativeXy.y, 1.0);
            beforeXy.add(canonicalXy.copy());
        }

        assertTrue(RoadCenterlineEditor.reverseRoad(network, road).isSuccess());
        StationDomainMap mapAfter = RoadStationing.domainMap(network, road);
        assertTrue(mapAfter.isReliable());
        double lengthAfter = RoadStationing.canonicalLength(network, road);
        for (int i = 0; i < samples.length; i++) {
            double mirrored = lengthAfter - samples[i];
            Vec2d canonicalXy = RoadStationing.pointAtStation(network, road, mirrored).orElseThrow();
            Vec2d instanceXy = RoadStationing.instancePointAtStation(network, road, mirrored).orElseThrow();
            double instance = RoadStationing.toInstanceChainage(network, road, mirrored);
            double nativeStation = mapAfter.toNative(instance);
            Vec2d nativeXy = HorizontalAlignmentGeometry.poseAt(
                    road.getHorizontalAlignment(), nativeStation)
                .map(pose -> new Vec2d(pose.x(), pose.y())).orElseThrow();
            assertEquals(beforeXy.get(i).x, canonicalXy.x, 1.0);
            assertEquals(beforeXy.get(i).y, canonicalXy.y, 1.0);
            assertEquals(canonicalXy.x, instanceXy.x, 1.0);
            assertEquals(canonicalXy.y, instanceXy.y, 1.0);
            assertEquals(canonicalXy.x, nativeXy.x, 1.0);
            assertEquals(canonicalXy.y, nativeXy.y, 1.0);
        }
    }

    @Test
    void dr18b_opposedHaOriginMapsCanonicalToMirroredNative() {
        // Design entry at A(0,0); HA native origin at B(100,0) walking toward A.
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("opposed");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(100, 0));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(100, 0), Math.PI,
            List.of(HorizontalAlignmentElement.tangent(100.0))));
        RoadDesignDirectionAuthority.ensureBound(network, road);
        assertEquals(a.getId(), road.getDesignEntryNodeId());
        assertTrue(RoadStationing.isPhysicalWalkOpposedToDesign(network, road));

        double nativeAt25 = com.plot.plugin.road.alignment.RoadPlanGeometry.haNativeStation(
            network, road, 25.0);
        assertEquals(75.0, nativeAt25, 1e-3);
        Vec2d canonicalXy = RoadStationing.pointAtStation(network, road, 25.0).orElseThrow();
        assertEquals(25.0, canonicalXy.x, 0.5);
        assertEquals(0.0, canonicalXy.y, 0.5);
        double bearing = com.plot.plugin.road.alignment.RoadPlanGeometry.bearingAtStation(
            network, road, 25.0).orElseThrow();
        // Design direction A→B is +x (bearing ~0), even though HA native walks B→A.
        assertEquals(0.0, Math.atan2(Math.sin(bearing), Math.cos(bearing)), 0.2);
    }

    @Test
    void dr19_gradeSeparatedAndFigureEightStackPreservePassIdentity() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("a");
        Road roadB = network.createRoad("b");
        RoadEdge edgeA = network.createEdge(
            network.createNode(new Vec2d(0, 5)).getId(),
            network.createNode(new Vec2d(20, 5)).getId(),
            List.of(new Vec2d(0, 5), new Vec2d(20, 5)),
            roadA.getId());
        network.createEdge(
            network.createNode(new Vec2d(10, 0)).getId(),
            network.createNode(new Vec2d(10, 10)).getId(),
            List.of(new Vec2d(10, 0), new Vec2d(10, 10)),
            roadB.getId());
        RoadCrossingReconciler.reconcileCrossings(network);
        RoadCrossing crossing = network.getCrossings().values().iterator().next();
        network.setCrossingGradeSeparation(
            crossing.id(), CrossingType.GRADE_SEPARATED, roadA.getId(), 4.5);
        String crossingId = crossing.id();

        // Real figure-eight self-intersection (same geometry as Phase22).
        Road eight = network.createRoad("figure-eight");
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
            eight.getId());
        RoadStackCrossingReconciler.reconcileStackCrossings(network);
        assertFalse(network.getStackCrossings().isEmpty());
        RoadStackCrossing stack = network.getStackCrossings().values().iterator().next();
        network.setStackCrossingGradeSeparation(
            stack.id(), CrossingType.GRADE_SEPARATED, stack.stationA(), stack.stationB(), 3.0);
        stack = network.getStackCrossing(stack.id());
        String stackId = stack.id();
        Vec2d passAXy = RoadStationing.pointAtStation(network, eight, stack.stationA()).orElseThrow().copy();
        Vec2d passBXy = RoadStationing.pointAtStation(network, eight, stack.stationB()).orElseThrow().copy();
        double lowerBefore = stack.resolvedLowerStation();
        double upperBefore = stack.resolvedUpperStation();
        double eightLength = RoadStationing.canonicalLength(network, eight);

        assertTrue(RoadCenterlineEditor.reverseEdge(network, edgeA.getId()).isSuccess());
        RoadCrossing afterEdge = network.getCrossing(crossingId);
        assertNotNull(afterEdge);
        assertEquals(CrossingType.GRADE_SEPARATED, afterEdge.type());
        assertEquals(roadA.getId(), afterEdge.elevatedRoadId());
        assertEquals(4.5, afterEdge.crossingClearance(), EPS);

        assertTrue(RoadCenterlineEditor.reverseRoad(network, roadA).isSuccess());
        RoadCrossing afterRoad = network.getCrossing(crossingId);
        assertEquals(CrossingType.GRADE_SEPARATED, afterRoad.type());
        assertEquals(roadA.getId(), afterRoad.elevatedRoadId());

        assertTrue(RoadCenterlineEditor.reverseRoad(network, eight).isSuccess());
        RoadStackCrossing afterStack = network.getStackCrossing(stackId);
        assertNotNull(afterStack);
        assertEquals(stackId, afterStack.id());
        assertEquals(CrossingType.GRADE_SEPARATED, afterStack.type());
        assertEquals(3.0, afterStack.crossingClearance(), EPS);
        assertEquals(eightLength - lowerBefore, afterStack.resolvedLowerStation(), 1.0);
        assertEquals(eightLength - upperBefore, afterStack.resolvedUpperStation(), 1.0);
        Vec2d afterLowerXy = RoadStationing.pointAtStation(
            network, eight, afterStack.resolvedLowerStation()).orElseThrow();
        Vec2d afterUpperXy = RoadStationing.pointAtStation(
            network, eight, afterStack.resolvedUpperStation()).orElseThrow();
        // Both physical passes keep world identity (order may swap with L-s).
        assertTrue(
            (afterLowerXy.distance(passAXy) < 1.5 && afterUpperXy.distance(passBXy) < 1.5)
                || (afterLowerXy.distance(passBXy) < 1.5 && afterUpperXy.distance(passAXy) < 1.5),
            "figure-eight reverse must preserve both pass world positions");
    }

    @Test
    void dr22_loopHaReversePreservesInteriorSeamPhysicalXy() {
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
        String seamEdgeId = null;
        for (int i = 0; i < sides; i++) {
            double startNative = i * sideLen;
            double endNative = (i + 1) * sideLen;
            List<Vec2d> points = new ArrayList<>();
            for (double s = startNative; s <= endNative + 1e-9; s += 1.0) {
                var pose = HorizontalAlignmentGeometry.poseAt(alignment, Math.min(s, endNative)).orElseThrow();
                points.add(new Vec2d(pose.x(), pose.y()));
            }
            var endPose = HorizontalAlignmentGeometry.poseAt(alignment, endNative).orElseThrow();
            Vec2d endPt = new Vec2d(endPose.x(), endPose.y());
            if (points.isEmpty() || points.getLast().distance(endPt) > 1e-6) {
                points.add(endPt);
            }
            RoadEdge edge = network.createEdge(
                nodes[i].getId(), nodes[(i + 1) % sides].getId(), points, road.getId());
            if (i == 0) {
                seamEdgeId = edge.getId();
            }
        }
        Vec2d seamPos = HorizontalAlignmentGeometry.poseAt(alignment, sideLen * 0.4)
            .map(pose -> new Vec2d(pose.x(), pose.y())).orElseThrow();
        road.setLoopSeam(RoadLoopSeam.onSegment(seamPos, seamEdgeId, 0.4));
        RoadDesignDirectionAuthority.ensureBound(network, road);
        Vec2d seamXy = RoadStationing.pointAtStation(
            network, road, RoadStationing.physicalSeamStation(network, road)).orElseThrow().copy();

        assertTrue(RoadCenterlineEditor.reverseRoad(network, road).isSuccess());
        Vec2d seamAfter = RoadStationing.pointAtStation(
            network, road, RoadStationing.physicalSeamStation(network, road)).orElseThrow();
        assertEquals(seamXy.x, seamAfter.x, 1.0);
        assertEquals(seamXy.y, seamAfter.y, 1.0);
        double length = RoadStationing.canonicalLength(network, road);
        Vec2d at0 = RoadStationing.pointAtStation(network, road, 0.0).orElseThrow();
        Vec2d atL = RoadStationing.pointAtStation(network, road, length).orElseThrow();
        assertEquals(at0.x, atL.x, 1.0);
        assertEquals(at0.y, atL.y, 1.0);
    }

    @Test
    void dr23_asymmetricEditedVaPreviewEquivalentAfterReverse() {
        RoadNetwork network = straightRoad(48.0);
        Road road = network.getRoad("main");
        road.setCrossSection(asymmetricSection(3, 4, 5));
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(24.0, 66.0),
            PointOfVerticalIntersection.of(48.0, 64.0))));
        ResolvedProfileAuthority.markEditedProfileConfirmed(network, road);

        List<Vec2d> beforeXy = sampleXy(network, road);
        List<Double> beforeZ = new ArrayList<>();
        for (int i = 0; i < beforeXy.size(); i++) {
            double s = RoadStationing.canonicalLength(network, road) * i / (beforeXy.size() - 1);
            beforeZ.add(VerticalAlignmentGeometry.elevationAt(road.getVerticalAlignment(), s).orElseThrow());
        }

        assertTrue(RoadCenterlineEditor.reverseRoad(network, road).isSuccess());
        double length = RoadStationing.canonicalLength(network, road);
        for (int i = 0; i < beforeXy.size(); i++) {
            double mirrored = length * (beforeXy.size() - 1 - i) / (beforeXy.size() - 1);
            Vec2d xy = RoadStationing.pointAtStation(network, road, mirrored).orElseThrow();
            assertEquals(beforeXy.get(i).x, xy.x, 0.5);
            assertEquals(beforeXy.get(i).y, xy.y, 0.5);
            double z = VerticalAlignmentGeometry.elevationAt(road.getVerticalAlignment(), mirrored).orElseThrow();
            assertEquals(beforeZ.get(i), z, EPS);
        }
        assertEquals(List.of(5, 4, 3), laneWidths(road.getCrossSection()));
        assertTrue(ResolvedProfileAuthority.hasValidEditedProfile(network, road));
    }

    @Test
    void dr20_staleEditedMustNotReactivateOnReverseRoad() {
        RoadNetwork network = straightRoad(100.0);
        Road road = network.getRoad("main");
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 60.0),
            PointOfVerticalIntersection.of(100.0, 70.0))));
        road.setProfileSource(ProfileSource.EDITED);
        road.setEditedGeometrySignature(42);
        assertFalse(ResolvedProfileAuthority.hasValidEditedProfile(network, road));

        assertTrue(RoadCenterlineEditor.reverseRoad(network, road).isSuccess());

        assertFalse(ResolvedProfileAuthority.hasValidEditedProfile(network, road));
        assertNotEquals(
            ProfileCompileInputsFingerprint.geometryHash(network, road),
            road.getEditedGeometrySignature());
    }

    @Test
    void dr21_floatingEditorReverseApplyUndoRedoRestoresState() {
        RoadNetworkManager manager = new RoadNetworkManager(
            new RoadSystemConfig("test"), new RoadProjectStatus());
        RoadNetwork live = manager.getNetwork();
        Road road = live.createRoad("main");
        RoadNode a = live.createNode(new Vec2d(0, 0));
        RoadNode b = live.createNode(new Vec2d(100, 0));
        live.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());
        road.setLoopSeam(null);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 55.0),
            PointOfVerticalIntersection.of(100.0, 65.0))));
        ResolvedProfileAuthority.markEditedProfileConfirmed(live, road);
        Road spur = live.createRoad("spur");
        live.createEdge(
            live.createNode(new Vec2d(40, -10)).getId(),
            live.createNode(new Vec2d(40, 10)).getId(),
            List.of(new Vec2d(40, -10), new Vec2d(40, 10)),
            spur.getId());
        RoadCrossingReconciler.reconcileCrossings(live);
        assertFalse(live.getCrossings().isEmpty());
        RoadCrossing crossing = live.getCrossings().values().iterator().next();
        String crossingId = crossing.id();
        double crossingStation = crossing.stationOn(road.getId());
        RoadDesignDirectionAuthority.ensureBound(live, road);
        String entry = road.getDesignEntryNodeId();
        int profileSig = road.getEditedGeometrySignature();

        RoadNetworkEditorSession session = RoadNetworkEditorSession.open(
            live,
            manager.getNetworkRevision(),
            (open, working) -> CenterlineEditorApplier.hasChanges(open, working, road.getId()));
        session.pushUndoCheckpoint();
        assertTrue(RoadCenterlineEditor.reverseRoad(
            session.workingNetwork(), session.workingNetwork().getRoad(road.getId())).isSuccess());

        manager.pushUndoSnapshot();
        CenterlineEditorApplier.applyToLive(live, session.workingNetwork(), road.getId());
        manager.observeReconcileAfterExternalEdit();
        manager.commitNetworkChange();

        Road liveRoad = live.getRoad(road.getId());
        assertNotEquals(entry, liveRoad.getDesignEntryNodeId());
        RoadCrossing afterApply = live.getCrossing(crossingId);
        assertNotNull(afterApply);
        double length = RoadStationing.canonicalLength(live, liveRoad);
        assertEquals(length - crossingStation, afterApply.stationOn(road.getId()), 0.75);
        assertEquals(ProfileSource.EDITED, liveRoad.getEffectiveProfileSource());
        assertTrue(ResolvedProfileAuthority.hasValidEditedProfile(live, liveRoad));

        assertTrue(manager.canUndo());
        manager.undo();
        RoadNetwork undoneNetwork = manager.getNetwork();
        Road undone = undoneNetwork.getRoad(road.getId());
        assertEquals(entry, undone.getDesignEntryNodeId());
        RoadCrossing undoneCrossing = undoneNetwork.getCrossing(crossingId);
        assertNotNull(undoneCrossing);
        assertEquals(crossingStation, undoneCrossing.stationOn(road.getId()), 0.75);
        assertEquals(profileSig, undone.getEditedGeometrySignature());

        assertTrue(manager.canRedo());
        manager.redo();
        RoadNetwork redoneNetwork = manager.getNetwork();
        Road redone = redoneNetwork.getRoad(road.getId());
        assertNotEquals(entry, redone.getDesignEntryNodeId());
        RoadCrossing redoneCrossing = redoneNetwork.getCrossing(crossingId);
        assertNotNull(redoneCrossing);
        assertEquals(
            RoadStationing.canonicalLength(redoneNetwork, redone) - crossingStation,
            redoneCrossing.stationOn(road.getId()),
            0.75);
        assertEquals(ProfileSource.EDITED, redone.getEffectiveProfileSource());
    }

    private static RoadNetwork straightRoad(double length) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(length, 0));
        network.createEdge(start.getId(), end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(length, 0)), road.getId());
        return network;
    }

    private static RoadNetwork twoSegmentRoad() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(50, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(50, 0), new Vec2d(100, 0)), road.getId());
        return network;
    }

    private static RoadNetwork loopRoad() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("loop");
        road.setTopologyMode(RoadTopologyMode.LOOP);
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(10, 10));
        RoadNode d = network.createNode(new Vec2d(0, 10));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());
        network.createEdge(c.getId(), d.getId(), List.of(new Vec2d(10, 10), new Vec2d(0, 10)), road.getId());
        network.createEdge(d.getId(), a.getId(), List.of(new Vec2d(0, 10), new Vec2d(0, 0)), road.getId());
        return network;
    }

    private static List<Vec2d> sampleXy(RoadNetwork network, Road road) {
        double length = RoadStationing.canonicalLength(network, road);
        List<Vec2d> samples = new ArrayList<>();
        int count = 11;
        for (int i = 0; i < count; i++) {
            double s = length * i / (count - 1);
            samples.add(RoadStationing.pointAtStation(network, road, s).orElseThrow().copy());
        }
        return samples;
    }

    private static RoadCrossSection asymmetricSection(int w0, int w1, int w2) {
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setLaneCount(3);
        section.getCarriageway().setLanes(List.of(
            new Lane(w0, null),
            new Lane(w1, null),
            new Lane(w2, null)));
        return section;
    }

    private static List<Integer> laneWidths(RoadCrossSection section) {
        List<Integer> widths = new ArrayList<>();
        if (section == null) {
            return widths;
        }
        for (Lane lane : section.getCarriageway().getLanes()) {
            widths.add(lane.getWidth());
        }
        return widths;
    }

    private static List<Integer> reversed(List<Integer> widths) {
        List<Integer> copy = new ArrayList<>(widths);
        java.util.Collections.reverse(copy);
        return copy;
    }

    private static List<Vec2d> sampleHaPoints(RoadHorizontalAlignment alignment, double spacing) {
        double total = HorizontalAlignmentGeometry.totalLength(alignment);
        List<Vec2d> points = new ArrayList<>();
        for (double s = 0.0; s <= total + 1e-9; s += spacing) {
            double clamped = Math.min(s, total);
            var pose = HorizontalAlignmentGeometry.poseAt(alignment, clamped).orElseThrow();
            Vec2d p = new Vec2d(pose.x(), pose.y());
            if (points.isEmpty() || points.getLast().distance(p) > 1e-6) {
                points.add(p);
            }
        }
        return points;
    }
}
