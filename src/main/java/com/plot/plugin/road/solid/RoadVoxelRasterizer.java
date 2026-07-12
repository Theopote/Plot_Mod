package com.plot.plugin.road.solid;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadGeometryUtils;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
            RoadGenerator.RoadGenerationResult result,
            RoadSolidModel solids,
            CoordinateTransformer transformer) {
        if (result == null || solids == null || solids.isEmpty()) {
            return;
        }
        BlockProjectionHandler projectionHandler = BlockProjectionHandler.getInstance();
        for (RoadSolidPrimitive primitive : solids.primitives()) {
            BlockPos pos = toBlockPos(primitive.planPoint(), primitive.elevation(), transformer);
            switch (primitive.layer()) {
                case ROAD, MEDIAN, MARKING -> result.roadBlocks.add(pos);
                case SIDEWALK, SHOULDER, DRAIN -> result.sidewalkBlocks.add(pos);
                case BRIDGE -> result.bridgeBlocks.add(pos);
                case TUNNEL -> result.tunnelBlocks.add(pos);
                case STREETLIGHT -> result.streetlightBlocks.add(pos);
            }
            if (primitive.materialId() != null) {
                RoadGenerator.recordPlacementIfAbsent(
                    result,
                    pos,
                    projectionHandler.getBlockIdAt(pos),
                    primitive.materialId());
            }
        }
        result.streetlightCount = result.streetlightBlocks.size();
    }
}
