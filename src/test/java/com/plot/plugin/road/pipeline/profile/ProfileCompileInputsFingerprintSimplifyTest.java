package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileCompileInputsFingerprintSimplifyTest {

    @Test
    void simplifyCollinearDropsSameDirectionDensifyVertices() {
        List<Vec2d> densified = List.of(
            new Vec2d(0, 0),
            new Vec2d(5, 0),
            new Vec2d(10, 0),
            new Vec2d(15, 0));
        List<Vec2d> simplified = ProfileCompileInputsFingerprint.simplifyCollinear(densified);
        assertEquals(2, simplified.size());
        assertEquals(0.0, simplified.getFirst().x, 1e-9);
        assertEquals(15.0, simplified.getLast().x, 1e-9);
    }

    @Test
    void simplifyCollinearKeepsOneHundredEightyDegreeTurnBack() {
        List<Vec2d> turnBack = List.of(
            new Vec2d(0, 0),
            new Vec2d(15, 0),
            new Vec2d(10, 0));
        List<Vec2d> simplified = ProfileCompileInputsFingerprint.simplifyCollinear(turnBack);
        assertEquals(3, simplified.size(), "turn-back apex must be retained");
        assertEquals(15.0, simplified.get(1).x, 1e-9);
    }

    @Test
    void equalLengthOppositeShapesProduceDistinctGeometryHashes() {
        RoadNetwork networkA = polylineRoad("a", List.of(
            new Vec2d(0, 0), new Vec2d(15, 0), new Vec2d(10, 0)));
        RoadNetwork networkB = polylineRoad("b", List.of(
            new Vec2d(0, 0), new Vec2d(-5, 0), new Vec2d(10, 0)));
        int hashA = ProfileCompileInputsFingerprint.geometryHash(networkA, networkA.getRoad("a"));
        int hashB = ProfileCompileInputsFingerprint.geometryHash(networkB, networkB.getRoad("b"));
        assertNotEquals(hashA, hashB);
        assertTrue(hashA != 0 || hashB != 0);
    }

    private static RoadNetwork polylineRoad(String roadId, List<Vec2d> points) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad(roadId);
        RoadNode start = network.createNode(points.getFirst().copy());
        RoadNode end = network.createNode(points.getLast().copy());
        network.createEdge(start.getId(), end.getId(), points, road.getId());
        return network;
    }
}
