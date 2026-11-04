package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.solid.RoadSolidLayer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeighborCarriagewayClipTest {

    @Test
    void detectsPointInsideNeighborCarriagewayAtHairpin() {
        // Z 形尖角：水平来 → 斜穿 → 水平去
        PathSegment incoming = new PathSegment(new Vec2d(0, 0), new Vec2d(10, 0));
        PathSegment outgoing = new PathSegment(new Vec2d(10, 0), new Vec2d(0, 2));
        // 落在 outgoing 行车道内、却是 incoming 外侧条带常写到的位置
        Vec2d intrusion = new Vec2d(8.0, 0.5);
        assertTrue(NeighborCarriagewayClip.insideNeighborCarriageway(
            intrusion, incoming, outgoing, 2.0));
        assertTrue(NeighborCarriagewayClip.insideAnyCarriageway(
            intrusion, List.of(incoming, outgoing), 2.0));
    }

    @Test
    void ignoresPointsOutsideAllCarriageways() {
        PathSegment a = new PathSegment(new Vec2d(0, 0), new Vec2d(20, 0));
        PathSegment b = new PathSegment(new Vec2d(20, 0), new Vec2d(20, 20));
        Vec2d outer = new Vec2d(10, 5);
        assertFalse(NeighborCarriagewayClip.insideAnyCarriageway(outer, List.of(a, b), 2.0));
    }

    @Test
    void rejectsSidewalkStampInsideNeighborCarriageway() {
        PathSegment a = new PathSegment(new Vec2d(0, 0), new Vec2d(10, 0));
        PathSegment b = new PathSegment(new Vec2d(10, 0), new Vec2d(0, 1));
        Vec2d centerOnA = new Vec2d(9.5, 0);
        Vec2d normalOnA = new Vec2d(0, 1);
        // 本段投影在行车道外，但点落入邻段行车道
        Vec2d planPoint = new Vec2d(8.5, 0.4);
        assertTrue(CrossSectionSpanRasterizer.shouldRejectIntrudingStamp(
            centerOnA, normalOnA, planPoint, 3.0, 1.0, 2.0, RoadSolidLayer.SIDEWALK, List.of(a, b)));
    }
}
