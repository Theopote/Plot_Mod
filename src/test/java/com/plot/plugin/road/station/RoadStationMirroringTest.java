package com.plot.plugin.road.station;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.facility.RoadFacilityKind;
import com.plot.plugin.road.model.facility.RoadFacilitySide;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.model.section.VariableCrossSectionResolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadStationMirroringTest {

    @Test
    void mirrorDoesNotMergeSameWidthDifferentEngineeringSections() {
        RoadCrossSection base = sectionWithWidth(9);
        base.getCarriageway().setLaneCount(2);
        base.getSidewalk().setEnabled(true);

        RoadCrossSection other = sectionWithWidth(9);
        other.getCarriageway().setLaneCount(4);
        other.getSidewalk().setEnabled(false);

        RoadVariableCrossSections source = new RoadVariableCrossSections(List.of(
            StationCrossSection.at(40.0, other),
            StationCrossSection.at(60.0, base)
        ));

        RoadVariableCrossSections mirrored = RoadStationMirroring.mirrorVariableCrossSections(
            source, base, 100.0);

        assertNotNull(mirrored);
        assertTrue(mirrored.stationCount() >= 2);
        List<StationCrossSection> stations = mirrored.sortedStations();
        boolean hasFourLanes = stations.stream()
            .anyMatch(station -> Integer.valueOf(4).equals(station.getCrossSection().getCarriageway().getLaneCount()));
        boolean hasTwoLanes = stations.stream()
            .anyMatch(station -> Integer.valueOf(2).equals(station.getCrossSection().getCarriageway().getLaneCount()));
        assertTrue(hasFourLanes && hasTwoLanes);
    }

    @Test
    void mirrorVariableCrossSectionSwapsStepRegions() {
        RoadCrossSection base = sectionWithWidth(6);
        RoadCrossSection wide = sectionWithWidth(12);
        RoadVariableCrossSections source = new RoadVariableCrossSections(List.of(
            StationCrossSection.at(80.0, wide)
        ));

        RoadVariableCrossSections mirrored = RoadStationMirroring.mirrorVariableCrossSections(
            source, base, 100.0);

        assertNotNull(mirrored);
        assertEquals(2, mirrored.stationCount());
        List<StationCrossSection> stations = mirrored.sortedStations();
        assertEquals(0.0, stations.get(0).getStation(), 1e-6);
        assertEquals(12, widthOf(stations.get(0).getCrossSection()));
        assertEquals(20.0, stations.get(1).getStation(), 1e-6);
        assertEquals(6, widthOf(stations.get(1).getCrossSection()));
        assertEquals(12, widthOf(VariableCrossSectionResolver.resolveTemplate(
            roadWithSections(mirrored, base), 10.0)));
        assertEquals(6, widthOf(VariableCrossSectionResolver.resolveTemplate(
            roadWithSections(mirrored, base), 50.0)));
    }

    @Test
    void mirrorFacilityRunSwapsIntervalAndSide() {
        StationFacilityRun run = StationFacilityRun.of(10.0, 30.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT);

        StationFacilityRun mirrored = RoadStationMirroring.mirrorFacilityRun(run, 100.0);

        assertNotNull(mirrored);
        assertEquals(70.0, mirrored.getStartStation(), 1e-6);
        assertEquals(90.0, mirrored.getEndStation(), 1e-6);
        assertEquals(RoadFacilitySide.RIGHT, mirrored.getSide());
    }

    @Test
    void mirrorOpenEndedFacilityRun() {
        StationFacilityRun run = StationFacilityRun.of(50.0, null, RoadFacilityKind.DRAINAGE, RoadFacilitySide.BOTH);

        StationFacilityRun mirrored = RoadStationMirroring.mirrorFacilityRun(run, 100.0);

        assertNotNull(mirrored);
        assertEquals(0.0, mirrored.getStartStation(), 1e-6);
        assertEquals(50.0, mirrored.getEndStation(), 1e-6);
    }

    @Test
    void mirrorRoadStationDataClearsWhenEverythingMapsToDefault() {
        Road road = new Road("r1");
        road.setWidth(6);
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(0.0, sectionWithWidth(6))
        )));
        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(0.0, 100.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.BOTH)
        )));

        RoadStationMirroring.mirrorRoadStationData(road, 100.0);

        assertNull(road.getVariableCrossSections());
        assertNotNull(road.getStationFacilities());
        assertEquals(1, road.getStationFacilities().runCount());
    }

    @Test
    void mirrorVariableCrossSectionWithinSegmentRange() {
        RoadCrossSection base = sectionWithWidth(6);
        RoadCrossSection wide = sectionWithWidth(12);
        RoadVariableCrossSections source = new RoadVariableCrossSections(List.of(
            StationCrossSection.at(80.0, wide)
        ));

        RoadVariableCrossSections mirrored = RoadStationMirroring.mirrorVariableCrossSectionsInRange(
            source, base, 100.0, 50.0, 100.0);

        assertNotNull(mirrored);
        assertEquals(12, widthOf(VariableCrossSectionResolver.resolveTemplate(
            roadWithSections(mirrored, base), 60.0)));
        assertEquals(6, widthOf(VariableCrossSectionResolver.resolveTemplate(
            roadWithSections(mirrored, base), 40.0)));
        assertEquals(6, widthOf(VariableCrossSectionResolver.resolveTemplate(
            roadWithSections(mirrored, base), 90.0)));
    }

    @Test
    void mirrorFacilityRunWithinSegmentRangeKeepsOutsideRuns() {
        RoadStationFacilities source = new RoadStationFacilities(List.of(
            StationFacilityRun.of(10.0, 30.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT),
            StationFacilityRun.of(60.0, 90.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT)
        ));

        RoadStationFacilities mirrored = RoadStationMirroring.mirrorStationFacilitiesInRange(
            source, 100.0, 50.0, 100.0);

        assertNotNull(mirrored);
        assertEquals(2, mirrored.runCount());
        assertEquals(10.0, mirrored.sortedRuns().get(0).getStartStation(), 1e-6);
        assertEquals(60.0, mirrored.sortedRuns().get(1).getStartStation(), 1e-6);
        assertEquals(RoadFacilitySide.RIGHT, mirrored.sortedRuns().get(1).getSide());
        assertEquals(90.0, mirrored.sortedRuns().get(1).getEndStation(), 1e-6);
    }

    private static Road roadWithSections(RoadVariableCrossSections sections, RoadCrossSection base) {
        Road road = new Road("r1");
        road.setWidth(widthOf(base));
        road.setCrossSection(base.copy());
        road.setVariableCrossSections(sections);
        return road;
    }

    private static RoadCrossSection sectionWithWidth(int width) {
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setWidth(width);
        return section;
    }

    private static int widthOf(RoadCrossSection section) {
        Integer width = section.getCarriageway().getWidth();
        return width != null ? width : 0;
    }
}
