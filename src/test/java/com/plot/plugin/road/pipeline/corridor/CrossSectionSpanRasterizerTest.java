package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.solid.RoadSolidLayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossSectionSpanRasterizerTest {

    @Test
    void crossesCenterlineWhenProjectedOnOppositeSide() {
        Vec2d center = new Vec2d(0, 0);
        Vec2d normal = new Vec2d(1, 0);
        assertTrue(CrossSectionSpanRasterizer.crossesCenterline(
            center, normal, new Vec2d(-1, 0), 2.0, 1.0));
        assertFalse(CrossSectionSpanRasterizer.crossesCenterline(
            center, normal, new Vec2d(2, 0), 2.0, 1.0));
        assertFalse(CrossSectionSpanRasterizer.crossesCenterline(
            center, normal, new Vec2d(-0.1, 0), 2.0, 1.0));
    }

    @Test
    void rejectsSidewalkStampInsideCarriagewayHalfWidth() {
        Vec2d center = new Vec2d(0, 0);
        Vec2d normal = new Vec2d(1, 0);
        // 人行道预期 lateral=3，但投影落在行车道半宽(2)内
        assertTrue(CrossSectionSpanRasterizer.shouldRejectIntrudingStamp(
            center, normal, new Vec2d(1.0, 0), 3.0, 1.0, 2.0, RoadSolidLayer.SIDEWALK));
        // 正常落在外侧
        assertFalse(CrossSectionSpanRasterizer.shouldRejectIntrudingStamp(
            center, normal, new Vec2d(3.0, 0), 3.0, 1.0, 2.0, RoadSolidLayer.SIDEWALK));
        // 行车道本身不因半宽裁剪被拒
        assertFalse(CrossSectionSpanRasterizer.shouldRejectIntrudingStamp(
            center, normal, new Vec2d(1.0, 0), 1.0, 1.0, 2.0, RoadSolidLayer.ROAD));
    }
}
