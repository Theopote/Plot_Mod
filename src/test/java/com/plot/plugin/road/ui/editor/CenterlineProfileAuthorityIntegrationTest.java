package com.plot.plugin.road.ui.editor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.centerline.CenterlineEditResult;
import com.plot.plugin.road.centerline.RoadCenterlineEditor;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.profile.ProfileCompileInputsFingerprint;
import com.plot.plugin.road.pipeline.profile.ProfileCompileOptions;
import com.plot.plugin.road.pipeline.profile.VerticalAlignmentProfileSupport;
import com.plot.plugin.road.pipeline.profile.VerticalProfileGenerationPolicy;
import com.plot.plugin.road.pipeline.profile.VoxelProfileConstraints;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3: centerline apply must keep live vertical alignment aligned with station policy,
 * and generation authority must not use stale EDITED curves after geometry changes.
 */
class CenterlineProfileAuthorityIntegrationTest {

    private static final double EPS = 1e-3;

    @Test
    void applyToLiveSyncsMirroredVerticalAlignmentAfterReverse() {
        RoadNetwork live = straightRoad("reverse-va", 100.0);
        Road road = live.getRoad("reverse-va");
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(100.0, 72.0))));

        RoadNetwork working = live.snapshot().snapshot();
        assertTrue(RoadCenterlineEditor.reverseRoad(working, working.getRoad(road.getId())).isSuccess());

        applyCenterline(live, working, road.getId());

        RoadVerticalAlignment mirrored = road.getVerticalAlignment();
        assertEquals(0.0, mirrored.getPvis().getFirst().getStation(), EPS);
        assertEquals(72.0, mirrored.getPvis().getFirst().getElevation(), EPS);
        assertEquals(100.0, mirrored.getPvis().get(1).getStation(), EPS);
        assertEquals(64.0, mirrored.getPvis().get(1).getElevation(), EPS);
    }

    @Test
    void applyToLiveSyncsRescaledVerticalAlignmentAfterFillet() {
        RoadNetwork live = new RoadNetwork();
        RoadNode n1 = live.createNode(new Vec2d(0, 0));
        RoadNode n3 = live.createNode(new Vec2d(40, 40));
        Road road = live.createRoad("fillet-va");
        String roadId = road.getId();
        RoadEdge edge = live.createEdge(
            n1.getId(),
            n3.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(40, 0), new Vec2d(40, 40)),
            roadId);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(80.0, 68.0))));

        double oldLength = com.plot.plugin.road.station.RoadStationing.canonicalLength(live, road);
        RoadNetwork working = live.snapshot().snapshot();
        assertTrue(RoadCenterlineEditor.filletVertex(working, edge.getId(), 1, 5.0).isSuccess());

        Road workingRoad = working.getRoad(roadId);
        double newLength = com.plot.plugin.road.station.RoadStationing.canonicalLength(working, workingRoad);
        double expectedEndStation = 80.0 * (newLength / oldLength);

        applyCenterline(live, working, roadId);

        RoadVerticalAlignment liveVa = road.getVerticalAlignment();
        assertEquals(0.0, liveVa.getPvis().getFirst().getStation(), EPS);
        assertEquals(expectedEndStation, liveVa.getPvis().get(1).getStation(), EPS);
        assertEquals(68.0, liveVa.getPvis().get(1).getElevation(), EPS);
    }

    @Test
    void reverseInvalidatesEditedSourceAndGenerationIgnoresStoredCurve() {
        RoadNetwork live = straightRoad("reverse-auth", 100.0);
        Road road = live.getRoad("reverse-auth");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(50.0, 80.0),
            PointOfVerticalIntersection.of(100.0, 64.0))));
        ResolvedProfileAuthority.markEditedProfileConfirmed(live, road);

        RoadNetwork working = live.snapshot().snapshot();
        assertTrue(RoadCenterlineEditor.reverseRoad(working, working.getRoad(road.getId())).isSuccess());

        applyCenterline(live, working, road.getId());
        assertTrue(ResolvedProfileAuthority.invalidateEditedProfileIfStale(live, road));

        assertEquals(ProfileSource.AUTO, road.getEffectiveProfileSource());
        assertTrue(ResolvedProfileAuthority.hasStoredEditedCurve(road));
        assertFalse(ResolvedProfileAuthority.usesEditedProfileForGeneration(live, road));

        RoadNetwork generationSnapshot = live.snapshot();
        VerticalProfileGenerationPolicy.applyGenerationProfileAuthority(generationSnapshot);
        Road generationRoad = generationSnapshot.getRoad(road.getId());
        assertFalse(VerticalAlignmentProfileSupport.shouldUseVerticalAlignment(
            generationSnapshot, generationRoad));
    }

    @Test
    void geometryHashChangesWhenCenterlineApplyMutatesPlanGeometry() {
        RoadNetwork live = straightRoad("fp", 100.0);
        Road road = live.getRoad("fp");
        ProfileCompileInputsFingerprint before = fingerprint(live, road);

        RoadNetwork working = live.snapshot().snapshot();
        RoadEdge edge = working.getEdge(working.getRoad(road.getId()).getOrderedSegmentIds().getFirst());
        edge.setCenterlinePoints(List.of(new Vec2d(0, 0), new Vec2d(80, 0)));
        working.getNode(edge.getEndNodeId()).setPosition(new Vec2d(80, 0));
        applyCenterline(live, working, road.getId());

        ProfileCompileInputsFingerprint after = fingerprint(live, road);
        assertNotEquals(before.geometryHash(), after.geometryHash());
        assertNotEquals(before, after);
    }

    @Test
    void reconfirmedEditedProfileUsesSyncedMirroredCurveForGeneration() {
        RoadNetwork live = straightRoad("reconfirm", 100.0);
        Road road = live.getRoad("reconfirm");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(100.0, 72.0))));
        ResolvedProfileAuthority.markEditedProfileConfirmed(live, road);

        RoadNetwork working = live.snapshot().snapshot();
        assertTrue(RoadCenterlineEditor.reverseRoad(working, working.getRoad(road.getId())).isSuccess());
        applyCenterline(live, working, road.getId());
        ResolvedProfileAuthority.invalidateEditedProfileIfStale(live, road);

        ResolvedProfileAuthority.markEditedProfileConfirmed(live, road);
        assertTrue(ResolvedProfileAuthority.usesEditedProfileForGeneration(live, road));

        RoadNetwork generationSnapshot = live.snapshot();
        VerticalProfileGenerationPolicy.applyGenerationProfileAuthority(generationSnapshot);
        Road generationRoad = generationSnapshot.getRoad(road.getId());
        assertTrue(VerticalAlignmentProfileSupport.shouldUseVerticalAlignment(
            generationSnapshot, generationRoad));
        assertEquals(72.0, generationRoad.getVerticalAlignment().getPvis().getFirst().getElevation(), EPS);
    }

    @Test
    void fingerprintChangesWhenEditedProfileSourceInvalidated() {
        RoadNetwork live = straightRoad("source-fp", 100.0);
        Road road = live.getRoad("source-fp");
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(100.0, 66.0))));
        ResolvedProfileAuthority.markEditedProfileConfirmed(live, road);

        ProfileCompileInputsFingerprint edited = fingerprint(live, road);

        RoadEdge edge = live.getEdge(road.getOrderedSegmentIds().getFirst());
        edge.setCenterlinePoints(List.of(new Vec2d(0, 0), new Vec2d(80, 0)));
        live.getNode(edge.getEndNodeId()).setPosition(new Vec2d(80, 0));

        assertTrue(ResolvedProfileAuthority.invalidateEditedProfileIfStale(live, road));
        ProfileCompileInputsFingerprint auto = fingerprint(live, road);

        assertNotEquals(edited, auto);
        assertEquals(ProfileSource.AUTO, road.getEffectiveProfileSource());
    }

    private static ProfileCompileInputsFingerprint fingerprint(RoadNetwork network, Road road) {
        return ProfileCompileInputsFingerprint.of(
            network,
            road,
            road.getVerticalAlignment(),
            List.of(),
            VoxelProfileConstraints.empty(),
            null,
            0L,
            new RoadSystemConfig("fp"),
            ProfileCompileOptions.defaults());
    }

    private static RoadNetwork straightRoad(String id, double length) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad(id);
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(length, 0));
        network.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(length, 0)),
            road.getId());
        return network;
    }

    private static void applyCenterline(RoadNetwork live, RoadNetwork working, String roadId) {
        CenterlineEditorApplier.applyToLive(live, working, roadId);
        com.plot.plugin.road.crossing.RoadCrossingReconciler.reconcileCrossings(live);
        com.plot.plugin.road.crossing.RoadStackCrossingReconciler.reconcileStackCrossings(live);
    }

}
