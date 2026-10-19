package com.plot.plugin.road.profile.edit;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadCrossingRef;
import com.plot.plugin.road.crossing.CrossingType;
import com.plot.plugin.road.crossing.RoadStackCrossing;
import com.plot.plugin.road.crossing.RoadStackCrossingRef;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.profile.RoadProfileIntersectionDragEditor;
import com.plot.plugin.road.profile.RoadProfileIntersectionResolver;
import com.plot.plugin.road.vertical.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProfileNetworkDraftTest {
    private final RoadSystemConfig config = new RoadSystemConfig("network-draft");

    @Test
    void firstCurveEditAtRegistryCrossingKeepsActualHeightInLiveDraftAndCommit() {
        RoadNetwork network = new RoadNetwork();
        Road a = road(network, "a");
        Road b = road(network, "b");
        a.setVerticalAlignment(VerticalProfileControlPoints.withElevation(a.getVerticalAlignment(), 1, 80));
        b.setVerticalAlignment(VerticalProfileControlPoints.withElevation(b.getVerticalAlignment(), 1, 80));
        network.registerCrossing(RoadCrossing.atGrade("a", 50, "b", 50, new Vec2d(50, 0)));
        var edit = new ProfileEditSession();
        edit.beginCurveEdit(a, 1);
        edit.updateCurveLength(20);
        var preview = edit.previewNetwork(network, a, config);
        assertEquals(80, at(preview.getRoad("a"), 50), 1e-6);
        assertEquals(80, at(preview.getRoad("b"), 50), 1e-6);
        assertEquals(20, preview.getRoad("a").getVerticalAlignment().getPvis().get(1).getCurveLength());
        edit.commitEdit(network, a, config, null, null, null);
        assertEquals(80, at(a, 50), 1e-6);
        assertEquals(80, at(b, 50), 1e-6);
    }

    @Test
    void livePreviewLinksRoadsButCancelLeavesWorkingAndLiveNetworksUntouched() {
        RoadNetwork live = new RoadNetwork();
        Road a = road(live, "a");
        road(live, "b");
        live.registerCrossing(RoadCrossing.atGrade("a", 50, "b", 50, new Vec2d(50, 0)));
        var window = ProfileEditorWindowSession.open(live, 0);
        var working = window.editingNetwork();
        var edit = new ProfileEditSession();
        Road current = working.getRoad("a");
        edit.beginPviEdit(current);
        edit.updatePviElevation(working, current, 1, 80);
        var preview = edit.previewNetwork(working, current, config);
        assertEquals(80, at(preview.getRoad("a"), 50), 1e-6);
        assertEquals(80, at(preview.getRoad("b"), 50), 1e-6);
        assertEquals(80, RoadProfileIntersectionResolver.forRoadDesign(preview,
            preview.getRoad("a"), config).getFirst().otherRoadElevation());
        assertEquals(64, at(working.getRoad("b"), 50), 1e-6);
        assertEquals(64, at(a, 50), 1e-6);
        assertSame(preview, edit.previewNetwork(working, current, config), "unchanged drafts reuse preview");
        edit.cancelEdit();
        assertSame(working, edit.previewNetwork(working, current, config));
        assertFalse(window.isDirty());
    }

    @Test
    void editingSecondRoadKeepsFirstRoadsChangesInsideSameWindowTransaction() {
        RoadNetwork live = new RoadNetwork();
        road(live, "a");
        road(live, "b");
        live.registerCrossing(RoadCrossing.atGrade("a", 50, "b", 50, new Vec2d(50, 0)));
        var window = ProfileEditorWindowSession.open(live, 0);
        var working = window.editingNetwork();
        var edit = new ProfileEditSession();
        Road a = working.getRoad("a");
        edit.beginPviEdit(a);
        edit.updatePviElevation(working, a, 1, 70);
        edit.commitEdit(working, a, config, window, null, null);
        assertEquals(70, at(working.getRoad("b"), 50), 1e-6);

        Road b = working.getRoad("b");
        edit.beginPviEdit(b);
        edit.updatePviElevation(working, b, 1, 74);
        edit.commitEdit(working, b, config, window, null, null);
        assertEquals(74, at(a, 50), 1e-6);
        assertEquals(74, at(b, 50), 1e-6);
        assertEquals(64, at(a, 0), 1e-6, "grades are not auto-fitted per road during editing");
        assertEquals(64, at(live.getRoad("a"), 50), 1e-6);
        assertTrue(window.touchedRoadIds().containsAll(List.of("a", "b")));
    }

    @Test
    void selfIntersectionMarkerDragPreviewsAndCommitsBothPasses() {
        RoadNetwork network = new RoadNetwork();
        Road road = road(network, "self");
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 64), PointOfVerticalIntersection.of(25, 64),
            PointOfVerticalIntersection.of(75, 64), PointOfVerticalIntersection.of(100, 64))));
        var stack = RoadStackCrossing.gradeSeparated("self", 75, 25, new Vec2d(25, 0), 6);
        network.registerStackCrossing(stack);
        var marker = new RoadProfileIntersection(RoadStackCrossingRef.toRef(stack.id()),
            "self", "self", "self", 75, 75, 64, 64, null, true, false, 6, false);
        var edit = new ProfileEditSession();
        edit.beginIntersectionEdit(List.of(marker));
        edit.updateIntersectionDrag(0, RoadProfileIntersectionDragEditor.DragTarget.CURRENT, 72);
        var preview = edit.previewNetwork(network, road, config);
        assertEquals(72, at(preview.getRoad("self"), 75), 1e-6);
        assertEquals(78, at(preview.getRoad("self"), 25), 1e-6);
        assertEquals(64, at(road, 25), 1e-6);
        edit.commitEdit(network, road, config, null, null, null);
        assertEquals(72, at(road, 75), 1e-6);
        assertEquals(78, at(road, 25), 1e-6);
    }

    @Test
    void geometricRefreshKeepsUpperBranchWhenItsStationComesFirst() {
        var stack = RoadStackCrossing.gradeSeparated("self", 75, 25, new Vec2d(0, 0), 6);
        var refreshed = stack.withRefreshedGeometry(30, 85, new Vec2d(1, 1));
        assertEquals(85, refreshed.resolvedLowerStation());
        assertEquals(30, refreshed.resolvedUpperStation());
    }

    @Test
    void crossedUpperAndLowerRolesMustNotShowPositiveClearance() {
        var marker = new RoadProfileIntersection("node", "upper", "lower", "lower",
            50, 50, 60, 70, null, true, true, 6, false);
        assertEquals(-10, marker.clearanceGap());
    }

    @Test
    void draggingOtherRoadCanPushTheCurrentUpperRoadUpward() {
        RoadNetwork network = new RoadNetwork();
        Road upper = road(network, "upper");
        road(network, "lower");
        upper.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 88), PointOfVerticalIntersection.of(50, 88),
            PointOfVerticalIntersection.of(100, 88))));
        var crossing = new RoadCrossing("lu", "upper", 50, "lower", 50,
            new Vec2d(50, 0), CrossingType.GRADE_SEPARATED, "upper", 6.0, null);
        network.registerCrossing(crossing);
        var marker = new RoadProfileIntersection(RoadCrossingRef.toRef(crossing.id()),
            "upper", "lower", "lower", 50, 50, 88, 64, null, true, true, 6, false);
        var edit = new ProfileEditSession();
        edit.beginIntersectionEdit(List.of(marker));
        edit.updateIntersectionDrag(0, RoadProfileIntersectionDragEditor.DragTarget.OTHER, 90);
        var preview = edit.previewNetwork(network, upper, config);
        assertEquals(90, at(preview.getRoad("lower"), 50), 1e-6);
        assertEquals(96, at(preview.getRoad("upper"), 50), 1e-6);
        assertEquals(88, at(upper, 50), 1e-6);
    }

    @Test
    void automaticLayerSelectionIsConsistentFromBothRoadCharts() {
        RoadNetwork network = new RoadNetwork();
        Road upper = road(network, "upper");
        Road lower = road(network, "lower");
        upper.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 80), PointOfVerticalIntersection.of(100, 80))));
        network.registerCrossing(new RoadCrossing("auto", "upper", 50, "lower", 50,
            new Vec2d(50, 0), CrossingType.GRADE_SEPARATED, null, 6.0, null));
        var upperMarker = RoadProfileIntersectionResolver.forRoadDesign(network, upper, config).getFirst();
        var lowerMarker = RoadProfileIntersectionResolver.forRoadDesign(network, lower, config).getFirst();
        assertTrue(upperMarker.currentRoadElevated());
        assertFalse(lowerMarker.currentRoadElevated());
        assertEquals(16, upperMarker.clearanceGap());
        assertEquals(16, lowerMarker.clearanceGap());
    }

    @Test
    void sampledTerrainBootstrapsEditorWithoutChangingAutomaticIntentOrLiveState() {
        RoadNetwork live = new RoadNetwork();
        Road road = road(live, "auto");
        road.setVerticalAlignment(null);
        road.setVerticalMode(null);
        var mode = road.getVerticalMode();
        var result = new com.plot.plugin.road.solid.RoadGenerationResult(100);
        result.profileDistances = List.of(0.0, 50.0, 100.0);
        result.profileGroundHeights = List.of(64, 66, 68);
        result.profileGuideLine = List.of(64, 66, 68);
        result.profileDesignElevations = List.of(64.0, 66.0, 68.0);
        result.profileBuildHeights = List.of(64, 66, 68);
        var edgeId = live.getEdges().values().iterator().next().getId();
        var window = ProfileEditorWindowSession.open(live, 0);
        var editingMode = window.editingNetwork().getRoad("auto").getVerticalMode();
        window.seedSampledProfiles(config, java.util.Map.of(edgeId, result));
        assertNotNull(window.editingNetwork().getRoad("auto").getVerticalAlignment());
        assertEquals(editingMode, window.editingNetwork().getRoad("auto").getVerticalMode());
        assertFalse(window.isDirty());
        assertNull(road.getVerticalAlignment());
        assertEquals(mode, road.getVerticalMode());
    }

    private Road road(RoadNetwork network, String id) {
        Road road = network.createRoad(id);
        var start = network.createNode(new Vec2d(0, 0));
        var end = network.createNode(new Vec2d(100, 0));
        network.createEdge(start.getId(), end.getId(), List.of(start.getPosition(), end.getPosition()), id);
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 64), PointOfVerticalIntersection.of(50, 64),
            PointOfVerticalIntersection.of(100, 64))));
        return road;
    }

    private double at(Road road, double station) {
        return VerticalAlignmentGeometry.elevationAt(road.getVerticalAlignment(), station).orElseThrow();
    }
}
