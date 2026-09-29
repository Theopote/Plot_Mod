package com.plot.plugin.road.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadOverlayGeometryTest {

    private static final List<Vec2d> CENTERLINE = List.of(new Vec2d(0, 0), new Vec2d(100, 0));

    @Test
    void resolveConfigPavementHalfWidthBlocks_includesSidewalkAndShoulder() {
        RoadSystemConfig config = new RoadSystemConfig("road_test");
        config.setRoadWidth(7);
        config.setIncludeShoulder(true);
        config.setShoulderWidth(1);
        config.setIncludeSidewalk(true);
        config.setSidewalkWidth(2);
        config.setIncludeSlopeBatter(false);

        double halfWidth = RoadOverlayGeometry.resolveConfigPavementHalfWidthBlocks(config);

        // 行车道半宽 3.5 + 路肩 1 + 人行道 2
        assertEquals(6.5, halfWidth, 0.01);
    }

    @Test
    void resolveConfigCorridorHalfWidth_includesSidewalkButNotShoulder() {
        RoadSystemConfig config = new RoadSystemConfig("road_test");
        config.setRoadWidth(7);
        config.setIncludeShoulder(true);
        config.setShoulderWidth(1);
        config.setIncludeSidewalk(true);
        config.setSidewalkWidth(2);
        config.setIncludeDrainage(true);
        config.setIncludeSlopeBatter(true);

        double corridorHalfWidth = RoadOverlayGeometry.resolveConfigCorridorHalfWidth(
            config, CENTERLINE, IdentityCoordinateService.INSTANCE);
        double pavementHalfWidth = RoadOverlayGeometry.resolveConfigPavementHalfWidthBlocks(config);

        assertEquals(5.5, corridorHalfWidth, 0.01);
        assertEquals(7.5, pavementHalfWidth, 0.01);
        assertTrue(pavementHalfWidth > corridorHalfWidth);
    }

    @Test
    void resolveConfigCorridorHalfWidth_updatesWhenRoadWidthChanges() {
        RoadSystemConfig config = new RoadSystemConfig("road_test");
        config.setIncludeSidewalk(false);
        config.setIncludeShoulder(false);
        config.setIncludeSlopeBatter(false);
        config.setRoadWidth(5);
        double narrow = RoadOverlayGeometry.resolveConfigCorridorHalfWidth(
            config, CENTERLINE, IdentityCoordinateService.INSTANCE);

        config.setRoadWidth(9);
        double wide = RoadOverlayGeometry.resolveConfigCorridorHalfWidth(
            config, CENTERLINE, IdentityCoordinateService.INSTANCE);

        assertEquals(2.5, narrow, 0.01);
        assertEquals(4.5, wide, 0.01);
        assertTrue(wide > narrow);
    }

    @Test
    void resolveRoadCorridor_usesPlanCenterlineWhenHorizontalAlignmentDefined() {
        RoadSystemConfig config = new RoadSystemConfig("road_test");
        config.setRoadWidth(6);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setIncludeDrainage(false);
        config.setIncludeSlopeBatter(false);

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        road.setIncludeSlopeBatter(false);
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 8), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(100.0));
        road.setHorizontalAlignment(alignment);

        List<Vec2d> planCenterline = RoadOverlayGeometry.resolvePlanCenterline(network, road);
        List<Vec2d> storedCenterline = RoadOverlayGeometry.resolveRoadCenterline(network, road);
        List<Vec2d> corridor = RoadOverlayGeometry.resolveRoadCorridor(
            network, road, config, IdentityCoordinateService.INSTANCE);

        assertTrue(planCenterline.size() >= 2);
        assertEquals(0.0, storedCenterline.getFirst().y, 1e-6);
        assertEquals(8.0, planCenterline.getFirst().y, 0.2);
        assertFalse(corridor.isEmpty());
        assertTrue(RoadOverlayGeometry.containsPoint(corridor, 50, 8));
        assertFalse(RoadOverlayGeometry.containsPoint(corridor, 50, 0));
    }
}
