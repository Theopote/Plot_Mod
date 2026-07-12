package com.plot.plugin.road.solid;

import com.plot.api.geometry.Vec2d;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
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
}
