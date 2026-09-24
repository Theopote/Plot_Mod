package com.plot.plugin.road.overlay;

import com.plot.plugin.config.RoadSystemConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadOverlayGeometryTest {

    @Test
    void resolveConfigCorridorHalfWidth_includesSidewalkAndShoulder() {
        RoadSystemConfig config = new RoadSystemConfig("road_test");
        config.setRoadWidth(7);
        config.setIncludeShoulder(true);
        config.setShoulderWidth(1);
        config.setIncludeSidewalk(true);
        config.setSidewalkWidth(2);

        double halfWidth = RoadOverlayGeometry.resolveConfigCorridorHalfWidth(config);

        // 行车道半宽 3.5 + 路肩 1 + 人行道 2
        assertEquals(6.5, halfWidth, 0.01);
    }

    @Test
    void resolveConfigCorridorHalfWidth_updatesWhenRoadWidthChanges() {
        RoadSystemConfig config = new RoadSystemConfig("road_test");
        config.setIncludeSidewalk(false);
        config.setIncludeShoulder(false);
        config.setRoadWidth(5);
        double narrow = RoadOverlayGeometry.resolveConfigCorridorHalfWidth(config);

        config.setRoadWidth(9);
        double wide = RoadOverlayGeometry.resolveConfigCorridorHalfWidth(config);

        assertEquals(2.5, narrow, 0.01);
        assertEquals(4.5, wide, 0.01);
        assertTrue(wide > narrow);
    }
}
