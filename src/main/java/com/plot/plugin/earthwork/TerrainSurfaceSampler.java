package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 地形实面标高采样：跳过空气、流体与可替换植物方块。
 */
public final class TerrainSurfaceSampler {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/TerrainSurfaceSampler");
    private static final int DEFAULT_ELEVATION = 64;

    private TerrainSurfaceSampler() {
    }

    public static int sampleAtBlock(World world, int blockX, int blockZ) {
        if (world == null) {
            return DEFAULT_ELEVATION;
        }
        try {
            int topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, blockX, blockZ);
            int bottomY = world.getBottomY();
            for (int y = topY; y >= bottomY; y--) {
                BlockState state = world.getBlockState(new BlockPos(blockX, y, blockZ));
                if (countsAsTerrainSurface(state)) {
                    return y;
                }
            }
            return bottomY;
        } catch (Exception e) {
            LOGGER.warn("采样地形标高失败 ({}, {}): {}", blockX, blockZ, e.getMessage());
            return DEFAULT_ELEVATION;
        }
    }

    public static int sampleAtCanvas(World world, Vec2d canvasPos, CoordinateTransformer transformer) {
        if (world == null || canvasPos == null || transformer == null) {
            return DEFAULT_ELEVATION;
        }
        BlockPos column = EarthworkGeometryUtils.canvasToBlockXZ(canvasPos, transformer);
        return sampleAtBlock(world, column.getX(), column.getZ());
    }

    static boolean countsAsTerrainSurface(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        return !state.isReplaceable();
    }
}
