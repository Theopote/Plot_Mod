package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.terrain.FlatTerrainSampler;
import net.minecraft.util.math.BlockPos;
import com.plot.plugin.road.station.RoadStationing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPlanGeometryTest {

    @Test
    void fallsBackToInstanceCenterlineWithoutAlignment() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(50, 0));
        RoadEdge edge = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());

        assertEquals(RoadGeometryAuthority.INSTANCE_CENTERLINE, RoadPlanGeometry.authority(network, edge));
        List<Vec2d> resolved = RoadPlanGeometry.resolveEdgeCenterline(network, edge);
        assertEquals(2, resolved.size());
        assertEquals(0.0, resolved.getFirst().y, 1e-6);
    }

    @Test
    void usesDesignAlignmentWhenDefined() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        RoadEdge edge = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 8), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(100.0));
        road.setHorizontalAlignment(alignment);

        assertEquals(RoadGeometryAuthority.DESIGN_HORIZONTAL_ALIGNMENT, RoadPlanGeometry.authority(network, edge));
        List<Vec2d> resolved = RoadPlanGeometry.resolveEdgeCenterline(network, edge);
        assertTrue(resolved.size() >= 2);
        assertEquals(8.0, resolved.getFirst().y, 0.2);
        assertEquals(8.0, resolved.getLast().y, 0.2);
    }

    @Test
    void generationFollowsHorizontalAlignmentNotStaleCenterline() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setRoadWidth(6);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setIncludeDrainage(false);
        config.setPathSampleDistance(5.0);

        RoadGenerator generator = new RoadGenerator(
            config, null, com.plot.infrastructure.event.block.BlockProjectionHandler.getInstance());
        FlatTerrainSampler terrain = new FlatTerrainSampler(64);

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(40, 0));
        RoadEdge edge = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(40, 0)), road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 10), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(40.0));
        road.setHorizontalAlignment(alignment);

        RoadGenerationResult result = generator.generateEdge(network, edge, n1, n2, terrain, null);

        assertFalse(result.roadBlocks.isEmpty());
        double averageZ = result.roadBlocks.stream().mapToInt(BlockPos::getZ).average().orElse(0.0);
        assertEquals(10.0, averageZ, 1.5);
    }

    @Test
    void pointAtStationUsesDesignAlignment() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(80, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(80, 0)), road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 7), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(80.0));
        road.setHorizontalAlignment(alignment);

        Vec2d planPoint = RoadStationing.pointAtStation(network, road, 0.0).orElseThrow();
        Vec2d instancePoint = RoadStationing.instancePointAtStation(network, road, 0.0).orElseThrow();

        assertEquals(7.0, planPoint.y, 1e-6);
        assertEquals(0.0, instancePoint.y, 1e-6);
        assertEquals(80.0, RoadStationing.canonicalLength(network, road), 1e-6);
    }
}
