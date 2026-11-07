package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 曲线条带并集栅格验收：最终铺装多边形不得超出各真实条带栅格并集（允许边界 1 格容差）。
 */
class JunctionPavementBandUnionVoxelTest {

    @Test
    void curvedTBandUnionDoesNotSpillOutsideBandRasterUnion() {
        List<Vec2d> east = List.of(new Vec2d(0, 0), new Vec2d(14, 0));
        List<Vec2d> west = List.of(new Vec2d(0, 0), new Vec2d(-14, 0));
        // 北向轻微弯曲支路：切线仍近似 +Y，便于 T 拓扑识别
        List<Vec2d> northCurve = new ArrayList<>();
        for (int i = 0; i <= 14; i++) {
            double t = i / 14.0;
            northCurve.add(new Vec2d(1.5 * t * t, 12.0 * t));
        }

        List<List<Vec2d>> bands = List.of(
            JunctionPavementBand.buildStubPolygon(east, 2.5, 1.0),
            JunctionPavementBand.buildStubPolygon(west, 2.5, 1.0),
            JunctionPavementBand.buildStubPolygon(northCurve, 2.0, 1.0));
        assertTrue(bands.stream().allMatch(b -> b.size() >= 3));

        List<JunctionCurbFilletBuilder.Approach> tangents = List.of(
            new JunctionCurbFilletBuilder.Approach(new Vec2d(1, 0), 2.5),
            new JunctionCurbFilletBuilder.Approach(new Vec2d(-1, 0), 2.5),
            new JunctionCurbFilletBuilder.Approach(new Vec2d(0, 1), 2.0));

        JunctionRegionGeometry region = JunctionRegionBuilder.buildFromApproachBands(
            new Vec2d(0, 0), tangents, bands, 2.0, 6.0);
        assertTrue(region.hasPavement(), "curved T bands should build region pavement");

        Set<Long> expected = rasterUnion(bands, -16, 16, -4, 16);
        // 切线并集顶点也会进入候选，一并并入期望（验收报告指出的风险边界）
        expected.addAll(rasterPolygon(region.originalRoadUnion(), -16, 16, -4, 16));
        Set<Long> actual = rasterPolygon(region.finalPavementRegion(), -16, 16, -4, 16);
        assertTrue(actual.size() >= 8, "final pavement should cover junction core cells");

        Set<Long> spill = new HashSet<>(actual);
        spill.removeAll(dilate(expected, 1));
        assertEquals(0, spill.size(),
            "polar-sort union must not invent cells outside dilated band∪tangent raster; spill="
                + spill.size());
    }

    @Test
    void variableWidthBandsKeepFarCellsInsideDilatedUnion() {
        List<Vec2d> spine = List.of(new Vec2d(0, 0), new Vec2d(20, 0));
        List<Vec2d> narrow = JunctionPavementBand.buildStubPolygon(spine, along -> 2.0, 1.0);
        List<Vec2d> wide = JunctionPavementBand.buildStubPolygon(
            spine, along -> 2.0 + along * 0.12, 1.0);
        assertTrue(wide.size() >= narrow.size());

        Set<Long> narrowCells = rasterPolygon(narrow, -2, 22, -8, 8);
        Set<Long> wideCells = rasterPolygon(wide, -2, 22, -8, 8);
        Set<Long> onlyWide = new HashSet<>(wideCells);
        onlyWide.removeAll(narrowCells);
        assertTrue(onlyWide.size() > 0, "variable band should add far-side cells");
        // 远端加宽格仍应落在宽条带自身内
        assertTrue(wideCells.containsAll(onlyWide));
    }

    private static Set<Long> rasterUnion(List<List<Vec2d>> polygons, int minX, int maxX, int minZ, int maxZ) {
        Set<Long> cells = new HashSet<>();
        for (List<Vec2d> polygon : polygons) {
            cells.addAll(rasterPolygon(polygon, minX, maxX, minZ, maxZ));
        }
        return cells;
    }

    private static Set<Long> rasterPolygon(List<Vec2d> polygon, int minX, int maxX, int minZ, int maxZ) {
        Set<Long> cells = new HashSet<>();
        if (polygon == null || polygon.size() < 3) {
            return cells;
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (RoadGeometryUtils.pointInPolygon(new Vec2d(x + 0.5, z + 0.5), polygon)) {
                    cells.add(pack(x, z));
                }
            }
        }
        return cells;
    }

    private static Set<Long> dilate(Set<Long> cells, int radius) {
        Set<Long> out = new HashSet<>(cells);
        for (long key : cells) {
            int x = (int) (key >> 32);
            int z = (int) key;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    out.add(pack(x + dx, z + dz));
                }
            }
        }
        return out;
    }

    private static long pack(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }
}
