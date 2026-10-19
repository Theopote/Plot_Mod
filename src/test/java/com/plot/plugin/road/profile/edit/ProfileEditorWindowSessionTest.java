package com.plot.plugin.road.profile.edit;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadPreviewManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.BuildHeightSample;
import com.plot.plugin.road.pipeline.profile.BuildHeightSequenceSmoother;
import com.plot.plugin.road.pipeline.profile.ProfileCompileOptions;
import com.plot.plugin.road.pipeline.profile.VoxelProfileCompileResult;
import com.plot.plugin.road.pipeline.profile.VoxelProfileConstraints;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.plugin.road.vertical.FlatVerticalIntent;
import com.plot.plugin.road.vertical.FlatVerticalIntentSupport;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.VerticalProfileControlPoints;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileEditorWindowSessionTest {

    private RoadSystemConfig config;
    private RoadNetworkManager manager;
    private RoadNetwork live;
    private Road road;

    @BeforeEach
    void setUp() {
        config = new RoadSystemConfig("test");
        manager = new RoadNetworkManager(config, new RoadProjectStatus());
        live = new RoadNetwork();
        road = live.createRoad("road-a");
        RoadNode n1 = live.createNode(new Vec2d(0, 0));
        RoadNode n2 = live.createNode(new Vec2d(100, 0));
        live.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        RoadVerticalAlignment alignment = new RoadVerticalAlignment();
        alignment.addPvi(new PointOfVerticalIntersection(0.0, 64.0));
        alignment.addPvi(PointOfVerticalIntersection.withCurve(50.0, 68.0, 8.0));
        alignment.addPvi(new PointOfVerticalIntersection(100.0, 66.0));
        road.setVerticalAlignment(alignment);
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);

        manager.setNetwork(live);
    }

    @Test
    void cancelAfterMultiplePviEditsLeavesLiveUnchanged() {
        ProfileEditorWindowSession session = openSession();
        Road workingRoad = session.editingNetwork().getRoad(road.getId());
        RoadVerticalAlignment baseline = live.getRoad(road.getId()).getVerticalAlignment().copy();

        mutatePvi(workingRoad, 1, 70.0);
        session.markTouched(workingRoad);
        mutatePvi(workingRoad, 2, 62.0);
        mutatePvi(workingRoad, 0, 63.0);

        session.cancel();

        assertTrue(ProfileEditorNetworkApplier.alignmentPvisEqual(
            baseline, live.getRoad(road.getId()).getVerticalAlignment()));
        assertFalse(session.isDirty());
    }

    @Test
    void restoreResetsWorkingCopyToOpenSnapshot() {
        ProfileEditorWindowSession session = openSession();
        Road workingRoad = session.editingNetwork().getRoad(road.getId());
        mutatePvi(workingRoad, 1, 74.0);
        session.markTouched(workingRoad);
        session.editingNetwork().createRoad("extra");

        session.restore();

        assertFalse(session.isDirty());
        assertTrue(session.touchedRoadIds().isEmpty());
        assertTrue(ProfileEditorNetworkApplier.alignmentPvisEqual(
            session.openSnapshot().getRoad(road.getId()).getVerticalAlignment(),
            session.editingNetwork().getRoad(road.getId()).getVerticalAlignment()));
        assertEquals(1, session.editingNetwork().getRoads().size());
    }

    @Test
    void flatElevationUndoRestoresPriorBaseElevation() {
        Road flatRoad = live.createRoad("flat-road");
        RoadNode f1 = live.createNode(new Vec2d(0, 20));
        RoadNode f2 = live.createNode(new Vec2d(40, 20));
        live.createEdge(f1.getId(), f2.getId(), List.of(new Vec2d(0, 20), new Vec2d(40, 20)), flatRoad.getId());
        flatRoad.setVerticalMode(RoadVerticalMode.FLAT);
        flatRoad.setFlatVerticalIntent(new FlatVerticalIntent(72.0));
        FlatVerticalIntentSupport.syncCompiledAlignment(live, flatRoad, flatRoad.getEffectiveMaxSlope(config));

        ProfileEditorWindowSession session = openSession();
        Road workingFlat = session.editingNetwork().getRoad(flatRoad.getId());

        session.recordUndoPoint();
        FlatVerticalIntentSupport.setBaseElevation(
            session.editingNetwork(), workingFlat, 88.0, workingFlat.getEffectiveMaxSlope(config));
        session.markTouched(workingFlat);
        assertEquals(
            88.0,
            FlatVerticalIntentSupport.resolveIntent(session.editingNetwork(), workingFlat)
                .getBaseElevation());

        session.undo();
        assertEquals(
            72.0,
            FlatVerticalIntentSupport.resolveIntent(
                session.editingNetwork(),
                session.editingNetwork().getRoad(flatRoad.getId())).getBaseElevation());
    }

    @Test
    void flatElevationCancelLeavesLiveIntentUnchanged() {
        Road flatRoad = live.createRoad("flat-road");
        RoadNode f1 = live.createNode(new Vec2d(0, 20));
        RoadNode f2 = live.createNode(new Vec2d(40, 20));
        live.createEdge(f1.getId(), f2.getId(), List.of(new Vec2d(0, 20), new Vec2d(40, 20)), flatRoad.getId());
        flatRoad.setVerticalMode(RoadVerticalMode.FLAT);
        flatRoad.setFlatVerticalIntent(new FlatVerticalIntent(72.0));
        FlatVerticalIntentSupport.syncCompiledAlignment(live, flatRoad, flatRoad.getEffectiveMaxSlope(config));

        ProfileEditorWindowSession session = openSession();
        Road workingFlat = session.editingNetwork().getRoad(flatRoad.getId());
        FlatVerticalIntentSupport.setBaseElevation(
            session.editingNetwork(), workingFlat, 88.0, workingFlat.getEffectiveMaxSlope(config));
        session.markTouched(workingFlat);

        session.cancel();

        FlatVerticalIntent liveIntent = FlatVerticalIntentSupport.resolveIntent(live, flatRoad);
        assertEquals(72.0, liveIntent.getBaseElevation());
    }

    @Test
    void confirmAfterWorkingEditsUpdatesLiveWithSingleUndoFrame() {
        ProfileEditorWindowSession session = openSession();
        Road workingRoad = session.editingNetwork().getRoad(road.getId());
        mutatePvi(workingRoad, 1, 75.0);
        session.markTouched(workingRoad);

        long revisionBefore = manager.getNetworkRevision();
        assertFalse(manager.canUndo());

        ProfileEditorWindowSession.ConfirmResult result =
            session.confirm(testContext(), live, config);

        assertEquals(ProfileEditorWindowSession.ConfirmResult.APPLIED, result);
        assertEquals(75.0, live.getRoad(road.getId()).getVerticalAlignment().getPvis().get(1).getElevation());
        assertEquals(revisionBefore + 1, manager.getNetworkRevision());
        assertTrue(manager.canUndo());

        manager.undo();
        assertEquals(
            68.0,
            manager.getNetwork().getRoad(road.getId()).getVerticalAlignment().getPvis().get(1).getElevation());
    }

    @Test
    void confirmWhenNotDirtyDoesNotPushUndo() {
        ProfileEditorWindowSession session = openSession();
        long revisionBefore = manager.getNetworkRevision();

        ProfileEditorWindowSession.ConfirmResult result =
            session.confirm(testContext(), live, config);

        assertEquals(ProfileEditorWindowSession.ConfirmResult.NO_CHANGES, result);
        assertEquals(revisionBefore, manager.getNetworkRevision());
        assertFalse(manager.canUndo());
    }

    @Test
    void confirmRejectsWhenLiveRevisionChanged() {
        ProfileEditorWindowSession session = openSession();
        Road workingRoad = session.editingNetwork().getRoad(road.getId());
        mutatePvi(workingRoad, 1, 77.0);
        session.markTouched(workingRoad);

        manager.mutateNetwork(() -> live.getRoad(road.getId()).setName("external"));

        ProfileEditorWindowSession.ConfirmResult result =
            session.confirm(testContext(), live, config);

        assertEquals(ProfileEditorWindowSession.ConfirmResult.CONFLICT, result);
        assertEquals(68.0, live.getRoad(road.getId()).getVerticalAlignment().getPvis().get(1).getElevation());
    }

    @Test
    void applierCopiesOnlyTouchedRoadVerticalFields() {
        Road other = live.createRoad("road-b");
        RoadNode b1 = live.createNode(new Vec2d(0, 10));
        RoadNode b2 = live.createNode(new Vec2d(50, 10));
        live.createEdge(b1.getId(), b2.getId(), List.of(new Vec2d(0, 10), new Vec2d(50, 10)), other.getId());
        RoadVerticalAlignment otherAlignment = new RoadVerticalAlignment();
        otherAlignment.addPvi(new PointOfVerticalIntersection(0.0, 60.0));
        otherAlignment.addPvi(new PointOfVerticalIntersection(50.0, 62.0));
        other.setVerticalAlignment(otherAlignment);
        other.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        other.setName("untouched-name");

        ProfileEditorWindowSession session = openSession();
        Road workingRoad = session.editingNetwork().getRoad(road.getId());
        mutatePvi(workingRoad, 1, 81.0);
        session.markTouched(workingRoad);

        session.confirm(testContext(), live, config);

        assertEquals(81.0, live.getRoad(road.getId()).getVerticalAlignment().getPvis().get(1).getElevation());
        assertEquals(62.0, live.getRoad(other.getId()).getVerticalAlignment().getPvis().get(1).getElevation());
        assertEquals("untouched-name", live.getRoad(other.getId()).getName());
    }

    @Test
    void editBackToOriginalValueIsNotDirty() {
        ProfileEditorWindowSession session = openSession();
        Road workingRoad = session.editingNetwork().getRoad(road.getId());

        mutatePvi(workingRoad, 1, 75.0);
        assertTrue(session.isDirty());

        mutatePvi(workingRoad, 1, 68.0);
        assertFalse(session.isDirty());

        ProfileEditorWindowSession.ConfirmResult result =
            session.confirm(testContext(), live, config);
        assertEquals(ProfileEditorWindowSession.ConfirmResult.NO_CHANGES, result);
        assertFalse(manager.canUndo());
    }

    @Test
    void confirmAppliesPropagationAdjustedConnectedRoad() {
        Road eastWest = live.createRoad("east-west");
        Road northSouth = live.createRoad("north-south");
        eastWest.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            new PointOfVerticalIntersection(0.0, 70.0),
            new PointOfVerticalIntersection(100.0, 76.0),
            new PointOfVerticalIntersection(200.0, 70.0))));
        northSouth.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            new PointOfVerticalIntersection(0.0, 70.0),
            new PointOfVerticalIntersection(60.0, 70.0),
            new PointOfVerticalIntersection(140.0, 70.0),
            new PointOfVerticalIntersection(200.0, 70.0))));
        eastWest.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        northSouth.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);

        RoadNode west = live.createNode(new Vec2d(-100, 0));
        RoadNode junction = live.createNode(new Vec2d(0, 0));
        RoadNode east = live.createNode(new Vec2d(100, 0));
        RoadNode south = live.createNode(new Vec2d(0, -100));
        RoadNode north = live.createNode(new Vec2d(0, 100));
        live.createEdge(west.getId(), junction.getId(),
            List.of(new Vec2d(-100, 0), new Vec2d(0, 0)), eastWest.getId());
        live.createEdge(junction.getId(), east.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)), eastWest.getId());
        live.createEdge(south.getId(), junction.getId(),
            List.of(new Vec2d(0, -100), new Vec2d(0, 0)), northSouth.getId());
        live.createEdge(junction.getId(), north.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(0, 100)), northSouth.getId());

        ProfileEditorWindowSession session = openSession();
        Road workingEastWest = session.editingNetwork().getRoad(eastWest.getId());
        mutatePvi(workingEastWest, 1, 80.0);
        session.markTouched(workingEastWest);

        session.confirm(testContext(), live, config);

        Road liveNorthSouth = manager.getNetwork().getRoad(northSouth.getId());
        boolean hasPropagatedJunctionElevation = liveNorthSouth.getVerticalAlignment().getPvis().stream()
            .anyMatch(pvi -> Math.abs(pvi.getElevation() - 80.0) < 1e-3);
        assertTrue(hasPropagatedJunctionElevation);
        assertTrue(ProfileEditorNetworkApplier.alignmentPvisEqual(
            session.editingNetwork().getRoad(northSouth.getId()).getVerticalAlignment(),
            liveNorthSouth.getVerticalAlignment()));
    }

    @Test
    void intersectionElevationRestoreRevertsWorkingCopy() {
        RoadNode junction = live.getNodes().values().iterator().next();
        junction.setManualElevation(64.0);

        ProfileEditorWindowSession session = openSession();
        session.editingNetwork().getNode(junction.getId()).setManualElevation(72.0);

        assertTrue(session.isDirty());
        session.restore();
        assertFalse(session.isDirty());
        assertEquals(64.0, session.editingNetwork().getNode(junction.getId()).getManualElevation());
    }

    @Test
    void undoRedoRoundTripRestoresPriorWorkingCopy() {
        ProfileEditorWindowSession session = openSession();
        Road workingRoad = session.editingNetwork().getRoad(road.getId());
        double originalElevation =
            workingRoad.getVerticalAlignment().getPvis().get(1).getElevation();

        session.recordUndoPoint();
        mutatePvi(workingRoad, 1, 75.0);
        assertEquals(75.0, workingRoad.getVerticalAlignment().getPvis().get(1).getElevation());
        assertTrue(session.canUndo());
        assertFalse(session.canRedo());

        session.undo();
        assertEquals(
            originalElevation,
            session.editingNetwork().getRoad(road.getId()).getVerticalAlignment().getPvis().get(1).getElevation());
        assertFalse(session.canUndo());
        assertTrue(session.canRedo());

        session.redo();
        assertEquals(75.0, session.editingNetwork().getRoad(road.getId()).getVerticalAlignment().getPvis().get(1).getElevation());
    }

    @Test
    void undoMarksVoxelPreviewStaleAndKeepsTouchedRoadIds() {
        ProfileEditorWindowSession session = openSession();
        Road workingRoad = session.editingNetwork().getRoad(road.getId());
        session.storeVoxelPreview(
            road.getId(),
            voxelKeyFor(session.editingNetwork(), workingRoad),
            readyVoxelResult());
        assertTrue(session.voxelPreview(road.getId()).isReady());

        session.recordUndoPoint();
        mutatePvi(workingRoad, 1, 76.0);
        session.markTouched(workingRoad);
        assertTrue(session.touchedRoadIds().contains(road.getId()));

        session.undo();
        assertTrue(session.voxelPreview(road.getId()).isStale(),
            "undo must invalidate voxel preview cache");
        assertTrue(session.touchedRoadIds().contains(road.getId()),
            "touchedRoadIds survive undo so confirm/cancel still track edited roads");
    }

    @Test
    void undoRedoThenConfirmAppliesRedoneState() {
        ProfileEditorWindowSession session = openSession();
        Road workingRoad = session.editingNetwork().getRoad(road.getId());

        session.recordUndoPoint();
        mutatePvi(workingRoad, 1, 79.0);
        session.markTouched(workingRoad);

        session.undo();
        session.redo();
        assertEquals(79.0,
            session.editingNetwork().getRoad(road.getId()).getVerticalAlignment().getPvis().get(1).getElevation());

        ProfileEditorWindowSession.ConfirmResult result =
            session.confirm(testContext(), live, config);
        assertEquals(ProfileEditorWindowSession.ConfirmResult.APPLIED, result);
        assertEquals(79.0, live.getRoad(road.getId()).getVerticalAlignment().getPvis().get(1).getElevation());
        assertTrue(session.touchedRoadIds().isEmpty());
    }

    @Test
    void hasUnconfirmedEditsForRoadTracksTouchedRoadOnly() {
        ProfileEditorWindowSession session = openSession();
        assertFalse(session.hasUnconfirmedEditsForRoad(road.getId()));

        Road workingRoad = session.editingNetwork().getRoad(road.getId());
        mutatePvi(workingRoad, 1, 73.0);
        session.markTouched(workingRoad);

        assertTrue(session.hasUnconfirmedEditsForRoad(road.getId()));
        assertFalse(session.hasUnconfirmedEditsForRoad("missing-road"));
    }

    @Test
    void insertAndDeletePviInWorkingCopyCancelRestoresLive() {
        ProfileEditorWindowSession session = openSession();
        Road workingRoad = session.editingNetwork().getRoad(road.getId());
        int originalCount = workingRoad.getVerticalAlignment().pviCount();

        VerticalProfileControlPoints.InsertResult insert = VerticalProfileControlPoints.tryBootstrapOrInsert(
            workingRoad.getVerticalAlignment(),
            100.0,
            64.0,
            66.0,
            25.0,
            65.0);
        assertTrue(insert.success());
        workingRoad.setVerticalAlignment(insert.alignment());
        session.markTouched(workingRoad);
        assertEquals(originalCount + 1, workingRoad.getVerticalAlignment().pviCount());

        workingRoad.setVerticalAlignment(VerticalProfileControlPoints.removeAt(
            workingRoad.getVerticalAlignment(), 1));
        session.markTouched(workingRoad);

        session.cancel();

        assertEquals(originalCount, live.getRoad(road.getId()).getVerticalAlignment().pviCount());
        assertEquals(originalCount, session.editingNetwork().getRoad(road.getId()).getVerticalAlignment().pviCount());
    }

    private ProfileEditorWindowSession openSession() {
        return ProfileEditorWindowSession.open(live, manager.getNetworkRevision());
    }

    @Test
    void conflictingMultiRoadBatchCannotSilentlyOverwriteOneProfileOnConfirm() {
        Road other = live.createRoad("other");
        var start = live.createNode(new Vec2d(0, 10));
        var end = live.createNode(new Vec2d(100, 10));
        live.createEdge(start.getId(), end.getId(), List.of(start.getPosition(), end.getPosition()), other.getId());
        other.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        other.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 64), PointOfVerticalIntersection.of(50, 64),
            PointOfVerticalIntersection.of(100, 64))));
        live.registerCrossing(com.plot.plugin.road.crossing.RoadCrossing.atGrade(
            road.getId(), 50, other.getId(), 50, new Vec2d(50, 0)));
        var session = openSession();
        Road a = session.editingNetwork().getRoad(road.getId());
        Road b = session.editingNetwork().getRoad(other.getId());
        mutatePvi(a, 1, 80);
        mutatePvi(b, 1, 90);
        session.markTouched(a);
        session.markTouched(b);
        long revision = manager.getNetworkRevision();
        assertEquals(ProfileEditorWindowSession.ConfirmResult.CONSTRAINT_CONFLICT,
            session.confirm(testContext(), live, config));
        assertEquals(80, a.getVerticalAlignment().getPvis().get(1).getElevation());
        assertEquals(90, b.getVerticalAlignment().getPvis().get(1).getElevation());
        assertEquals(68, road.getVerticalAlignment().getPvis().get(1).getElevation());
        assertEquals(64, other.getVerticalAlignment().getPvis().get(1).getElevation());
        assertEquals(revision, manager.getNetworkRevision());
        assertFalse(manager.canUndo());
    }

    private static void mutatePvi(Road road, int index, double elevation) {
        RoadVerticalAlignment updated = VerticalProfileControlPoints.withElevation(
            road.getVerticalAlignment(), index, elevation, road);
        road.setVerticalAlignment(updated);
    }

    private static VoxelProfilePreviewKey voxelKeyFor(RoadNetwork network, Road road) {
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

    private static VoxelProfileCompileResult readyVoxelResult() {
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

    private RoadUiContext testContext() {
        PluginContext host = new PluginContext(
            ApplicationContext.getInstance().getAppState(),
            ApplicationContext.getInstance().getCommandService(),
            ApplicationContext.getInstance().getEventBus(),
            ApplicationContext.getInstance().getToolManager(),
            IdentityCoordinateService.INSTANCE,
            null,
            null,
            null);
        return new RoadUiContext(
            manager,
            new RoadPreviewManager(new RoadProjectStatus(), host),
            null,
            null,
            new RoadProjectStatus(),
            host);
    }
}
