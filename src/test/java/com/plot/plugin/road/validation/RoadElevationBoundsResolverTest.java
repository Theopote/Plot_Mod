package com.plot.plugin.road.validation;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadNetworkEngineeringValidator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadElevationBounds;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadWorldElevationBounds;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadElevationBoundsResolverTest {

    @Test
    void customWorldBoundsAreUsedByPreflight() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-a");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        RoadVerticalAlignment alignment = new RoadVerticalAlignment();
        alignment.addPvi(new PointOfVerticalIntersection(0.0, 64.0));
        alignment.addPvi(new PointOfVerticalIntersection(100.0, 200.0));
        road.setVerticalAlignment(alignment);

        RoadElevationBounds custom = new RoadElevationBounds(0, 127);
        assertTrue(RoadNetworkEngineeringValidator.analyzePreGeneration(network, custom).blocksBuild());
        assertFalse(RoadNetworkEngineeringValidator.analyzePreGeneration(
            network, RoadWorldElevationBounds.fallback()).blocksBuild());
    }
}
