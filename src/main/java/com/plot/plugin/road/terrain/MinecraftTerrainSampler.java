package com.plot.plugin.road.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.road.RoadGeometryUtils;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.block.BlockState;
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
    private final ICoordinateService transformer;

    public MinecraftTerrainSampler(World world, ICoordinateService transformer) {
        this.world = world;
        this.transformer = transformer;
    }

    public static TerrainSampler of(World world, ICoordinateService transformer) {
        return new MinecraftTerrainSampler(world, transformer);
    }

    @Override
    public int sampleSurfaceY(Vec2d planPoint) {
        if (world == null || planPoint == null) {
            return DEFAULT_SEA_LEVEL;
        }
        BlockPos column = RoadGeometryUtils.canvasToBlockXZ(planPoint, transformer);
        try {
            if (!isChunkLoaded(column.getX(), column.getZ())) {
                return DEFAULT_SEA_LEVEL;
            }
            int topY = sampleRawTopY(column.getX(), column.getZ());
            int bottomY = world.getBottomY();
            for (int y = topY; y >= bottomY; y--) {
                BlockState state = world.getBlockState(new BlockPos(column.getX(), y, column.getZ()));
                if (countsAsEngineeringTerrain(state)) {
                    return y;
                }
            }
            return bottomY;
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
        if (!isChunkLoaded(worldX, worldZ)) {
            return false;
        }
        try {
            BlockState blockState = world.getBlockState(new BlockPos(worldX, y, worldZ));
            return countsAsEngineeringTerrain(blockState);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public int sampleColumnTopY(Vec2d planPoint) {
        if (world == null || planPoint == null) return DEFAULT_SEA_LEVEL;
        BlockPos column = RoadGeometryUtils.canvasToBlockXZ(planPoint, transformer);
        return isChunkLoaded(column.getX(), column.getZ())
            ? sampleRawTopY(column.getX(), column.getZ())
            : DEFAULT_SEA_LEVEL;
    }

    @Override
    public boolean isRoadClearableDecoration(int worldX, int y, int worldZ) {
        if (world == null || !isChunkLoaded(worldX, worldZ)) return false;
        try {
            return isNaturalDecoration(world.getBlockState(new BlockPos(worldX, y, worldZ)));
        } catch (Exception ignored) {
            return false;
        }
    }

    private int sampleRawTopY(int worldX, int worldZ) {
        return world.getTopY(Heightmap.Type.WORLD_SURFACE, worldX, worldZ);
    }

    static boolean countsAsEngineeringTerrain(BlockState state) {
        return state != null && !state.isAir()
            && state.getFluidState().isEmpty()
            && !isNaturalDecoration(state);
    }

    static boolean isNaturalDecoration(BlockState state) {
        if (state == null || state.isAir()) return false;
        return state.getFluidState().isEmpty()
            && (state.isReplaceable()
            || state.isIn(BlockTags.LOGS)
            || state.isIn(BlockTags.LEAVES)
            || state.isIn(BlockTags.FLOWERS));
    }

    private boolean isChunkLoaded(int worldX, int worldZ) {
        return world.isChunkLoaded(worldX >> 4, worldZ >> 4);
    }
}
