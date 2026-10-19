package com.plot.infrastructure.event.block;

import com.plot.api.geometry.Vec2d;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LineToBlockHandler 单元测试。
 * <p>
 * 完整事件流依赖 Minecraft 客户端与 BlockConfigManager；此处覆盖可独立验证的光栅化逻辑。
 */
class LineToBlockHandlerTest {

    private static List<Vec2d> unitSquareWorldPoints() {
        return List.of(
                new Vec2d(0, 0),
                new Vec2d(2, 0),
                new Vec2d(2, 2),
                new Vec2d(0, 2));
    }

    @Test
    void getInstanceReturnsSingleton() {
        assertNotNull(LineToBlockHandler.getInstance());
        assertEquals(LineToBlockHandler.getInstance(), LineToBlockHandler.getInstance());
    }

    @Test
    void scanlineIntersectionsReturnPairAtBlockCenter() throws Exception {
        @SuppressWarnings("unchecked")
        List<Integer> intersections = (List<Integer>) invokeStatic(
                "getIntersectionsForScanline",
                new Class<?>[] {List.class, double.class},
                unitSquareWorldPoints(),
                0.5);

        assertEquals(2, intersections.size());
        intersections.sort(Integer::compareTo);
        assertEquals(List.of(0, 2), intersections);
    }

    @Test
    void scanlineIntersectionsUseFloorRuleForEdgeCrossings() throws Exception {
        @SuppressWarnings("unchecked")
        List<Integer> atOneHalf = (List<Integer>) invokeStatic(
                "getIntersectionsForScanline",
                new Class<?>[] {List.class, double.class},
                unitSquareWorldPoints(),
                1.5);

        assertEquals(2, atOneHalf.size());
        atOneHalf.sort(Integer::compareTo);
        assertEquals(List.of(0, 2), atOneHalf);
    }

    @Test
    void rasterizePolygonFillsScanlineSpansAtBlockCenters() throws Exception {
        LineToBlockHandler handler = LineToBlockHandler.getInstance();
        @SuppressWarnings("unchecked")
        List<BlockPos> blocks = (List<BlockPos>) invokeInstance(
                handler,
                "rasterizePolygon",
                new Class<?>[] {List.class, double.class},
                unitSquareWorldPoints(),
                64.0);

        assertEquals(6, blocks.size());
        assertTrue(blocks.stream().allMatch(pos -> pos.getY() == 64));

        Set<String> coords = new HashSet<>();
        for (BlockPos pos : blocks) {
            coords.add(pos.getX() + "," + pos.getZ());
        }
        assertEquals(Set.of(
                "0,0", "1,0", "2,0",
                "0,1", "1,1", "2,1"), coords);
    }

    @Test
    void rasterizePolygonReturnsEmptyForDegenerateInput() throws Exception {
        LineToBlockHandler handler = LineToBlockHandler.getInstance();
        @SuppressWarnings("unchecked")
        List<BlockPos> blocks = (List<BlockPos>) invokeInstance(
                handler,
                "rasterizePolygon",
                new Class<?>[] {List.class, double.class},
                List.of(new Vec2d(0, 0), new Vec2d(1, 0)),
                64.0);

        assertTrue(blocks.isEmpty());
    }

    @Test
    void rasterizePolygonHandlesNegativeCoordinates() throws Exception {
        List<Vec2d> shifted = List.of(
                new Vec2d(-2, -2),
                new Vec2d(0, -2),
                new Vec2d(0, 0),
                new Vec2d(-2, 0));
        LineToBlockHandler handler = LineToBlockHandler.getInstance();
        @SuppressWarnings("unchecked")
        List<BlockPos> blocks = (List<BlockPos>) invokeInstance(
                handler,
                "rasterizePolygon",
                new Class<?>[] {List.class, double.class},
                shifted,
                10.0);

        assertFalse(blocks.isEmpty());
        assertTrue(blocks.stream().allMatch(pos -> pos.getX() <= 0 && pos.getZ() <= 0));
        assertTrue(blocks.stream().allMatch(pos -> pos.getY() == 10));
        assertEquals(6, blocks.size());
    }

    // TODO: handleLineToBlockEvent — 空选择、空调色盘、SIMPLIFIED 模式过滤
    // TODO: DDA 线框光栅化 — 贴格线端点
    // TODO: 坐标缓存 — clearCoordinateCache 后不复用旧相机变换结果

    private static Object invokeStatic(String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = LineToBlockHandler.class.getDeclaredMethod(name, paramTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static Object invokeInstance(Object target, String name, Class<?>[] paramTypes, Object... args)
            throws Exception {
        Method method = LineToBlockHandler.class.getDeclaredMethod(name, paramTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
}
