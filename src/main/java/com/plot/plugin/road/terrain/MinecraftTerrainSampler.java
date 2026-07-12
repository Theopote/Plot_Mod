package com.plot.plugin.road.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import com.plot.plugin.road.RoadGeometryUtils;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Minecraft {@link World} 与 {@link Heightmap} 的地形采样实现。
 */
public final class MinecraftTerrainSampler implements TerrainSampler {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/MinecraftTerrainSampler");

    private final World world;
    private final CoordinateTransformer transformer;

    public MinecraftTerrainSampler(World world, CoordinateTransformer transformer) {
        this.world = world;
        this.transformer = transformer;
    }

    public static TerrainSampler of(World world, CoordinateTransformer transformer) {
        return new MinecraftTerrainSampler(world, transformer);
    }

    @Override
    public int sampleSurfaceY(Vec2d planPoint) {
        if (world == null || planPoint == null) {
            return DEFAULT_SEA_LEVEL;
        }
        BlockPos column = RoadGeometryUtils.canvasToBlockXZ(planPoint, transformer);
        try {
            BlockPos topPos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, column);
            return topPos != null ? topPos.getY() : DEFAULT_SEA_LEVEL;
        } catch (Exception e) {
            LOGGER.warn("获取地形高度失败 ({}, {}): {}", column.getX(), column.getZ(), e.getMessage());
            return DEFAULT_SEA_LEVEL;
        }
    }

    @Override
    public boolean isSolidBlock(int worldX, int y, int worldZ) {
        if (world == null) {
            return false;
        }
        try {
            var blockState = world.getBlockState(new BlockPos(worldX, y, worldZ));
            return blockState != null && !blockState.isAir();
        } catch (Exception e) {
            return false;
        }
    }
}
