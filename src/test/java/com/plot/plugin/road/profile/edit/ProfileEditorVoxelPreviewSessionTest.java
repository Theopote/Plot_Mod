package com.plot.plugin.road.profile.edit;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.BuildHeightSample;
import com.plot.plugin.road.pipeline.profile.BuildHeightSequenceSmoother;
import com.plot.plugin.road.pipeline.profile.ProfileCompileOptions;
import com.plot.plugin.road.pipeline.profile.VoxelProfileCompileResult;
import com.plot.plugin.road.pipeline.profile.VoxelProfileConstraints;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileEditorVoxelPreviewSessionTest {

    @Test
    void previewCompilationDoesNotMutateLiveNetwork() {
        RoadNetwork live = networkWithRoad("r1");
        ProfileEditorWindowSession session = ProfileEditorWindowSession.open(live, 1L);
        Road working = session.editingNetwork().getRoad("r1");
        working.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            new PointOfVerticalIntersection(0.0, 65.0),
            new PointOfVerticalIntersection(30.0, 65.0))));

        session.storeVoxelPreview("r1", keyFor(session.editingNetwork(), working), readyResult());

        assertEquals(64.0, live.getRoad("r1").getVerticalAlignment().getPvis().getFirst().getElevation());
        assertTrue(session.voxelPreview("r1").isReady());
    }

    @Test
    void cancelDiscardsCompiledPreview() {
        RoadNetwork live = networkWithRoad("r1");
        ProfileEditorWindowSession session = ProfileEditorWindowSession.open(live, 1L);
        Road road = session.editingNetwork().getRoad("r1");
        session.storeVoxelPreview("r1", keyFor(session.editingNetwork(), road), readyResult());
        assertTrue(session.voxelPreview("r1").isReady());

        session.cancel();
        assertEquals(VoxelProfilePreviewState.NOT_COMPUTED, session.voxelPreview("r1").state());
        assertFalse(session.voxelPreview("r1").isReady());
    }

    @Test
    void restoreInvalidatesEditedPreview() {
        RoadNetwork live = networkWithRoad("r1");
        ProfileEditorWindowSession session = ProfileEditorWindowSession.open(live, 1L);
        Road road = session.editingNetwork().getRoad("r1");
        session.storeVoxelPreview("r1", keyFor(session.editingNetwork(), road), readyResult());
        session.restore();
        assertEquals(VoxelProfilePreviewState.STALE, session.voxelPreview("r1").state());
    }

    @Test
    void pviElevationChangeInvalidatesVoxelPreview() {
        RoadNetwork live = networkWithRoad("r1");
        ProfileEditorWindowSession session = ProfileEditorWindowSession.open(live, 1L);
        Road road = session.editingNetwork().getRoad("r1");
        session.storeVoxelPreview("r1", keyFor(session.editingNetwork(), road), readyResult());
        session.markVoxelPreviewStale("r1");
        assertTrue(session.voxelPreview("r1").isStale());
    }

    @Test
    void switchingRoadDoesNotReuseOtherRoadPreview() {
        RoadNetwork live = networkWithRoad("r1");
        Road road2 = live.createRoad("r2");
        road2.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road2.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            new PointOfVerticalIntersection(0.0, 64.0),
            new PointOfVerticalIntersection(30.0, 64.0))));
        RoadNode a = live.getNodes().values().iterator().next();
        RoadNode b = live.createNode(new Vec2d(60, 0));
        live.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), "r2");

        ProfileEditorWindowSession session = ProfileEditorWindowSession.open(live, 1L);
        Road road1 = session.editingNetwork().getRoad("r1");
        session.storeVoxelPreview("r1", keyFor(session.editingNetwork(), road1), readyResult());

        VoxelProfilePreviewSlot slotR2 = session.voxelPreview("r2");
        assertEquals(VoxelProfilePreviewState.NOT_COMPUTED, slotR2.state());
        assertNotSame(session.voxelPreview("r1"), slotR2);
        assertTrue(session.voxelPreview("r1").isReady());
    }

    @Test
    void hoverDoesNotInvalidateVoxelPreview() {
        RoadNetwork live = networkWithRoad("r1");
        ProfileEditorWindowSession session = ProfileEditorWindowSession.open(live, 1L);
        Road road = session.editingNetwork().getRoad("r1");
        session.storeVoxelPreview("r1", keyFor(session.editingNetwork(), road), readyResult());
        assertTrue(session.voxelPreview("r1").isReady());
    }

    @Test
    void failedCompileKeepsLastGoodPreviewStale() {
        RoadNetwork live = networkWithRoad("r1");
        ProfileEditorWindowSession session = ProfileEditorWindowSession.open(live, 1L);
        Road road = session.editingNetwork().getRoad("r1");
        VoxelProfilePreviewKey key = keyFor(session.editingNetwork(), road);
        session.storeVoxelPreview("r1", key, readyResult());
        assertTrue(session.voxelPreview("r1").isReady());

        session.storeVoxelPreview("r1", key, VoxelProfileCompileResult.failed("boom"));
        VoxelProfilePreviewSlot slot = session.voxelPreview("r1");
        assertEquals(VoxelProfilePreviewState.FAILED, slot.state());
        assertNotNull(slot.lastGoodResult());
        assertTrue(slot.lastGoodResult().feasible());
        assertTrue(slot.hasLastGoodSamples(), "failed compile must keep previous successful samples");
        assertEquals("boom", slot.failureReason());
        // Failed payload must not replace last-good.
        assertTrue(slot.lastGoodResult().hasSamples());
    }

    @Test
    void previewKeyChangesOnIntersectionConstraint() {
        RoadNetwork network = networkWithRoad("r1");
        Road road = network.getRoad("r1");
        VoxelProfilePreviewKey before = keyFor(network, road);

        com.plot.plugin.road.profile.RoadProfileIntersection intersection =
            new com.plot.plugin.road.profile.RoadProfileIntersection(
                "node-x",
                "r1",
                "r2",
                "other",
                15.0,
                15.0,
                65.0,
                65.0,
                null,
                false,
                false,
                0.0,
                false);
        VoxelProfilePreviewKey after = VoxelProfilePreviewKey.of(
            network,
            road,
            road.getVerticalAlignment(),
            List.of(intersection),
            VoxelProfileConstraints.empty(),
            null,
            0L,
            new RoadSystemConfig("fp"),
            ProfileCompileOptions.defaults());
        assertNotEquals(before, after);
    }

    @Test
    void editingWhileCompileIsPendingInvalidatesTheOldRequest() {
        var network = networkWithRoad("pending");
        var slot = new VoxelProfilePreviewSlot();
        slot.markComputing(keyFor(network, network.getRoad("pending")));
        slot.markStale();
        assertEquals(VoxelProfilePreviewState.STALE, slot.state());
    }

    @Test
    void equalLengthMirroredPathInvalidatesPreview() {
        var network = networkWithRoad("shape");
        var road = network.getRoad("shape");
        var edge = network.getEdges().values().iterator().next();
        edge.setCenterlinePoints(List.of(new Vec2d(0, 0), new Vec2d(15, 10), new Vec2d(30, 0)));
        var before = keyFor(network, road);
        edge.setCenterlinePoints(List.of(new Vec2d(0, 0), new Vec2d(15, -10), new Vec2d(30, 0)));
        assertNotEquals(before, keyFor(network, road));
    }

    private static VoxelProfilePreviewKey keyFor(RoadNetwork network, Road road) {
        return VoxelProfilePreviewKey.of(
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

    private static RoadNetwork networkWithRoad(String roadId) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad(roadId);
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            new PointOfVerticalIntersection(0.0, 64.0),
            new PointOfVerticalIntersection(30.0, 64.0))));
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(30, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), roadId);
        return network;
    }

    private static VoxelProfileCompileResult readyResult() {
        List<BuildHeightSample> samples = List.of(
            new BuildHeightSample(0.0, 64.0, 64),
            new BuildHeightSample(1.0, 64.0, 64),
            new BuildHeightSample(2.0, 64.0, 64));
        return new VoxelProfileCompileResult(
            true,
            samples,
            BuildHeightProfile.fromSamples(samples),
            null,
            null,
            new BuildHeightSequenceSmoother.SmoothingDiagnostics(0, 0, 0));
    }
}
