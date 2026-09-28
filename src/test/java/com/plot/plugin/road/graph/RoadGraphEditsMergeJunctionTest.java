package com.plot.plugin.road.graph;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNetworkInvariantValidator;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import com.plot.plugin.road.model.facility.RoadFacilityKind;
import com.plot.plugin.road.model.facility.RoadFacilitySide;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadGraphEditsMergeJunctionTest {

    @Test
    void mergesNearbyJunctionNodesAndSyncsCenterlineGeometry() {
        RoadNetwork network = new RoadNetwork();
        RoadNode survivor = network.createNode(new Vec2d(0, 0));
        RoadNode absorbed = network.createNode(new Vec2d(2, 0));
        String roadA = network.createRoad("road-a").getId();
        String roadB = network.createRoad("road-b").getId();
        network.createEdge(
            survivor.getId(), network.createNode(new Vec2d(-10, 0)).getId(),
            List.of(new Vec2d(0, 0), new Vec2d(-10, 0)), roadA);
        RoadEdge absorbedEdge = network.createEdge(
            absorbed.getId(), network.createNode(new Vec2d(10, 0)).getId(),
            List.of(new Vec2d(2, 0), new Vec2d(10, 0)), roadB);

        Optional<String> merged = RoadGraphEdits.of(network).mergeJunctionNode(
            survivor.getId(), absorbed.getId());

        assertTrue(merged.isPresent());
        assertEquals(survivor.getId(), merged.get());
        assertEquals(null, network.getNode(absorbed.getId()));
        assertEquals(2, survivor.getDegree());

        RoadEdge relinked = network.getEdge(absorbedEdge.getId());
        assertNotNull(relinked);
        assertEquals(survivor.getId(), relinked.getStartNodeId());
        assertTrue(RoadGeometryUtils.pointsNear(
            relinked.getCenterlinePoints().getFirst(),
            survivor.getPosition(),
            RoadNetworkBuilder.NODE_TOLERANCE));
        assertEquals(10.0, relinked.getLength(), 1e-6);
        assertTrue(RoadNetworkInvariantValidator.validate(network).valid());
    }

    @Test
    void mergeJunctionReparameterizesStationDataWhenSegmentLengthChanges() {
        RoadNetwork network = new RoadNetwork();
        RoadNode survivor = network.createNode(new Vec2d(0, 0));
        RoadNode absorbed = network.createNode(new Vec2d(2, 0));
        network.createEdge(
            survivor.getId(), network.createNode(new Vec2d(-10, 0)).getId(),
            List.of(new Vec2d(0, 0), new Vec2d(-10, 0)),
            network.createRoad("road-a").getId());
        Road roadB = network.createRoad("road-b");
        network.createEdge(
            absorbed.getId(), network.createNode(new Vec2d(10, 0)).getId(),
            List.of(new Vec2d(2, 0), new Vec2d(10, 0)),
            roadB.getId());
        roadB.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(4, 70),
            PointOfVerticalIntersection.of(8, 72))));
        roadB.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(6.0, 7.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT))));

        assertEquals(8.0, RoadStationing.canonicalLength(network, roadB), 1e-6);

        Optional<String> merged = RoadGraphEdits.of(network).mergeJunctionNode(
            survivor.getId(), absorbed.getId());
        assertTrue(merged.isPresent());

        assertEquals(10.0, RoadStationing.canonicalLength(network, roadB), 1e-6);
        assertEquals(5.0, roadB.getVerticalAlignment().getPvis().get(0).getStation(), 1e-3);
        assertEquals(10.0, roadB.getVerticalAlignment().getPvis().get(1).getStation(), 1e-3);
        StationFacilityRun run = roadB.getStationFacilities().sortedRuns().getFirst();
        assertEquals(7.5, run.getStartStation(), 1e-3);
        assertEquals(8.75, run.getEndStation(), 1e-3);
        assertTrue(RoadNetworkInvariantValidator.validate(network).valid());
    }
}
