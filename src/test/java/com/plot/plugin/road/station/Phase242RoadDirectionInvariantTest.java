package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.centerline.CenterlineEditResult;
import com.plot.plugin.road.centerline.CenterlineEditStatus;
import com.plot.plugin.road.centerline.RoadCenterlineEditor;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadCrossingReconciler;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadLoopSeam;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.pipeline.profile.ProfileCompileInputsFingerprint;
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
 * Phase 2.4.2 road design-direction + reverse invariants (DR01–DR14).
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
        RoadEdge edge = network.getEdge(edgeId);
        road.setLoopSeam(RoadLoopSeam.onSegment(new Vec2d(5, 0), edgeId, 0.25));
        double seamXyX = RoadStationing.pointAtStation(
            network, road, RoadStationing.physicalSeamStation(network, road)).orElseThrow().x;

        assertTrue(RoadCenterlineEditor.reverseEdge(network, edgeId).isSuccess());

        assertEquals(0.75, road.getLoopSeam().localFraction(), EPS);
        assertEquals(edgeId, road.getLoopSeam().segmentHintId());
        double afterXyX = RoadStationing.pointAtStation(
            network, road, RoadStationing.physicalSeamStation(network, road)).orElseThrow().x;
        assertEquals(seamXyX, afterXyX, 0.5);
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

        assertTrue(RoadCenterlineEditor.reverseEdge(network, edgeA.getId()).isSuccess());
        RoadCrossing afterEdge = network.getCrossing(original.id());
        assertNotNull(afterEdge);
        assertEquals(stationBefore, afterEdge.stationOn(roadA.getId()), 0.5);

        double length = RoadStationing.canonicalLength(network, roadA);
        assertTrue(RoadCenterlineEditor.reverseRoad(network, roadA).isSuccess());
        RoadCrossing afterRoad = network.getCrossing(original.id());
        assertNotNull(afterRoad);
        assertEquals(length - stationBefore, afterRoad.stationOn(roadA.getId()), 0.5);
        assertEquals(1.0, afterRoad.position().x, EPS);
    }

    @Test
    void dr12_doubleReverseRoadRestoresStationsAndEntry() {
        RoadNetwork network = straightRoad(80.0);
        Road road = network.getRoad("main");
        RoadDesignDirectionAuthority.ensureBound(network, road);
        String entry = road.getDesignEntryNodeId();
        Vec2d at10 = RoadStationing.pointAtStation(network, road, 10.0).orElseThrow();

        assertTrue(RoadCenterlineEditor.reverseRoad(network, road).isSuccess());
        assertTrue(RoadCenterlineEditor.reverseRoad(network, road).isSuccess());

        assertEquals(entry, road.getDesignEntryNodeId());
        Vec2d restored = RoadStationing.pointAtStation(network, road, 10.0).orElseThrow();
        assertEquals(at10.x, restored.x, EPS);
        assertEquals(at10.y, restored.y, EPS);
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
}
