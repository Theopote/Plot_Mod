package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorizontalAlignmentJunctionConsistencyTest {

    @Test
    void detectsConflictWhenSharedJunctionDeviationExceedsTolerance() {
        RoadNetwork network = buildSharedJunctionRoad(new Vec2d(0, 5));

        List<HorizontalAlignmentJunctionConsistency.JunctionConflict> conflicts =
            HorizontalAlignmentJunctionConsistency.findConflicts(network, network.getRoad("a"), 2.0);

        assertFalse(conflicts.isEmpty());
        assertEquals("a", conflicts.getFirst().roadId());
        assertTrue(conflicts.getFirst().deviationMeters() > HorizontalAlignmentJunctionConsistency.SHARED_JUNCTION_TOLERANCE_METERS);
    }

    @Test
    void acceptsSharedJunctionWithinTolerance() {
        RoadNetwork network = buildSharedJunctionRoad(new Vec2d(0, 0.5));

        assertTrue(HorizontalAlignmentJunctionConsistency.findConflicts(network, network.getRoad("a"), 2.0).isEmpty());
        assertFalse(HorizontalAlignmentJunctionConsistency.hasConflicts(network, network.getRoad("a")));
    }

    private static RoadNetwork buildSharedJunctionRoad(Vec2d alignmentOrigin) {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("a");
        network.createRoad("b");
        RoadNode shared = network.createNode(new Vec2d(0, 0));
        RoadNode endA = network.createNode(new Vec2d(100, 0));
        RoadNode endB = network.createNode(new Vec2d(0, 100));
        network.createEdge(
            shared.getId(), endA.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)),
            roadA.getId());
        network.createEdge(
            shared.getId(), endB.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(0, 100)),
            network.getRoads().values().stream().filter(r -> !r.getId().equals("a")).findFirst().orElseThrow().getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(alignmentOrigin, 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(100.0));
        roadA.setHorizontalAlignment(alignment);
        return network;
    }
}
