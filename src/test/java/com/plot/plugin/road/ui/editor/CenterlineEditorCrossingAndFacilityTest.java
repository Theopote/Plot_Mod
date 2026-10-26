package com.plot.plugin.road.ui.editor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.IntersectionProbeResult;
import com.plot.plugin.road.centerline.CenterlineEditResult;
import com.plot.plugin.road.centerline.RoadCenterlineEditor;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadCrossingReconciler;
import com.plot.plugin.road.crossing.RoadStackCrossingReconciler;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.facility.RoadFacilityKind;
import com.plot.plugin.road.model.facility.RoadFacilitySide;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.model.section.VariableCrossSectionResolver;
import com.plot.plugin.road.station.RoadStationing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CenterlineEditorCrossingAndFacilityTest {

    private static final double EPS = 1e-3;

    @Test
    void applyToLivePreservesCrossingAfterSplitAndReconcile() {
        RoadNetwork live = new RoadNetwork();
        CrossingFixture fixture = createPerpendicularCrossing(live);
        RoadCrossing original = live.getCrossings().values().iterator().next();
        String originalId = original.id();
        double stationOnHorizontalBefore = original.stationOn(fixture.horizontalRoadId());
        assertEquals(1.0, stationOnHorizontalBefore, 0.5);

        RoadNetwork working = live.snapshot().snapshot();
        String horizontalEdgeId = working.getRoad(fixture.horizontalRoadId()).getOrderedSegmentIds().getFirst();
        CenterlineEditResult split =
            RoadCenterlineEditor.splitAtLocalDistance(working, horizontalEdgeId, 5.0);
        assertTrue(split.isSuccess(), () -> "split failed: " + split.status());

        applyCenterlineWithCrossingRefresh(live, working, fixture.horizontalRoadId());

        RoadCrossing updated = live.getCrossing(originalId);
        assertNotNull(updated);
        assertEquals(originalId, updated.id());
        assertEquals(stationOnHorizontalBefore, updated.stationOn(fixture.horizontalRoadId()), 0.5);
        assertEquals(1.0, updated.position().x, EPS);
        assertEquals(5.0, updated.position().y, EPS);
        assertEquals(2, live.getRoad(fixture.horizontalRoadId()).getOrderedSegmentIds().size());
        assertTrue(live.validateInvariants().isValid(), live.validateInvariants().violations().toString());
        IntersectionProbeResult probe = RoadCrossingReconciler.probeRegistryCompleteness(live);
        assertFalse(probe.hasPendingWork(), "crossing registry should match geometry after apply");
    }

    @Test
    void applyToLivePreservesCrossingAfterSplitMergeAndReconcile() {
        RoadNetwork live = new RoadNetwork();
        CrossingFixture fixture = createPerpendicularCrossing(live);
        RoadCrossing original = live.getCrossings().values().iterator().next();
        String originalId = original.id();
        double stationOnHorizontalBefore = original.stationOn(fixture.horizontalRoadId());

        RoadNetwork working = live.snapshot().snapshot();
        String horizontalEdgeId = working.getRoad(fixture.horizontalRoadId()).getOrderedSegmentIds().getFirst();
        CenterlineEditResult split =
            RoadCenterlineEditor.splitAtLocalDistance(working, horizontalEdgeId, 5.0);
        assertTrue(split.isSuccess());
        assertTrue(RoadCenterlineEditor.mergeThroughNode(working, split.nodeId()).isSuccess());

        applyCenterlineWithCrossingRefresh(live, working, fixture.horizontalRoadId());

        RoadCrossing updated = live.getCrossing(originalId);
        assertNotNull(updated);
        assertEquals(originalId, updated.id());
        assertEquals(stationOnHorizontalBefore, updated.stationOn(fixture.horizontalRoadId()), 0.5);
        assertEquals(1.0, updated.position().x, EPS);
        assertEquals(5.0, updated.position().y, EPS);
        assertEquals(1, live.getRoad(fixture.horizontalRoadId()).getOrderedSegmentIds().size());
        assertTrue(live.validateInvariants().isValid(), live.validateInvariants().violations().toString());
        assertFalse(RoadCrossingReconciler.probeRegistryCompleteness(live).hasPendingWork());
    }

    @Test
    void applyToLivePropagatesMirroredVariableCrossSectionsAfterReverse() {
        RoadNetwork live = new RoadNetwork();
        RoadNode start = live.createNode(new Vec2d(0, 0));
        RoadNode end = live.createNode(new Vec2d(100, 0));
        Road road = live.createRoad("reverse-vcs");
        String roadId = road.getId();
        live.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)),
            roadId);
        RoadCrossSection roadDefault = sectionWithWidth(6);
        road.setCrossSection(roadDefault);
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(80.0, sectionWithWidth(12))
        )));

        RoadNetwork working = live.snapshot().snapshot();
        assertTrue(RoadCenterlineEditor.reverseRoad(working, working.getRoad(roadId)).isSuccess());

        applyCenterlineWithCrossingRefresh(live, working, roadId);

        Road liveRoad = live.getRoad(roadId);
        assertNotNull(liveRoad.getVariableCrossSections());
        assertEquals(12, widthOf(VariableCrossSectionResolver.resolveTemplate(liveRoad, 10.0)));
        assertEquals(6, widthOf(VariableCrossSectionResolver.resolveTemplate(liveRoad, 50.0)));
        assertTrue(live.validateInvariants().isValid(), live.validateInvariants().violations().toString());
    }

    @Test
    void applyToLivePropagatesRescaledStationFacilitiesAfterFillet() {
        RoadNetwork live = new RoadNetwork();
        RoadNode n1 = live.createNode(new Vec2d(0, 0));
        RoadNode n3 = live.createNode(new Vec2d(40, 40));
        Road road = live.createRoad("fillet");
        String roadId = road.getId();
        RoadEdge edge = live.createEdge(
            n1.getId(),
            n3.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(40, 0), new Vec2d(40, 40)),
            roadId);
        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(55.0, 65.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT)
        )));

        double oldLength = RoadStationing.canonicalLength(live, road);
        StationFacilityRun liveBefore = road.getStationFacilities().sortedRuns().getFirst();
        assertEquals(55.0, liveBefore.getStartStation(), EPS);

        RoadNetwork working = live.snapshot().snapshot();
        assertTrue(RoadCenterlineEditor.filletVertex(working, edge.getId(), 1, 5.0).isSuccess());

        Road workingRoad = working.getRoad(roadId);
        double newLength = RoadStationing.canonicalLength(working, workingRoad);
        assertTrue(newLength > 0.0);
        assertTrue(Math.abs(newLength - oldLength) > EPS, "fillet should change road length");
        StationFacilityRun workingRun = workingRoad.getStationFacilities().sortedRuns().getFirst();
        double expectedStart = 55.0 * (newLength / oldLength);
        assertEquals(expectedStart, workingRun.getStartStation(), EPS);

        applyCenterlineWithCrossingRefresh(live, working, roadId);

        Road liveRoad = live.getRoad(roadId);
        assertNotNull(liveRoad.getStationFacilities());
        StationFacilityRun liveRun = liveRoad.getStationFacilities().sortedRuns().getFirst();
        assertEquals(expectedStart, liveRun.getStartStation(), EPS);
        assertEquals(65.0 * (newLength / oldLength), liveRun.getEndStation(), EPS);
        assertTrue(live.validateInvariants().isValid(), live.validateInvariants().violations().toString());
    }

    private static void applyCenterlineWithCrossingRefresh(
            RoadNetwork live,
            RoadNetwork working,
            String roadId) {
        CenterlineEditorApplier.applyToLive(live, working, roadId);
        RoadCrossingReconciler.reconcileCrossings(live);
        RoadStackCrossingReconciler.reconcileStackCrossings(live);
    }

    private static CrossingFixture createPerpendicularCrossing(RoadNetwork network) {
        Road roadA = network.createRoad("road-a");
        Road roadB = network.createRoad("road-b");
        network.createEdge(
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
        return new CrossingFixture(roadA.getId(), roadB.getId());
    }

    private record CrossingFixture(String horizontalRoadId, String verticalRoadId) {
    }

    private static RoadCrossSection sectionWithWidth(int width) {
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setWidth(width);
        return section;
    }

    private static int widthOf(RoadCrossSection section) {
        return section.getCarriageway().getWidth();
    }
}
