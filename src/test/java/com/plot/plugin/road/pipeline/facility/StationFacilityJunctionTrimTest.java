package com.plot.plugin.road.pipeline.facility;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.facility.RoadFacilityKind;
import com.plot.plugin.road.model.facility.RoadFacilitySide;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationFacilityJunctionTrimTest {

    @Test
    void trimsAtJunctionStartOfApproachEdge() {
        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 40));
        RoadNode south = network.createNode(new Vec2d(0, -40));
        RoadNode east = network.createNode(new Vec2d(80, 0));
        network.createEdge(junction.getId(), north.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 40)), null);
        network.createEdge(junction.getId(), south.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, -40)), null);

        Road road = network.createRoad("main");
        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(0.0, 80.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.BOTH)
        )));
        RoadEdge approach = network.createEdge(
            junction.getId(), east.getId(), List.of(new Vec2d(0, 0), new Vec2d(80, 0)), road.getId());

        RoadSystemConfig config = new RoadSystemConfig("test");
        ResolvedCrossSection crossSection = sectionWithoutExtras();
        StationFacilityJunctionTrim.FacilityEndpointTrim trim = StationFacilityJunctionTrim.forEdge(
            network, road, approach, crossSection, config, 1.0);

        assertTrue(trim.skipStart() > 0.0);
        assertTrue(trim.skipEnd() > 0.0);
        assertTrue(trim.skipStart() > crossSection.carriagewayWidth);
    }

    @Test
    void doesNotTrimInternalSameRoadConnection() {
        RoadNetwork network = new RoadNetwork();
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode mid = network.createNode(new Vec2d(50, 0));
        RoadNode end = network.createNode(new Vec2d(100, 0));
        Road road = network.createRoad("chain");
        RoadEdge first = network.createEdge(
            start.getId(), mid.getId(), List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());
        RoadEdge second = network.createEdge(
            mid.getId(), end.getId(), List.of(new Vec2d(50, 0), new Vec2d(100, 0)), road.getId());

        RoadSystemConfig config = new RoadSystemConfig("test");
        ResolvedCrossSection crossSection = sectionWithoutExtras();

        StationFacilityJunctionTrim.FacilityEndpointTrim firstTrim = StationFacilityJunctionTrim.forEdge(
            network, road, first, crossSection, config, 1.0);
        StationFacilityJunctionTrim.FacilityEndpointTrim secondTrim = StationFacilityJunctionTrim.forEdge(
            network, road, second, crossSection, config, 1.0);

        assertTrue(firstTrim.skipStart() > 0.0);
        assertEquals(0.0, firstTrim.skipEnd(), 1e-9);
        assertEquals(0.0, secondTrim.skipStart(), 1e-9);
        assertTrue(secondTrim.skipEnd() > 0.0);
        assertTrue(StationFacilityJunctionTrim.isSameRoadInternalConnection(network, road, mid));
        assertFalse(StationFacilityJunctionTrim.shouldTrimAtEndpoint(network, road, first, mid));
    }

    @Test
    void junctionTrimReducesGuardrailPlacementNearNode() {
        RoadSolidModel withTrim = new RoadSolidModel();
        RoadSolidModel withoutTrim = new RoadSolidModel();
        ResolvedCrossSection crossSection = sectionWithoutExtras();
        Road road = new Road("r1");
        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(0.0, 80.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.BOTH)
        )));

        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(80, 0));
        List<PathSegment> segments = List.of(segment);
        List<SegmentHeightInfo> heightInfos = List.of(new SegmentHeightInfo(segment, 64, 64, 64, 64, 0.0));

        RoadStationFacilityGenerator.generateAlongStations(
            withTrim,
            segments,
            heightInfos,
            CrossSectionBuildContext.fixed(crossSection),
            road,
            0.0,
            80.0,
            80.0,
            new StationFacilityJunctionTrim.FacilityEndpointTrim(12.0, 0.0),
            1.0,
            material -> material,
            (center, targetY) -> targetY);

        RoadStationFacilityGenerator.generateAlongStations(
            withoutTrim,
            segments,
            heightInfos,
            CrossSectionBuildContext.fixed(crossSection),
            road,
            0.0,
            80.0,
            80.0,
            StationFacilityJunctionTrim.FacilityEndpointTrim.NONE,
            1.0,
            material -> material,
            (center, targetY) -> targetY);

        assertTrue(withTrim.count(RoadSolidLayer.GUARDRAIL) > 0);
        assertTrue(withoutTrim.count(RoadSolidLayer.GUARDRAIL) > withTrim.count(RoadSolidLayer.GUARDRAIL));
    }

    @Test
    void junctionTrimSkipsFurtherThanDeadEndTrim() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        ResolvedCrossSection crossSection = sectionWithoutExtras();

        RoadNetwork junctionNetwork = buildTJunctionApproach(80.0);
        RoadEdge junctionApproach = junctionNetwork.getEdges().values().stream()
            .filter(edge -> edge.getRoadId() != null)
            .findFirst()
            .orElseThrow();
        Road junctionRoad = junctionNetwork.getRoad(junctionApproach.getRoadId());

        RoadNetwork deadEndNetwork = buildDeadEndRoad(80.0);
        RoadEdge deadEndEdge = deadEndNetwork.getEdges().values().iterator().next();
        Road deadEndRoad = deadEndNetwork.getRoad(deadEndEdge.getRoadId());

        StationFacilityJunctionTrim.FacilityEndpointTrim junctionTrim = StationFacilityJunctionTrim.forEdge(
            junctionNetwork, junctionRoad, junctionApproach, crossSection, config, 1.0);
        StationFacilityJunctionTrim.FacilityEndpointTrim deadEndTrim = StationFacilityJunctionTrim.forEdge(
            deadEndNetwork, deadEndRoad, deadEndEdge, crossSection, config, 1.0);

        assertEquals(3, junctionNetwork.getNode(junctionApproach.getStartNodeId()).getDegree());
        assertTrue(junctionTrim.skipStart() >= deadEndTrim.skipStart());
        assertTrue(junctionTrim.skipStart() > crossSection.carriagewayWidth);
        assertTrue(deadEndTrim.skipEnd() > 0.0);
    }

    private static RoadNetwork buildTJunctionApproach(double length) {
        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 40));
        RoadNode south = network.createNode(new Vec2d(0, -40));
        RoadNode east = network.createNode(new Vec2d(length, 0));
        network.createEdge(junction.getId(), north.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 40)), null);
        network.createEdge(junction.getId(), south.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, -40)), null);
        Road road = network.createRoad("main");
        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(0.0, length, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.BOTH)
        )));
        network.createEdge(
            junction.getId(),
            east.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(length, 0)),
            road.getId());
        return network;
    }

    private static RoadNetwork buildDeadEndRoad(double length) {
        RoadNetwork network = new RoadNetwork();
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(length, 0));
        Road road = network.createRoad("dead");
        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(0.0, length, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.BOTH)
        )));
        network.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(length, 0)),
            road.getId());
        return network;
    }

    private static ResolvedCrossSection sectionWithoutExtras() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setRoadWidth(6);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setIncludeDrainage(false);
        return ResolvedCrossSection.fromConfig(config);
    }
}
