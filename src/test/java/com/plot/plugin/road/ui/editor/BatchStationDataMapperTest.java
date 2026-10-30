package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.model.facility.RoadFacilityKind;
import com.plot.plugin.road.model.facility.RoadFacilitySide;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchStationDataMapperTest {

    @Test
    void proportionalMapsStationsByLengthRatio() {
        RoadCrossSection section = new RoadCrossSection();
        RoadVariableCrossSections source = new RoadVariableCrossSections(List.of(
            StationCrossSection.at(20.0, section),
            StationCrossSection.at(60.0, section),
            StationCrossSection.at(90.0, section)
        ));

        var shortRoad = BatchStationDataMapper.mapVariableCrossSections(
            source, 100.0, 40.0, BatchStationMappingMode.PROPORTIONAL);
        assertFalse(shortRoad.rejected());
        assertEquals(3, shortRoad.data().stationCount());
        assertEquals(8.0, shortRoad.data().sortedStations().get(0).getStation(), 1e-6);
        assertEquals(24.0, shortRoad.data().sortedStations().get(1).getStation(), 1e-6);
        assertEquals(36.0, shortRoad.data().sortedStations().get(2).getStation(), 1e-6);
        assertEquals(0, shortRoad.droppedCount());

        var longRoad = BatchStationDataMapper.mapVariableCrossSections(
            source, 100.0, 200.0, BatchStationMappingMode.PROPORTIONAL);
        assertEquals(40.0, longRoad.data().sortedStations().get(0).getStation(), 1e-6);
        assertEquals(180.0, longRoad.data().sortedStations().get(2).getStation(), 1e-6);
    }

    @Test
    void absoluteDropsOutOfRangeStations() {
        RoadCrossSection section = new RoadCrossSection();
        RoadVariableCrossSections source = new RoadVariableCrossSections(List.of(
            StationCrossSection.at(20.0, section),
            StationCrossSection.at(60.0, section),
            StationCrossSection.at(90.0, section)
        ));

        var result = BatchStationDataMapper.mapVariableCrossSections(
            source, 100.0, 40.0, BatchStationMappingMode.ABSOLUTE);
        assertEquals(1, result.data().stationCount());
        assertEquals(20.0, result.data().sortedStations().getFirst().getStation(), 1e-6);
        assertEquals(2, result.droppedCount());
    }

    @Test
    void proportionalRejectsZeroSourceLength() {
        RoadVariableCrossSections source = new RoadVariableCrossSections(List.of(
            StationCrossSection.at(20.0, new RoadCrossSection())
        ));
        var result = BatchStationDataMapper.mapVariableCrossSections(
            source, 0.0, 100.0, BatchStationMappingMode.PROPORTIONAL);
        assertTrue(result.rejected());
        assertNull(result.data());
        assertEquals(1, result.droppedCount());
        assertTrue(Double.isNaN(
            BatchStationDataMapper.mapStation(20.0, 0.0, 100.0, BatchStationMappingMode.PROPORTIONAL)));
    }

    @Test
    void proportionalRejectsZeroTargetLength() {
        RoadVariableCrossSections source = new RoadVariableCrossSections(List.of(
            StationCrossSection.at(20.0, new RoadCrossSection()),
            StationCrossSection.at(60.0, new RoadCrossSection())
        ));
        var result = BatchStationDataMapper.mapVariableCrossSections(
            source, 100.0, 0.0, BatchStationMappingMode.PROPORTIONAL);
        assertTrue(result.rejected());
        assertNull(result.data());
        assertEquals(2, result.droppedCount());
    }

    @Test
    void proportionalDropsOverlappingMappedStationsWithoutNudging() {
        // 极短目标会使多个源桩号映射到几乎同一位置 → 重叠后只保留第一个
        RoadCrossSection section = new RoadCrossSection();
        RoadVariableCrossSections source = new RoadVariableCrossSections(List.of(
            StationCrossSection.at(10.0, section),
            StationCrossSection.at(10.0000001, section),
            StationCrossSection.at(90.0, section)
        ));
        var result = BatchStationDataMapper.mapVariableCrossSections(
            source, 100.0, 0.001, BatchStationMappingMode.PROPORTIONAL);
        assertFalse(result.rejected());
        assertTrue(result.data().stationCount() >= 1);
        assertTrue(result.droppedCount() >= 1);
        var stations = result.data().sortedStations();
        for (int i = 1; i < stations.size(); i++) {
            assertTrue(stations.get(i).getStation() > stations.get(i - 1).getStation());
        }
        assertTrue(stations.getLast().getStation() <= 0.001 + 1e-9);
    }

    @Test
    void absoluteClampsFacilityRunsToTargetLength() {
        RoadStationFacilities source = new RoadStationFacilities(List.of(
            StationFacilityRun.of(10.0, 80.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT),
            StationFacilityRun.of(90.0, 120.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.RIGHT)
        ));

        var result = BatchStationDataMapper.mapStationFacilities(
            source, 150.0, 50.0, BatchStationMappingMode.ABSOLUTE);
        assertEquals(1, result.data().runCount());
        assertEquals(10.0, result.data().sortedRuns().getFirst().getStartStation(), 1e-6);
        assertEquals(50.0, result.data().sortedRuns().getFirst().getEndStation(), 1e-6);
        assertEquals(1, result.droppedCount());
        assertEquals(1, result.clampedCount());
        assertTrue(result.hasWarnings());
    }

    @Test
    void proportionalMapsFacilityRuns() {
        RoadStationFacilities source = new RoadStationFacilities(List.of(
            StationFacilityRun.of(50.0, 80.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT)
        ));
        var result = BatchStationDataMapper.mapStationFacilities(
            source, 100.0, 30.0, BatchStationMappingMode.PROPORTIONAL);
        assertEquals(1, result.data().runCount());
        assertEquals(15.0, result.data().sortedRuns().getFirst().getStartStation(), 1e-6);
        assertEquals(24.0, result.data().sortedRuns().getFirst().getEndStation(), 1e-6);
        assertEquals(RoadFacilitySide.LEFT, result.data().sortedRuns().getFirst().getSide());
    }

    @Test
    void proportionalFacilitiesRejectZeroLengthSource() {
        RoadStationFacilities source = new RoadStationFacilities(List.of(
            StationFacilityRun.of(1.0, 3.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT)
        ));
        var result = BatchStationDataMapper.mapStationFacilities(
            source, 0.0, 30.0, BatchStationMappingMode.PROPORTIONAL);
        assertTrue(result.rejected());
        assertNull(result.data());
    }
}
