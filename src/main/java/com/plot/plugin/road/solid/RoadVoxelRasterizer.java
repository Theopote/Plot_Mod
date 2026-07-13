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
        for (RoadSolidPrimitive primitive : solids.primitives()) {
            if (primitive.layer() == RoadSolidLayer.TUNNEL) {
                continue;
            }
            writePlacementRecord(result, primitive, transformer, projectionHandler, false);
        }
        for (RoadSolidPrimitive primitive : solids.primitives()) {
            if (primitive.layer() != RoadSolidLayer.TUNNEL) {
                continue;
            }
            writePlacementRecord(result, primitive, transformer, projectionHandler, true);
        }
        result.streetlightCount = result.streetlightBlocks.size();
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
        for (RoadSolidPrimitive primitive : solids.primitives()) {
            if (primitive.layer() == RoadSolidLayer.TUNNEL) {
                continue;
            }
            writeJunctionPlacementRecord(
                result, primitive, transformer, projectionHandler,
                roadBlockId, sidewalkBlockId, markingBlockId, false);
        }
        for (RoadSolidPrimitive primitive : solids.primitives()) {
            if (primitive.layer() != RoadSolidLayer.TUNNEL) {
                continue;
            }
            writeJunctionPlacementRecord(
                result, primitive, transformer, projectionHandler,
                roadBlockId, sidewalkBlockId, markingBlockId, true);
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
