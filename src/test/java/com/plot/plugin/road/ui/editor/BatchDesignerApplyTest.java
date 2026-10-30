package com.plot.plugin.road.ui.editor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.facility.RoadFacilityKind;
import com.plot.plugin.road.model.facility.RoadFacilitySide;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BatchDesignerApplyTest {

    @Test
    void variableCrossSectionApplyCopiesToAllTargets() {
        RoadNetwork live = networkWithRoads(50.0, 50.0);
        Road a = named(live, "a");
        Road b = named(live, "b");
        RoadNetwork working = live.snapshot();
        RoadCrossSection wide = new RoadCrossSection();
        wide.getCarriageway().setWidth(14);
        working.getRoad(a.getId()).setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(5.0, wide)
        )));

        VariableCrossSectionEditorApplier.applyToLive(
            live, working, a.getId(), Set.of(a.getId(), b.getId()), BatchStationMappingMode.PROPORTIONAL);

        assertNotNull(live.getRoad(a.getId()).getVariableCrossSections());
        assertNotNull(live.getRoad(b.getId()).getVariableCrossSections());
        assertEquals(1, live.getRoad(b.getId()).getVariableCrossSections().stationCount());
        assertEquals(
            14,
            live.getRoad(b.getId()).getVariableCrossSections().sortedStations()
                .getFirst().getCrossSection().getCarriageway().getWidth());
    }

    @Test
    void variableCrossSectionProportionalRespectsDifferentLengths() {
        RoadNetwork live = networkWithRoads(100.0, 40.0);
        Road a = named(live, "a");
        Road b = named(live, "b");
        RoadNetwork working = live.snapshot();
        RoadCrossSection wide = new RoadCrossSection();
        wide.getCarriageway().setWidth(12);
        working.getRoad(a.getId()).setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(20.0, wide),
            StationCrossSection.at(60.0, wide),
            StationCrossSection.at(90.0, wide)
        )));

        BatchApplyReport report = VariableCrossSectionEditorApplier.applyToLive(
            live, working, a.getId(), Set.of(a.getId(), b.getId()), BatchStationMappingMode.PROPORTIONAL);

        assertEquals(0, report.droppedItems());
        assertEquals(0, report.skippedTargets());
        var stations = live.getRoad(b.getId()).getVariableCrossSections().sortedStations();
        assertEquals(3, stations.size());
        assertEquals(8.0, stations.get(0).getStation(), 1e-6);
        assertEquals(24.0, stations.get(1).getStation(), 1e-6);
        assertEquals(36.0, stations.get(2).getStation(), 1e-6);
    }

    @Test
    void variableCrossSectionAbsoluteDropsBeyondTargetLength() {
        RoadNetwork live = networkWithRoads(100.0, 40.0);
        Road a = named(live, "a");
        Road b = named(live, "b");
        RoadNetwork working = live.snapshot();
        working.getRoad(a.getId()).setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(20.0, new RoadCrossSection()),
            StationCrossSection.at(60.0, new RoadCrossSection()),
            StationCrossSection.at(90.0, new RoadCrossSection())
        )));

        BatchApplyReport report = VariableCrossSectionEditorApplier.applyToLive(
            live, working, a.getId(), Set.of(b.getId()), BatchStationMappingMode.ABSOLUTE);

        assertEquals(2, report.droppedItems());
        assertEquals(1, live.getRoad(b.getId()).getVariableCrossSections().stationCount());
        assertEquals(20.0,
            live.getRoad(b.getId()).getVariableCrossSections().sortedStations().getFirst().getStation(),
            1e-6);
    }

    @Test
    void proportionalSkipsZeroLengthTargetWithoutWriting() {
        RoadNetwork live = networkWithRoads(100.0, 40.0);
        Road a = named(live, "a");
        Road zero = live.createRoad("zero");
        zero.setName("zero");
        // no edges → length 0
        RoadNetwork working = live.snapshot();
        working.getRoad(a.getId()).setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(20.0, new RoadCrossSection())
        )));
        assertNull(live.getRoad(zero.getId()).getVariableCrossSections());

        BatchApplyReport report = VariableCrossSectionEditorApplier.applyToLive(
            live, working, a.getId(), Set.of(zero.getId()), BatchStationMappingMode.PROPORTIONAL);

        assertEquals(1, report.skippedTargets());
        assertNull(live.getRoad(zero.getId()).getVariableCrossSections());
    }

    @Test
    void stationFacilitiesApplyCopiesToAllTargets() {
        RoadNetwork live = networkWithRoads(50.0, 50.0);
        Road a = named(live, "a");
        Road b = named(live, "b");
        assertNull(b.getStationFacilities());

        RoadNetwork working = live.snapshot();
        working.getRoad(a.getId()).setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(1.0, 3.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT)
        )));

        StationFacilityEditorApplier.applyToLive(
            live, working, a.getId(), Set.of(a.getId(), b.getId()), BatchStationMappingMode.PROPORTIONAL);

        assertEquals(1, live.getRoad(a.getId()).getStationFacilities().runCount());
        assertEquals(1, live.getRoad(b.getId()).getStationFacilities().runCount());
        assertEquals(
            RoadFacilityKind.GUARDRAIL,
            live.getRoad(b.getId()).getStationFacilities().sortedRuns().getFirst().getKind());
    }

    @Test
    void stationFacilitiesProportionalFitsShorterTarget() {
        RoadNetwork live = networkWithRoads(100.0, 30.0);
        Road a = named(live, "a");
        Road b = named(live, "b");
        RoadNetwork working = live.snapshot();
        working.getRoad(a.getId()).setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(50.0, 80.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT)
        )));

        BatchApplyReport report = StationFacilityEditorApplier.applyToLive(
            live, working, a.getId(), Set.of(b.getId()), BatchStationMappingMode.PROPORTIONAL);

        assertEquals(0, report.droppedItems());
        var run = live.getRoad(b.getId()).getStationFacilities().sortedRuns().getFirst();
        assertEquals(15.0, run.getStartStation(), 1e-6);
        assertEquals(24.0, run.getEndStation(), 1e-6);
        assertEquals(RoadFacilitySide.LEFT, run.getSide());
    }

    @Test
    void absoluteFacilityKeepsSideRelativeToRoadDirection() {
        RoadNetwork live = networkWithRoads(100.0, 100.0);
        Road a = named(live, "a");
        Road b = named(live, "b");
        RoadNetwork working = live.snapshot();
        working.getRoad(a.getId()).setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(10.0, 20.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.RIGHT)
        )));

        StationFacilityEditorApplier.applyToLive(
            live, working, a.getId(), Set.of(b.getId()), BatchStationMappingMode.ABSOLUTE);

        assertEquals(
            RoadFacilitySide.RIGHT,
            live.getRoad(b.getId()).getStationFacilities().sortedRuns().getFirst().getSide());
    }

    private static Road named(RoadNetwork network, String name) {
        return network.getRoads().values().stream()
            .filter(r -> name.equals(r.getName()))
            .findFirst()
            .orElseThrow();
    }

    private static RoadNetwork networkWithRoads(double lengthA, double lengthB) {
        RoadNetwork network = new RoadNetwork();
        Road a = network.createRoad("a");
        a.setName("a");
        Road b = network.createRoad("b");
        b.setName("b");
        RoadNode a0 = network.createNode(new Vec2d(0, 0));
        RoadNode a1 = network.createNode(new Vec2d(lengthA, 0));
        network.createEdge(a0.getId(), a1.getId(), List.of(new Vec2d(0, 0), new Vec2d(lengthA, 0)), a.getId());
        RoadNode b0 = network.createNode(new Vec2d(0, 10));
        RoadNode b1 = network.createNode(new Vec2d(lengthB, 10));
        network.createEdge(b0.getId(), b1.getId(), List.of(new Vec2d(0, 10), new Vec2d(lengthB, 10)), b.getId());
        return network;
    }
}
