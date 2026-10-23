package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.BuildHeightSample;
import com.plot.plugin.road.solid.RoadGenerationResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JunctionCellElevationResolverTest {

    @Test
    void centerCellUsesNetworkJunctionElevation() {
        CrossFixture fixture = simpleCross();
        RoadNetwork network = fixture.network();
        RoadNode junction = fixture.junction();
        Map<String, RoadGenerationResult> edgeResults = slopedEdgeResults(network, junction.getId(), 64, 72);

        int y = JunctionCellElevationResolver.resolve(
            junction.getPosition(),
            junction.getPosition(),
            junction.getId(),
            68,
            network.getEdges().values().stream().toList(),
            network,
            new RoadSystemConfig("cell-y"),
            edgeResults,
            1.0,
            10.0);

        assertEquals(68, y);
    }

    @Test
    void flatBlendRadiusKeepsPeripheralAtJunctionElevation() {
        CrossFixture fixture = simpleCross();
        RoadNetwork network = fixture.network();
        RoadNode junction = fixture.junction();
        Map<String, RoadGenerationResult> edgeResults = slopedEdgeResults(network, junction.getId(), 64, 72);

        Vec2d peripheral = new Vec2d(9, 0);
        int y = JunctionCellElevationResolver.resolve(
            peripheral,
            junction.getPosition(),
            junction.getId(),
            68,
            network.getEdges().values().stream().toList(),
            network,
            new RoadSystemConfig("cell-y"),
            edgeResults,
            1.0,
            0.0);

        assertEquals(68, y, "zero blend radius keeps a flat plaza plane");
    }

    @Test
    void peripheralCellStaysOnConsensusJunctionElevation() {
        CrossFixture fixture = simpleCross();
        RoadNetwork network = fixture.network();
        RoadNode junction = fixture.junction();
        Map<String, RoadGenerationResult> edgeResults = slopedEdgeResults(network, junction.getId(), 64, 72);

        Vec2d peripheral = new Vec2d(9, 0);
        int y = JunctionCellElevationResolver.resolve(
            peripheral,
            junction.getPosition(),
            junction.getId(),
            68,
            network.getEdges().values().stream().toList(),
            network,
            new RoadSystemConfig("cell-y"),
            edgeResults,
            1.0,
            10.0);

        assertEquals(68, y, "plaza cells stay on the consensus plane");
    }

    @Test
    void sampleStationMeasuresFromNodeAlongApproach() {
        CrossFixture fixture = simpleCross();
        var sample = JunctionCellElevationResolver.sampleStationAt(
            new Vec2d(5, 0),
            fixture.junction().getId(),
            fixture.eastEdge(),
            edge -> List.of(new Vec2d(0, 0), new Vec2d(10, 0)),
            1.0);

        assertTrue(sample != null && Math.abs(sample.worldStation() - 5.0) < 0.5);
    }

    @Test
    void sampleStationUsesEdgeStartAxisWhenJunctionIsEndNode() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("inbound");
        RoadNode far = network.createNode(new Vec2d(0, 0));
        RoadNode junction = network.createNode(new Vec2d(20, 0));
        RoadEdge edge = network.createEdge(
            far.getId(), junction.getId(),
            List.of(far.getPosition(), junction.getPosition()), road.getId());

        var sample = JunctionCellElevationResolver.sampleStationAt(
            new Vec2d(18, 0),
            junction.getId(),
            edge,
            e -> List.of(new Vec2d(0, 0), new Vec2d(20, 0)),
            1.0);

        assertNotNull(sample);
        assertEquals(18.0, sample.worldStation(), 0.5,
            "station must be from edge start so end-node approaches read the junction-side profile");
    }

    @Test
    void endNodeApproachCellUsesNearJunctionProfileElevation() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("inbound");
        RoadNode far = network.createNode(new Vec2d(0, 0));
        RoadNode junction = network.createNode(new Vec2d(20, 0));
        RoadEdge edge = network.createEdge(
            far.getId(), junction.getId(),
            List.of(far.getPosition(), junction.getPosition()), road.getId());

        RoadGenerationResult result = new RoadGenerationResult(20.0);
        List<BuildHeightSample> samples = new java.util.ArrayList<>();
        for (int station = 0; station <= 20; station++) {
            int y = 80 - station; // 80 at far start → 60 at junction end
            samples.add(new BuildHeightSample(station, y, y));
        }
        result.buildProfile = BuildHeightProfile.fromSamples(samples);
        Map<String, RoadGenerationResult> edgeResults = Map.of(edge.getId(), result);

        int y = JunctionCellElevationResolver.resolve(
            new Vec2d(18, 0),
            junction.getPosition(),
            junction.getId(),
            60,
            List.of(edge),
            network,
            new RoadSystemConfig("end-node-y"),
            edgeResults,
            1.0,
            4.0);

        assertEquals(60, y, "plaza cells stay on junctionY even on end-node approaches");
    }

    private record CrossFixture(RoadNetwork network, RoadNode junction, RoadEdge eastEdge) {
    }

    private static CrossFixture simpleCross() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode east = network.createNode(new Vec2d(10, 0));
        RoadNode north = network.createNode(new Vec2d(0, 10));
        RoadEdge eastEdge = network.createEdge(
            junction.getId(), east.getId(),
            List.of(junction.getPosition(), east.getPosition()), road.getId());
        network.createEdge(
            junction.getId(), north.getId(),
            List.of(junction.getPosition(), north.getPosition()), road.getId());
        return new CrossFixture(network, junction, eastEdge);
    }

    private static Map<String, RoadGenerationResult> slopedEdgeResults(
            RoadNetwork network,
            String junctionId,
            int startY,
            int endY) {
        Map<String, RoadGenerationResult> results = new LinkedHashMap<>();
        for (RoadEdge edge : network.getEdges().values()) {
            RoadGenerationResult result = new RoadGenerationResult(edge.getLength());
            double length = edge.getLength() > 0 ? edge.getLength() : 10.0;
            boolean fromJunction = edge.getStartNodeId().equals(junctionId);
            int y0 = fromJunction ? startY : endY;
            int y1 = fromJunction ? endY : startY;
            int steps = Math.max(2, (int) Math.ceil(length));
            List<BuildHeightSample> samples = new java.util.ArrayList<>();
            for (int step = 0; step <= steps; step++) {
                double station = length * step / steps;
                double t = steps > 0 ? (double) step / steps : 0.0;
                int y = (int) Math.round(y0 * (1.0 - t) + y1 * t);
                samples.add(new BuildHeightSample(station, y, y));
            }
            result.buildProfile = BuildHeightProfile.fromSamples(samples);
            results.put(edge.getId(), result);
        }
        return results;
    }
}
