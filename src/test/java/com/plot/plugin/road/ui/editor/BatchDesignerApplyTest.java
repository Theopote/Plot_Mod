package com.plot.plugin.road.ui.editor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
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
        RoadNetwork live = new RoadNetwork();
        Road a = live.createRoad("a");
        Road b = live.createRoad("b");
        live.createNode(new Vec2d(0, 0));
        RoadNetwork working = live.snapshot();
        RoadCrossSection wide = new RoadCrossSection();
        wide.getCarriageway().setWidth(14);
        working.getRoad(a.getId()).setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(5.0, wide)
        )));

        VariableCrossSectionEditorApplier.applyToLive(
            live, working, a.getId(), Set.of(a.getId(), b.getId()));

        assertNotNull(live.getRoad(a.getId()).getVariableCrossSections());
        assertNotNull(live.getRoad(b.getId()).getVariableCrossSections());
        assertEquals(1, live.getRoad(b.getId()).getVariableCrossSections().stationCount());
        assertEquals(
            14,
            live.getRoad(b.getId()).getVariableCrossSections().sortedStations()
                .getFirst().getCrossSection().getCarriageway().getWidth());
    }

    @Test
    void stationFacilitiesApplyCopiesToAllTargets() {
        RoadNetwork live = new RoadNetwork();
        Road a = live.createRoad("a");
        Road b = live.createRoad("b");
        assertNull(b.getStationFacilities());

        RoadNetwork working = live.snapshot();
        working.getRoad(a.getId()).setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(1.0, 3.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT)
        )));

        StationFacilityEditorApplier.applyToLive(
            live, working, a.getId(), Set.of(a.getId(), b.getId()));

        assertEquals(1, live.getRoad(a.getId()).getStationFacilities().runCount());
        assertEquals(1, live.getRoad(b.getId()).getStationFacilities().runCount());
        assertEquals(
            RoadFacilityKind.GUARDRAIL,
            live.getRoad(b.getId()).getStationFacilities().sortedRuns().getFirst().getKind());
    }
}
