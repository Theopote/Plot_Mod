package com.plot.infrastructure.event.block;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.Events;
import com.plot.ui.dialog.LineToBlockSettingsDialog.ConversionMode;
import com.plot.utils.PlotI18n;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

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

    private EventBus eventBus;
    private AppState appState;

    private static List<Vec2d> unitSquareWorldPoints() {
        return List.of(
                new Vec2d(0, 0),
                new Vec2d(2, 0),
                new Vec2d(2, 2),
                new Vec2d(0, 2));
    }

    @BeforeEach
    void setUp() {
        eventBus = ApplicationContext.getInstance().getEventBus();
        appState = ApplicationContext.getInstance().getAppState();
        appState.clearSelection();
        LineToBlockHandler.getInstance();
    }

    @AfterEach
    void tearDown() {
        appState.clearSelection();
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

    @Test
    void handleLineToBlockEventWarnsWhenSelectionIsEmpty() throws Exception {
        AtomicReference<Events.WarningEvent> warning = new AtomicReference<>();
        eventBus.subscribe(Events.WarningEvent.class, event -> {
            if (event instanceof Events.WarningEvent warningEvent) {
                warning.set(warningEvent);
            }
        });

        appState.clearSelection();
        invokeHandleLineToBlockEvent(new LineToBlockEvent(
                List.of(),
                ConversionMode.FULL,
                0.5f,
                64.0,
                true,
                false));

        assertNotNull(warning.get());
        assertEquals("LineToBlockHandler", warning.get().getSource());
        assertEquals(
                PlotI18n.tr("status.plot.line_to_block.no_selection"),
                warning.get().getMessage());
    }

    @Test
    void handleLineToBlockEventWarnsWhenPaletteIsEmpty() throws Exception {
        AtomicReference<Events.WarningEvent> warning = new AtomicReference<>();
        eventBus.subscribe(Events.WarningEvent.class, event -> {
            if (event instanceof Events.WarningEvent warningEvent) {
                warning.set(warningEvent);
            }
        });

        LineShape line = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        appState.addShape(line);
        appState.setSelectedShapes(List.of(line));

        invokeHandleLineToBlockEvent(new LineToBlockEvent(
                null,
                ConversionMode.FULL,
                0.5f,
                64.0,
                true,
                false));

        assertNotNull(warning.get());
        assertEquals(
                PlotI18n.tr("status.plot.line_to_block.no_palette"),
                warning.get().getMessage());
    }

    @Test
    void simplifiedModeFiltersInteriorCellsButKeepsEndpoints() throws Exception {
        LineToBlockHandler handler = LineToBlockHandler.getInstance();

        // 近乎水平的长线：中间格内穿越长度很短，高阈值下会被 SIMPLIFIED 过滤
        @SuppressWarnings("unchecked")
        List<BlockPos> full = (List<BlockPos>) invokeInstance(
                handler,
                "rasterizeLineSegment",
                new Class<?>[] {
                        double.class, double.class, double.class, double.class,
                        double.class, ConversionMode.class, float.class
                },
                0.0, 0.0, 40.0, 0.2, 64.0, ConversionMode.FULL, 0.95f);

        @SuppressWarnings("unchecked")
        List<BlockPos> simplified = (List<BlockPos>) invokeInstance(
                handler,
                "rasterizeLineSegment",
                new Class<?>[] {
                        double.class, double.class, double.class, double.class,
                        double.class, ConversionMode.class, float.class
                },
                0.0, 0.0, 40.0, 0.2, 64.0, ConversionMode.SIMPLIFIED, 0.95f);

        assertFalse(full.isEmpty());
        assertFalse(simplified.isEmpty());
        assertTrue(simplified.size() < full.size());
        assertEquals(full.getFirst(), simplified.getFirst());
        assertEquals(full.getLast(), simplified.getLast());
    }

    @Test
    void ddaRasterizationHandlesGridLineEndpoints() throws Exception {
        LineToBlockHandler handler = LineToBlockHandler.getInstance();

        @SuppressWarnings("unchecked")
        List<BlockPos> forward = (List<BlockPos>) invokeInstance(
                handler,
                "rasterizeLineGridTraversal",
                new Class<?>[] {double.class, double.class, double.class, double.class, int.class},
                0.0, 0.0, 3.0, 0.0, 64);

        assertFalse(forward.isEmpty());
        assertEquals(new BlockPos(0, 64, 0), forward.getFirst());
        assertEquals(new BlockPos(3, 64, 0), forward.getLast());

        @SuppressWarnings("unchecked")
        List<BlockPos> backward = (List<BlockPos>) invokeInstance(
                handler,
                "rasterizeLineGridTraversal",
                new Class<?>[] {double.class, double.class, double.class, double.class, int.class},
                5.0, 0.0, 0.0, 0.0, 64);

        assertFalse(backward.isEmpty());
        assertEquals(new BlockPos(4, 64, 0), backward.getFirst());
        assertEquals(new BlockPos(0, 64, 0), backward.getLast());
    }

    @Test
    void clearCoordinateCacheRemovesCachedEntries() throws Exception {
        LineToBlockHandler handler = LineToBlockHandler.getInstance();
        ConcurrentHashMap<String, Vec2d> cache = getCoordinateCache(handler);
        cache.put("test-key", new Vec2d(1, 2));

        invokeInstance(handler, "clearCoordinateCache", new Class<?>[] {});

        assertTrue(cache.isEmpty());
    }

    @Test
    void resolveGhostPreviewYPlacesBlockAboveSampledGround() {
        assertEquals(65, LineToBlockHandler.resolveGhostPreviewY(10, 20, Double.NaN, 64));
    }

    @Test
    void resolveGhostPreviewYUsesUserSpecifiedElevation() {
        assertEquals(80, LineToBlockHandler.resolveGhostPreviewY(10, 20, 80.9, 64));
    }

    @Test
    void resolveGhostPreviewYFollowsTerrainWhenColumnsDiffer() {
        assertEquals(71, LineToBlockHandler.resolveGhostPreviewY(0, 0, Double.NaN, 70));
        assertEquals(51, LineToBlockHandler.resolveGhostPreviewY(5, 5, Double.NaN, 50));
    }

    @Test
    void handleLineToBlockEventClearsCoordinateCacheBeforeProcessing() throws Exception {
        LineToBlockHandler handler = LineToBlockHandler.getInstance();
        ConcurrentHashMap<String, Vec2d> cache = getCoordinateCache(handler);
        cache.put("stale-key", new Vec2d(9, 9));

        appState.clearSelection();
        invokeHandleLineToBlockEvent(new LineToBlockEvent(
                List.of(),
                ConversionMode.FULL,
                0.5f,
                64.0,
                true,
                false));

        assertTrue(cache.isEmpty());
    }

    private static void invokeHandleLineToBlockEvent(LineToBlockEvent event) throws Exception {
        LineToBlockHandler handler = LineToBlockHandler.getInstance();
        Method method = LineToBlockHandler.class.getDeclaredMethod("handleLineToBlockEvent", com.plot.infrastructure.event.base.Event.class);
        method.setAccessible(true);
        method.invoke(handler, event);
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, Vec2d> getCoordinateCache(LineToBlockHandler handler)
            throws Exception {
        Field field = LineToBlockHandler.class.getDeclaredField("coordinateCache");
        field.setAccessible(true);
        return (ConcurrentHashMap<String, Vec2d>) field.get(handler);
    }

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
