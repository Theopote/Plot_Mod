package com.plot.plugin.road.solid;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.IBlockProjectionService;
import com.plot.plugin.road.RoadGeometryUtils;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 将 {@link RoadSolidPrimitive} 栅格化为 Minecraft {@link BlockPos}。
 */
public final class RoadVoxelRasterizer {
    private RoadVoxelRasterizer() {
    }

    public static BlockPos toBlockPos(Vec2d planPoint, int elevation, ICoordinateService transformer) {
        BlockPos base = RoadGeometryUtils.canvasToBlockXZ(planPoint, transformer);
        return new BlockPos(base.getX(), elevation, base.getZ());
    }

    public static List<Vec2d> sampleSpanPoints(Vec2d left, Vec2d right) {
        return sampleConnectedPlanPoints(left, right);
    }

    /**
     * 平面 4-连通折线（每步只走 X 或 Z 一格），斜向路面不会留下对角缺口。
     */
    public static List<Vec2d> sampleConnectedPlanPoints(Vec2d left, Vec2d right) {
        if (left == null || right == null) {
            return List.of();
        }
        int x = (int) Math.round(left.x);
        int z = (int) Math.round(left.y);
        int x1 = (int) Math.round(right.x);
        int z1 = (int) Math.round(right.y);
        int dx = x1 - x;
        int dz = z1 - z;
        int ax = Math.abs(dx);
        int az = Math.abs(dz);
        int sx = Integer.signum(dx);
        int sz = Integer.signum(dz);
        int steps = ax + az;
        if (steps == 0) {
            return List.of(new Vec2d(x, z));
        }
        List<Vec2d> points = new ArrayList<>(steps + 1);
        points.add(new Vec2d(x, z));
        int errX = steps / 2;
        int errZ = steps / 2;
        for (int i = 0; i < steps; i++) {
            errX -= ax;
            errZ -= az;
            if (errX <= errZ) {
                x += sx;
                errX += steps;
            } else {
                z += sz;
                errZ += steps;
            }
            points.add(new Vec2d(x, z));
        }
        return points;
    }

    public static List<BlockPos> rasterize(
            List<RoadSolidPrimitive> primitives,
            ICoordinateService transformer) {
        if (primitives == null || primitives.isEmpty()) {
            return List.of();
        }
        Set<BlockPos> unique = new LinkedHashSet<>();
        for (RoadSolidPrimitive primitive : primitives) {
            unique.add(toBlockPos(primitive.planPoint(), primitive.elevation(), transformer));
        }
        return new ArrayList<>(unique);
    }

    public static List<BlockPos> rasterizeSpan(Vec2d left, Vec2d right, int y, ICoordinateService transformer) {
        if (left == null || right == null) {
            return List.of();
        }
        Set<BlockPos> unique = new LinkedHashSet<>();
        for (Vec2d point : sampleSpanPoints(left, right)) {
            unique.add(toBlockPos(point, y, transformer));
        }
        return new ArrayList<>(unique);
    }

    public static void flushEdgeSolids(
            RoadGenerationResult result,
            RoadSolidModel solids,
            ICoordinateService transformer,
            IBlockProjectionService projectionHandler) {
        if (result == null || solids == null) {
            return;
        }
        if (solids.getDroppedDueToLimit() > 0) {
            result.droppedSolidCount += solids.getDroppedDueToLimit();
        }
        if (solids.isEmpty()) {
            return;
        }
        java.util.Objects.requireNonNull(projectionHandler, "projectionHandler");

        flushSolidsByLayer(result, solids, transformer, projectionHandler,
            RoadVoxelRasterizer::writePlacementRecord);

        result.streetlightCount = result.streetlightBlocks.size();
    }

    /**
     * 路口 solids 落地：按层应用材质覆盖并写入 placement（override 语义）。
     */
    public static void flushJunctionSolids(
            RoadGenerationResult result,
            RoadSolidModel solids,
            ICoordinateService transformer,
            IBlockProjectionService projectionHandler,
            String roadBlockId,
            String sidewalkBlockId,
            String markingBlockId) {
        flushJunctionSolids(result, solids, transformer, projectionHandler,
            roadBlockId, sidewalkBlockId, markingBlockId, Set.of());
    }

    public static void flushJunctionSolids(
            RoadGenerationResult result,
            RoadSolidModel solids,
            ICoordinateService transformer,
            IBlockProjectionService projectionHandler,
            String roadBlockId,
            String sidewalkBlockId,
            String markingBlockId, Set<BlockPos> upperPlacements) {
        if (result == null || solids == null || solids.isEmpty()) {
            return;
        }
        java.util.Objects.requireNonNull(projectionHandler, "projectionHandler");

        Set<BlockPos> protectedPlacements = new LinkedHashSet<>(upperPlacements);
        for (var primitive : solids.primitives()) {
            if (isJunctionPlazaSurface(primitive.layer())) protectedPlacements.add(
                toBlockPos(primitive.planPoint(), primitive.elevation(), transformer));
        }
        // 提取公共逻辑：先处理非隧道，再处理隧道
        flushSolidsByLayer(result, solids, transformer, projectionHandler,
            (res, prim, trans, handler) ->
                writeJunctionPlacementRecord(res, prim, trans, handler,
                    roadBlockId, sidewalkBlockId, markingBlockId, protectedPlacements));
    }

    /**
     * 通用的按层刷新逻辑：按固定图层优先级升序写入，高优先级层覆盖低优先级层。
     */
    private static void flushSolidsByLayer(
            RoadGenerationResult result,
            RoadSolidModel solids,
            ICoordinateService transformer,
            IBlockProjectionService projectionHandler,
            SolidWriter writer) {
        List<RoadSolidPrimitive> primitives = solids.primitives();
        List<Integer> insertionOrder = new ArrayList<>(primitives.size());
        for (int i = 0; i < primitives.size(); i++) {
            insertionOrder.add(i);
        }
        insertionOrder.sort(Comparator
            .comparingInt((Integer index) -> RoadSolidLayerPriority.of(primitives.get(index).layer()))
            .thenComparingInt(index -> index));

        for (int index : insertionOrder) {
            writer.write(result, primitives.get(index), transformer, projectionHandler);
        }
    }

    /**
     * Solid写入器函数式接口
     */
    @FunctionalInterface
    private interface SolidWriter {
        void write(
            RoadGenerationResult result,
            RoadSolidPrimitive primitive,
            ICoordinateService transformer,
            IBlockProjectionService projectionHandler);
    }

    private static void writePlacementRecord(
            RoadGenerationResult result,
            RoadSolidPrimitive primitive,
            ICoordinateService transformer,
            IBlockProjectionService projectionHandler) {
        BlockPos pos = toBlockPos(primitive.planPoint(), primitive.elevation(), transformer);
        appendToResultBucket(result, primitive.layer(), pos);
        if (primitive.materialId() == null) {
            return;
        }
        result.recordPlacementByLayerPriority(
            pos,
            projectionHandler.getBlockIdAt(pos),
            primitive.materialId(),
            primitive.layer(),
            primitive.materialPriority());
    }

    private static void writeJunctionPlacementRecord(
            RoadGenerationResult result,
            RoadSolidPrimitive primitive,
            ICoordinateService transformer,
            IBlockProjectionService projectionHandler,
            String roadBlockId,
            String sidewalkBlockId,
            String markingBlockId, Set<BlockPos> protectedPlacements) {
        String blockId = resolveJunctionMaterialOverride(
            primitive.layer(), roadBlockId, sidewalkBlockId, markingBlockId);
        if (blockId == null) {
            blockId = primitive.materialId();
        }
        if (blockId == null) {
            return;
        }
        BlockPos pos = toBlockPos(primitive.planPoint(), primitive.elevation(), transformer);
        appendToResultBucket(result, primitive.layer(), pos);
        // Plaza surface owns the junction footprint: edge TUNNEL clearance must not
        // outrank ROAD/SIDEWALK via layer priority after edge→aggregate merge.
        if (isJunctionPlazaSurface(primitive.layer())) {
            result.recordPlacementOverride(pos, projectionHandler.getBlockIdAt(pos), blockId);
            result.placementLayers.put(pos, primitive.layer());
            result.placementMaterialPriorities.put(pos, primitive.materialPriority());
            return;
        }
        // A sloping upper deck can be below the node's nominal clearance plane.
        // Junction excavation must respect the actual road and bridge placements.
        if ("minecraft:air".equals(blockId) && protectedPlacements.contains(pos)) return;
        result.recordPlacementByLayerPriority(
            pos,
            projectionHandler.getBlockIdAt(pos),
            blockId,
            primitive.layer(),
            primitive.materialPriority());
    }

    private static boolean isJunctionPlazaSurface(RoadSolidLayer layer) {
        return layer == RoadSolidLayer.ROAD
            || layer == RoadSolidLayer.SIDEWALK
            || layer == RoadSolidLayer.SHOULDER
            || layer == RoadSolidLayer.BIKE_LANE
            || layer == RoadSolidLayer.MEDIAN;
    }

    private static String resolveJunctionMaterialOverride(
            RoadSolidLayer layer,
            String roadBlockId,
            String sidewalkBlockId,
            String markingBlockId) {
        return switch (layer) {
            case ROAD -> roadBlockId;
            case SIDEWALK -> sidewalkBlockId;
            case MARKING -> markingBlockId;
            default -> null;
        };
    }

    private static void appendToResultBucket(
            RoadGenerationResult result,
            RoadSolidLayer layer,
            BlockPos pos) {
        switch (layer) {
            case ROAD, MEDIAN, MARKING -> result.roadBlocks.add(pos);
            case SIDEWALK, BIKE_LANE, SHOULDER, GUARDRAIL, RETAINING_WALL, SUBGRADE -> result.sidewalkBlocks.add(pos);
            case BRIDGE -> result.bridgeBlocks.add(pos);
            case TUNNEL -> result.tunnelBlocks.add(pos);
            case STREETLIGHT -> result.streetlightBlocks.add(pos);
        }
    }
}
