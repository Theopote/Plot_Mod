package com.plot.core.terrain;

import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plot 工程插件共享的地形采样与方块分类服务。
 * <p>
 * 统一「现状地面」语义：排除空气、流体与自然附着物（含原木、树叶、花草等），
 * 并执行区块加载检查。
 */
public final class EngineeringTerrainService {
    public static final int DEFAULT_GROUND_ELEVATION = 64;

    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/EngineeringTerrain");

    private final World world;

    public EngineeringTerrainService(World world) {
        this.world = world;
    }

    public static EngineeringTerrainService of(World world) {
        return new EngineeringTerrainService(world);
    }

    /**
     * 工程现状地面：跳过空气、流体与自然附着物后的最高实心地形方块 Y。
     */
    public int sampleGroundSurface(int worldX, int worldZ) {
        if (world == null || !isChunkLoaded(worldX, worldZ)) {
            return DEFAULT_GROUND_ELEVATION;
        }
        try {
            int topY = sampleRawSurface(worldX, worldZ);
            int bottomY = world.getBottomY();
            for (int y = topY; y >= bottomY; y--) {
                BlockState state = world.getBlockState(new BlockPos(worldX, y, worldZ));
                if (isEngineeringTerrain(state)) {
                    return y;
                }
            }
            return bottomY;
        } catch (Exception e) {
            LOGGER.warn("采样工程地面失败 ({}, {}): {}", worldX, worldZ, e.getMessage());
            return DEFAULT_GROUND_ELEVATION;
        }
    }

    /**
     * {@link Heightmap.Type#WORLD_SURFACE} 列顶，不做方块分类过滤。
     */
    public int sampleRawSurface(int worldX, int worldZ) {
        if (world == null || !isChunkLoaded(worldX, worldZ)) {
            return DEFAULT_GROUND_ELEVATION;
        }
        try {
            return world.getTopY(Heightmap.Type.WORLD_SURFACE, worldX, worldZ);
        } catch (Exception e) {
            LOGGER.warn("采样原始地表失败 ({}, {}): {}", worldX, worldZ, e.getMessage());
            return DEFAULT_GROUND_ELEVATION;
        }
    }

    /**
     * 列顶向下第一个流体方块 Y；无流体时返回 {@link #DEFAULT_GROUND_ELEVATION}。
     */
    public int sampleWaterSurface(int worldX, int worldZ) {
        if (world == null || !isChunkLoaded(worldX, worldZ)) {
            return DEFAULT_GROUND_ELEVATION;
        }
        try {
            int topY = sampleRawSurface(worldX, worldZ);
            int bottomY = world.getBottomY();
            for (int y = topY; y >= bottomY; y--) {
                BlockState state = world.getBlockState(new BlockPos(worldX, y, worldZ));
                if (!state.getFluidState().isEmpty()) {
                    return y;
                }
            }
            return DEFAULT_GROUND_ELEVATION;
        } catch (Exception e) {
            LOGGER.warn("采样水面失败 ({}, {}): {}", worldX, worldZ, e.getMessage());
            return DEFAULT_GROUND_ELEVATION;
        }
    }

    /**
     * 列顶向下第一个非空气方块 Y（含流体、植被与构筑物）。
     */
    public int sampleSolidSurface(int worldX, int worldZ) {
        if (world == null || !isChunkLoaded(worldX, worldZ)) {
            return DEFAULT_GROUND_ELEVATION;
        }
        try {
            int topY = sampleRawSurface(worldX, worldZ);
            int bottomY = world.getBottomY();
            for (int y = topY; y >= bottomY; y--) {
                BlockState state = world.getBlockState(new BlockPos(worldX, y, worldZ));
                if (!state.isAir()) {
                    return y;
                }
            }
            return bottomY;
        } catch (Exception e) {
            LOGGER.warn("采样实心顶面失败 ({}, {}): {}", worldX, worldZ, e.getMessage());
            return DEFAULT_GROUND_ELEVATION;
        }
    }

    public boolean isChunkLoaded(int worldX, int worldZ) {
        return world != null && world.isChunkLoaded(worldX >> 4, worldZ >> 4);
    }

    public boolean isSolidEngineeringBlock(int worldX, int y, int worldZ) {
        if (world == null || !isChunkLoaded(worldX, worldZ)) {
            return false;
        }
        try {
            return isEngineeringTerrain(world.getBlockState(new BlockPos(worldX, y, worldZ)));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isClearableNaturalDecoration(int worldX, int y, int worldZ) {
        if (world == null || !isChunkLoaded(worldX, worldZ)) {
            return false;
        }
        try {
            return isNaturalDecoration(world.getBlockState(new BlockPos(worldX, y, worldZ)));
        } catch (Exception e) {
            return false;
        }
    }

    public static EngineeringTerrainBlockRole classifyBlock(BlockState state) {
        if (state == null || state.isAir()) {
            return EngineeringTerrainBlockRole.AIR;
        }
        if (!state.getFluidState().isEmpty()) {
            return EngineeringTerrainBlockRole.FLUID;
        }
        if (isNaturalDecoration(state)) {
            return EngineeringTerrainBlockRole.NATURAL_DECORATION;
        }
        if (isEngineeringTerrain(state)) {
            return EngineeringTerrainBlockRole.ENGINEERING_TERRAIN;
        }
        return EngineeringTerrainBlockRole.OTHER_SOLID;
    }

    public static boolean isNaturalDecoration(BlockState state) {
        if (state == null || state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        return isNaturalDecorationTraits(
            state.isReplaceable(),
            state.isIn(BlockTags.LOGS),
            state.isIn(BlockTags.LEAVES),
            state.isIn(BlockTags.FLOWERS));
    }

    public static boolean isEngineeringTerrain(BlockState state) {
        return state != null
            && !state.isAir()
            && state.getFluidState().isEmpty()
            && !isNaturalDecoration(state);
    }

    static boolean isNaturalDecorationTraits(
            boolean replaceable,
            boolean inLogsTag,
            boolean inLeavesTag,
            boolean inFlowersTag) {
        return replaceable || inLogsTag || inLeavesTag || inFlowersTag;
    }

    static boolean isEngineeringTerrainTraits(
            boolean air,
            boolean hasFluid,
            boolean replaceable,
            boolean inLogsTag,
            boolean inLeavesTag,
            boolean inFlowersTag) {
        return !air
            && !hasFluid
            && !isNaturalDecorationTraits(replaceable, inLogsTag, inLeavesTag, inFlowersTag);
    }

    static EngineeringTerrainBlockRole classifyTraits(
            boolean air,
            boolean hasFluid,
            boolean replaceable,
            boolean inLogsTag,
            boolean inLeavesTag,
            boolean inFlowersTag) {
        if (air) {
            return EngineeringTerrainBlockRole.AIR;
        }
        if (hasFluid) {
            return EngineeringTerrainBlockRole.FLUID;
        }
        if (isNaturalDecorationTraits(replaceable, inLogsTag, inLeavesTag, inFlowersTag)) {
            return EngineeringTerrainBlockRole.NATURAL_DECORATION;
        }
        if (isEngineeringTerrainTraits(air, hasFluid, replaceable, inLogsTag, inLeavesTag, inFlowersTag)) {
            return EngineeringTerrainBlockRole.ENGINEERING_TERRAIN;
        }
        return EngineeringTerrainBlockRole.OTHER_SOLID;
    }

    /**
     * 非自然附着物、且不计入工程自然地形的其它实心方块（如人工构筑）。
     */
    public static boolean isStructure(BlockState state) {
        EngineeringTerrainBlockRole role = classifyBlock(state);
        return role == EngineeringTerrainBlockRole.OTHER_SOLID;
    }
}
