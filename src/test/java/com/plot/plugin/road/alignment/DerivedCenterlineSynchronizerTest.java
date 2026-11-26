package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.core.terrain.FlatTerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerivedCenterlineSynchronizerTest {

    @Test
    void synchronizeRoadRequiresReliableDomainMap() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(80, 0));
        RoadEdge edge = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(80, 0)), road.getId());

        // Offset HA: no reliable map → derived refresh must no-op.
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 6), 0.0, List.of(HorizontalAlignmentElement.tangent(80.0))));
        assertFalse(DerivedCenterlineSynchronizer.synchronizeRoad(network, road, 2.0));
        assertEquals(0.0, edge.getCenterlinePoints().getFirst().y, 1e-6);

        // Matching HA: derived refresh may rewrite densified design samples.
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0), 0.0, List.of(HorizontalAlignmentElement.tangent(80.0))));
        assertTrue(DerivedCenterlineSynchronizer.synchronizeRoad(network, road, 2.0));
        assertTrue(HorizontalAlignmentCenterlineConsistency.evaluate(network, road).isConsistent());
    }

    @Test
    void synchronizeAllSkipsRoadsWithoutAlignment() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(20, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(20, 0)), road.getId());

        assertEquals(0, DerivedCenterlineSynchronizer.synchronizeAll(network, 2.0));
    }

    @Test
    void generateEdgeDoesNotSilentlyMaterializeUnreliableOffsetHa() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setRoadWidth(6);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setPathSampleDistance(4.0);

        RoadGenerator generator = new RoadGenerator(
            config, com.plot.test.world.IdentityCoordinateService.INSTANCE, com.plot.infrastructure.event.block.BlockProjectionHandler.getInstance());
        FlatTerrainSampler terrain = new FlatTerrainSampler(64);

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(60, 0));
        RoadEdge edge = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(60, 0)), road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 12), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(60.0));
        road.setHorizontalAlignment(alignment);

        assertFalse(HorizontalAlignmentCenterlineConsistency.isMaterializable(network, road));
        generator.generateEdge(network, edge, n1, n2, terrain, null);

        // Without a reliable map, generate must not rewrite instance onto the offset HA.
        assertEquals(0.0, edge.getCenterlinePoints().getFirst().y, 0.3);
    }
}
