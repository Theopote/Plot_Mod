package com.plot.plugin.road.vertical;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadNetworkEngineeringValidator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadVerticalBoundsValidatorTest {

    @Test
    void findsOutOfBoundsPvi() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-a");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        RoadVerticalAlignment alignment = new RoadVerticalAlignment();
        alignment.addPvi(new PointOfVerticalIntersection(0.0, 64.0));
        alignment.addPvi(new PointOfVerticalIntersection(100.0, 400.0));
        road.setVerticalAlignment(alignment);

        RoadElevationBounds bounds = RoadWorldElevationBounds.fallback();
        assertEquals(1, RoadVerticalBoundsValidator.findViolations(network, bounds).size());
        assertTrue(RoadNetworkEngineeringValidator.analyzePreGeneration(network).blocksBuild());
    }
}
