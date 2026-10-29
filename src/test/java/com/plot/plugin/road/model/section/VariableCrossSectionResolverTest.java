package com.plot.plugin.road.model.section;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.VariableCrossSectionPersistence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariableCrossSectionResolverTest {

    @Test
    void usesBaseCrossSectionBeforeFirstStation() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        road.setWidth(6);
        RoadCrossSection wide = sectionWithWidth(12);
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(50.0, wide)
        )));

        RoadCrossSection at20 = VariableCrossSectionResolver.resolveTemplate(road, 20.0);
        assertEquals(6, widthOf(at20));
    }

    @Test
    void usesStationCrossSectionAtAndAfterStation() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        road.setWidth(6);
        RoadCrossSection wide = sectionWithWidth(12);
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(50.0, wide)
        )));

        assertEquals(12, widthOf(VariableCrossSectionResolver.resolveTemplate(road, 50.0)));
        assertEquals(12, widthOf(VariableCrossSectionResolver.resolveTemplate(road, 80.0)));
    }

    @Test
    void stepsBetweenMultipleStations() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        road.setWidth(6);
        RoadCrossSection mid = sectionWithWidth(10);
        RoadCrossSection wide = sectionWithWidth(14);
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(40.0, mid),
            StationCrossSection.at(80.0, wide)
        )));

        assertEquals(6, widthOf(VariableCrossSectionResolver.resolveTemplate(road, 30.0)));
        assertEquals(10, widthOf(VariableCrossSectionResolver.resolveTemplate(road, 60.0)));
        assertEquals(14, widthOf(VariableCrossSectionResolver.resolveTemplate(road, 100.0)));
    }

    @Test
    void resolveForEdgeUsesSegmentStartStation() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        RoadNetwork network = new RoadNetwork();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        Road road = network.createRoad("r1");
        road.setWidth(6);
        RoadCrossSection wide = sectionWithWidth(12);
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(50.0, wide)
        )));
        RoadEdge edge = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        ResolvedCrossSection start = RoadModelUtils.resolveCrossSection(network, edge, config);
        assertEquals(6, start.carriagewayWidth);

        RoadNode n3 = network.createNode(new Vec2d(150, 0));
        RoadEdge tail = network.createEdge(
            n2.getId(), n3.getId(), List.of(new Vec2d(100, 0), new Vec2d(150, 0)), road.getId());
        ResolvedCrossSection tailSection = RoadModelUtils.resolveCrossSection(network, tail, config);
        assertEquals(12, tailSection.carriagewayWidth);
    }

    @Test
    void persistenceRoundTrip() {
        RoadVariableCrossSections variable = new RoadVariableCrossSections(List.of(
            StationCrossSection.at(30.0, sectionWithWidth(8)),
            StationCrossSection.at(70.0, sectionWithWidth(12))
        ), VariableCrossSectionTransitionMode.SMOOTH);

        RoadVariableCrossSections restored = VariableCrossSectionPersistence.fromData(
            VariableCrossSectionPersistence.toData(variable));

        assertNotNull(restored);
        assertEquals(2, restored.stationCount());
        assertEquals(12, widthOf(restored.sortedStations().get(1).getCrossSection()));
        assertEquals(VariableCrossSectionTransitionMode.SMOOTH, restored.getTransitionMode());
    }

    @Test
    void persistenceDefaultsMissingTransitionModeToStep() {
        VariableCrossSectionPersistence.VariableCrossSectionsData data =
            VariableCrossSectionPersistence.toData(new RoadVariableCrossSections(List.of(
                StationCrossSection.at(10.0, sectionWithWidth(8))
            ), VariableCrossSectionTransitionMode.SMOOTH));
        assertNotNull(data);
        data.transitionMode = null;

        RoadVariableCrossSections restored = VariableCrossSectionPersistence.fromData(data);
        assertNotNull(restored);
        assertEquals(VariableCrossSectionTransitionMode.STEP, restored.getTransitionMode());
        assertEquals(VariableCrossSectionTransitionMode.STEP, VariableCrossSectionTransitionMode.fromStorage(null));
        assertEquals(VariableCrossSectionTransitionMode.STEP, VariableCrossSectionTransitionMode.fromStorage("nope"));
    }

    @Test
    void smoothLerpsCarriagewayWidthBetweenStations() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        road.setWidth(6);
        RoadVariableCrossSections variable = new RoadVariableCrossSections(List.of(
            StationCrossSection.at(0.0, sectionWithWidth(6)),
            StationCrossSection.at(100.0, sectionWithWidth(16))
        ), VariableCrossSectionTransitionMode.SMOOTH);
        road.setVariableCrossSections(variable);

        assertEquals(6, VariableCrossSectionResolver.resolve(network, road, 0.0, config).carriagewayWidth);
        assertEquals(11, VariableCrossSectionResolver.resolve(network, road, 50.0, config).carriagewayWidth);
        assertEquals(16, VariableCrossSectionResolver.resolve(network, road, 100.0, config).carriagewayWidth);
        // 阶梯模板语义不变
        assertEquals(6, widthOf(VariableCrossSectionResolver.resolveTemplate(road, 50.0)));
    }

    @Test
    void stepStillJumpsAtStation() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        road.setWidth(6);
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(0.0, sectionWithWidth(6)),
            StationCrossSection.at(100.0, sectionWithWidth(16))
        ), VariableCrossSectionTransitionMode.STEP));

        assertEquals(6, VariableCrossSectionResolver.resolve(network, road, 50.0, config).carriagewayWidth);
        assertEquals(16, VariableCrossSectionResolver.resolve(network, road, 100.0, config).carriagewayWidth);
    }

    @Test
    void jsonRoundTripThroughNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("var");
        road.setWidth(6);
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(25.0, sectionWithWidth(10))
        ), VariableCrossSectionTransitionMode.SMOOTH));

        RoadNetwork restored = RoadNetwork.parseSnapshot(network.toJson());
        Road restoredRoad = restored.getRoad("var");

        assertNotNull(restoredRoad.getVariableCrossSections());
        assertEquals(1, restoredRoad.getVariableCrossSections().stationCount());
        assertEquals(10, widthOf(restoredRoad.getVariableCrossSections().sortedStations().getFirst().getCrossSection()));
        assertEquals(
            VariableCrossSectionTransitionMode.SMOOTH,
            restoredRoad.getVariableCrossSections().getTransitionMode());
    }

    @Test
    void lerpContinuousTakesDiscreteFieldsFromNearerSide() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        RoadCrossSection narrow = sectionWithWidth(6);
        narrow.getCarriageway().setLaneCount(2);
        narrow.getCarriageway().setMaterial("minecraft:stone");
        RoadCrossSection wide = sectionWithWidth(14);
        wide.getCarriageway().setLaneCount(4);
        wide.getCarriageway().setMaterial("minecraft:deepslate");

        ResolvedCrossSection mid = ResolvedCrossSection.lerpContinuous(
            ResolvedCrossSection.resolve(narrow, config),
            ResolvedCrossSection.resolve(wide, config),
            0.25);
        assertEquals(8, mid.carriagewayWidth);
        assertEquals(2, mid.laneCount);
        assertEquals("minecraft:stone", mid.carriagewayMaterial.getPrimaryMaterial());

        ResolvedCrossSection pastHalf = ResolvedCrossSection.lerpContinuous(
            ResolvedCrossSection.resolve(narrow, config),
            ResolvedCrossSection.resolve(wide, config),
            0.75);
        assertEquals(12, pastHalf.carriagewayWidth);
        assertEquals(4, pastHalf.laneCount);
        assertEquals("minecraft:deepslate", pastHalf.carriagewayMaterial.getPrimaryMaterial());
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
