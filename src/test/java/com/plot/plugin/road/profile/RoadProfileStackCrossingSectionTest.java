package com.plot.plugin.road.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.crossing.RoadStackCrossing;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadProfileStackCrossingSectionTest {

    @Test
    void selfIntersectionUsesOppositeStationCrossSection() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("self");
        road.setWidth(4);
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(50.0, sectionWithWidth(12)))));
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(100.0, 64.0))));

        RoadStackCrossing stack = RoadStackCrossing.gradeSeparated(
            road.getId(), 75.0, 25.0, new Vec2d(25.0, 0.0), 6.0);
        network.registerStackCrossing(stack);

        List<RoadProfileIntersection> markers = RoadProfileIntersectionResolver.forRoadDesign(
            network, road, new RoadSystemConfig("test"));
        assertTrue(markers.size() >= 2, "expected two stack markers");

        RoadProfileIntersection atA = markers.stream()
            .filter(marker -> Math.abs(marker.roadStation() - stack.stationA()) < 0.5)
            .findFirst()
            .orElseThrow();
        RoadProfileIntersection atB = markers.stream()
            .filter(marker -> Math.abs(marker.roadStation() - stack.stationB()) < 0.5)
            .findFirst()
            .orElseThrow();

        assertEquals(12, atA.otherCrossSection().carriagewayWidth);
        assertEquals(4, atB.otherCrossSection().carriagewayWidth);
    }

    private static RoadCrossSection sectionWithWidth(int width) {
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setWidth(width);
        return section;
    }
}
