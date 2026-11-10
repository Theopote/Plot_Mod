package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 走廊↔路口接缝：铺装列在清障外形内 4-邻域膨胀一格，闭合单列漏缝。
 */
class RoadJunctionSurfaceDilateTest {

    @Test
    void dilateFillsFourNeighborsInsideClearanceOnly() {
        RoadJunctionGenerator junctionGenerator = new RoadJunctionGenerator(
            new RoadGenerator(
                new RoadSystemConfig("dilate-seam"),
                IdentityCoordinateService.INSTANCE,
                BlockProjectionHandler.getInstance()));

        List<Vec2d> clearance = List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10));
        // Identity 下 canvas≈world；取整数中心避免 round 半格歧义
        List<Vec2d> surface = List.of(new Vec2d(5.0, 5.0));

        List<Vec2d> dilated = junctionGenerator.dilateSurfaceCellsWithinClearance(surface, clearance);
        Set<Long> columns = columnKeys(dilated);

        assertTrue(columns.contains(columnKey(5, 5)), "seed column");
        assertTrue(columns.contains(columnKey(6, 5)), "east neighbor");
        assertTrue(columns.contains(columnKey(4, 5)), "west neighbor");
        assertTrue(columns.contains(columnKey(5, 6)), "south neighbor");
        assertTrue(columns.contains(columnKey(5, 4)), "north neighbor");
        assertEquals(5, columns.size(), "seed + 4 neighbors; got=" + columns.size());
        for (Vec2d cell : dilated) {
            assertTrue(RoadGeometryUtils.pointInPolygon(cell, clearance),
                "dilated cell must stay inside clearance: " + cell);
        }
    }

    @Test
    void dilateDoesNotEscapeClearancePolygon() {
        RoadJunctionGenerator junctionGenerator = new RoadJunctionGenerator(
            new RoadGenerator(
                new RoadSystemConfig("dilate-clip"),
                IdentityCoordinateService.INSTANCE,
                BlockProjectionHandler.getInstance()));

        // 清障只覆盖 x≥0；种子贴在西边界，西向邻格应被裁掉
        List<Vec2d> clearance = List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10));
        List<Vec2d> surface = List.of(new Vec2d(0.0, 5.0));

        List<Vec2d> dilated = junctionGenerator.dilateSurfaceCellsWithinClearance(surface, clearance);
        Set<Long> columns = columnKeys(dilated);

        assertTrue(columns.contains(columnKey(0, 5)));
        assertFalse(columns.contains(columnKey(-1, 5)), "must not pave outside clearance");
        assertTrue(columns.contains(columnKey(1, 5)));
        assertTrue(columns.contains(columnKey(0, 4)));
        assertTrue(columns.contains(columnKey(0, 6)));
        for (Vec2d cell : dilated) {
            assertTrue(RoadGeometryUtils.pointInPolygon(cell, clearance),
                "dilated cell must stay inside clearance: " + cell);
        }
    }

    private static Set<Long> columnKeys(List<Vec2d> cells) {
        Set<Long> keys = new HashSet<>();
        for (Vec2d cell : cells) {
            var world = RoadGeometryUtils.canvasToBlockXZ(cell, IdentityCoordinateService.INSTANCE);
            keys.add(columnKey(world.getX(), world.getZ()));
        }
        return keys;
    }

    private static long columnKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }
}
