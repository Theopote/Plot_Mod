package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.model.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RoadCrossingDetectorTest {

    @Test
    void detectCrossing_registersRoadCrossing_notSharedNode() {
        RoadNetwork network = new RoadNetwork();
        RoadNetworkBuilder builder = new RoadNetworkBuilder();
        RoadSystemConfig config = new RoadSystemConfig("crossing");

        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false), config);
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(5, 0), new Vec2d(5, 10)), false), config);

        int nodesBefore = network.getNodes().size();
        RoadCrossingReconciler.reconcileCrossings(network);

        assertEquals(1, network.getCrossings().size());
        assertEquals(nodesBefore, network.getNodes().size());
        assertEquals(2, network.getEdges().size());
        assertFalse(network.getJunctionCount() > 0);
    }
}
