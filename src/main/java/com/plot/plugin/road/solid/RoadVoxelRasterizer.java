package com.plot.plugin.road.solid;

import com.plot.api.geometry.Vec2d;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.road.RoadGeometryUtils;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 将 {@link RoadSolidPrimitive} 栅格化为 Minecraft {@link BlockPos}。
 */
public final class RoadVoxelRasterizer {
    private RoadVoxelRasterizer() {
    }

    public static BlockPos toBlockPos(Vec2d planPoint, int elevation, CoordinateTransformer transformer) {
        BlockPos base = RoadGeometryUtils.canvasToBlockXZ(planPoint, transformer);
        return new BlockPos(base.getX(), elevation, base.getZ());
    }

    public static List<Vec2d> sampleSpanPoints(Vec2d left, Vec2d right) {
        if (left == null || right == null) {
            return List.of();
        }
        double span = left.distance(right);
        int steps = Math.max(1, (int) Math.ceil(span * 2.0));
        List<Vec2d> points = new ArrayList<>(steps + 1);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            points.add(left.lerp(right, t));
        }
        return points;
    }

    public static List<BlockPos> rasterize(
            List<RoadSolidPrimitive> primitives,
            CoordinateTransformer transformer) {
        if (primitives == null || primitives.isEmpty()) {
            return List.of();
        }
        Set<BlockPos> unique = new LinkedHashSet<>();
        for (RoadSolidPrimitive primitive : primitives) {
            unique.add(toBlockPos(primitive.planPoint(), primitive.elevation(), transformer));
        }
        return new ArrayList<>(unique);
    }

    public static List<BlockPos> rasterizeSpan(Vec2d left, Vec2d right, int y, CoordinateTransformer transformer) {
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
            CoordinateTransformer transformer) {
        if (result == null || solids == null || solids.isEmpty()) {
            return;
        }
        BlockProjectionHandler projectionHandler = BlockProjectionHandler.getInstance();

        // 提取公共逻辑：先处理非隧道，再处理隧道
        flushSolidsByLayer(result, solids, transformer, projectionHandler,
            (res, prim, trans, handler, overrideExisting) ->
                writePlacementRecord(res, prim, trans, handler, overrideExisting));

        result.streetlightCount = result.streetlightBlocks.size();
    }

    /**
     * 路口 solids 落地：按层应用材质覆盖并写入 placement（override 语义）。
     */
    public static void flushJunctionSolids(
            RoadGenerationResult result,
            RoadSolidModel solids,
            CoordinateTransformer transformer,
            String roadBlockId,
            String sidewalkBlockId,
            String markingBlockId) {
        if (result == null || solids == null || solids.isEmpty()) {
            return;
        }
        BlockProjectionHandler projectionHandler = BlockProjectionHandler.getInstance();

        // 提取公共逻辑：先处理非隧道，再处理隧道
        flushSolidsByLayer(result, solids, transformer, projectionHandler,
            (res, prim, trans, handler, overrideExisting) ->
                writeJunctionPlacementRecord(res, prim, trans, handler,
                    roadBlockId, sidewalkBlockId, markingBlockId, overrideExisting));
    }

    /**
     * 通用的按层刷新逻辑：先处理非隧道层，再处理隧道层（覆盖）
     */
    private static void flushSolidsByLayer(
            RoadGenerationResult result,
            RoadSolidModel solids,
            CoordinateTransformer transformer,
            BlockProjectionHandler projectionHandler,
            SolidWriter writer) {
        // 第一遍：非隧道层
        for (RoadSolidPrimitive primitive : solids.primitives()) {
            if (primitive.layer() == RoadSolidLayer.TUNNEL) {
                continue;
            }
            writer.write(result, primitive, transformer, projectionHandler, false);
        }

        // 第二遍：隧道层（覆盖已有方块）
        for (RoadSolidPrimitive primitive : solids.primitives()) {
            if (primitive.layer() != RoadSolidLayer.TUNNEL) {
                continue;
            }
            writer.write(result, primitive, transformer, projectionHandler, true);
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
            CoordinateTransformer transformer,
            BlockProjectionHandler projectionHandler,
            boolean overrideExisting);
    }

    private static void writePlacementRecord(
            RoadGenerationResult result,
            RoadSolidPrimitive primitive,
            CoordinateTransformer transformer,
            BlockProjectionHandler projectionHandler,
            boolean overrideExisting) {
        BlockPos pos = toBlockPos(primitive.planPoint(), primitive.elevation(), transformer);
        appendToResultBucket(result, primitive.layer(), pos);
        if (primitive.materialId() == null) {
            return;
        }
        if (overrideExisting) {
            result.recordPlacementOverride(
                pos,
                projectionHandler.getBlockIdAt(pos),
                primitive.materialId());
        } else {
            result.recordPlacementIfAbsent(
                pos,
                projectionHandler.getBlockIdAt(pos),
                primitive.materialId());
        }
    }

    private static void writeJunctionPlacementRecord(
            RoadGenerationResult result,
            RoadSolidPrimitive primitive,
            CoordinateTransformer transformer,
            BlockProjectionHandler projectionHandler,
            String roadBlockId,
            String sidewalkBlockId,
            String markingBlockId,
            boolean overrideExisting) {
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
        if (overrideExisting) {
            result.recordPlacementOverride(
                pos,
                projectionHandler.getBlockIdAt(pos),
                blockId);
        } else {
            result.recordPlacementIfAbsent(
                pos,
                projectionHandler.getBlockIdAt(pos),
                blockId);
        }
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
            case SIDEWALK, BIKE_LANE, SHOULDER, DRAIN, SUBGRADE -> result.sidewalkBlocks.add(pos);
            case BRIDGE -> result.bridgeBlocks.add(pos);
            case TUNNEL -> result.tunnelBlocks.add(pos);
            case STREETLIGHT -> result.streetlightBlocks.add(pos);
        }
    }
}
