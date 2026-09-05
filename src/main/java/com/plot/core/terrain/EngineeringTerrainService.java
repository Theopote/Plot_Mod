package com.plot.core.terrain;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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
        return findWaterSurface(worldX, worldZ)
            .orElse(DEFAULT_GROUND_ELEVATION);
    }

    /**
     * 列顶向下第一个流体方块 Y；无流体时为空（比 {@link #sampleWaterSurface} 更安全，避免把默认 64 当成水面）。
     * <p>
     * 注意：会扫到世界底部，可能命中地下洞穴水。Building 场地分析请用
     * {@link #findExposedWaterSurface(int, int)}。
     */
    public java.util.OptionalInt findWaterSurface(int worldX, int worldZ) {
        if (world == null || !isChunkLoaded(worldX, worldZ)) {
            return java.util.OptionalInt.empty();
        }
        try {
            int topY = sampleRawSurface(worldX, worldZ);
            int bottomY = world.getBottomY();
            for (int y = topY; y >= bottomY; y--) {
                BlockState state = world.getBlockState(new BlockPos(worldX, y, worldZ));
                if (!state.getFluidState().isEmpty()) {
                    return java.util.OptionalInt.of(y);
                }
            }
            return java.util.OptionalInt.empty();
        } catch (Exception e) {
            LOGGER.warn("采样水面失败 ({}, {}): {}", worldX, worldZ, e.getMessage());
            return java.util.OptionalInt.empty();
        }
    }

    /**
     * 地表暴露水体：仅在 {@code rawSurfaceY} 下扫到 {@code engineeringGroundY}（含）的区间内找流体。
     * 忽略工程地面以下的地下水 / 洞穴水。
     */
    public java.util.OptionalInt findExposedWaterSurface(int worldX, int worldZ) {
        if (world == null || !isChunkLoaded(worldX, worldZ)) {
            return java.util.OptionalInt.empty();
        }
        try {
            int rawY = sampleRawSurface(worldX, worldZ);
            int groundY = sampleGroundSurface(worldX, worldZ);
            return findExposedWaterInRange(rawY, groundY, y -> {
                BlockState state = world.getBlockState(new BlockPos(worldX, y, worldZ));
                return !state.getFluidState().isEmpty();
            });
        } catch (Exception e) {
            LOGGER.warn("采样地表水面失败 ({}, {}): {}", worldX, worldZ, e.getMessage());
            return java.util.OptionalInt.empty();
        }
    }

    /**
     * 纯函数：从 rawSurface 向下扫到 groundY，返回第一个流体 Y。
     * 供测试与 {@link #findExposedWaterSurface} 共用。
     */
    public static java.util.OptionalInt findExposedWaterInRange(
            int rawSurfaceY,
            int engineeringGroundY,
            java.util.function.IntPredicate isFluidAtY) {
        if (isFluidAtY == null) {
            return java.util.OptionalInt.empty();
        }
        int top = Math.max(rawSurfaceY, engineeringGroundY);
        int bottom = Math.min(rawSurfaceY, engineeringGroundY);
        for (int y = top; y >= bottom; y--) {
            if (isFluidAtY.test(y)) {
                return java.util.OptionalInt.of(y);
            }
        }
        return java.util.OptionalInt.empty();
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
        if (state == null || state.isAir() || !state.getFluidState().isEmpty() || isNaturalDecoration(state)) {
            return false;
        }
        return isNaturalGroundMaterial(state);
    }

    /**
     * 自然/工程地表材料（土石沙泥等）。人工构筑（砖、木板、玻璃等）返回 false。
     */
    public static boolean isNaturalGroundMaterial(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        return state.isIn(BlockTags.DIRT)
            || state.isIn(BlockTags.BASE_STONE_OVERWORLD)
            || state.isIn(BlockTags.BASE_STONE_NETHER)
            || state.isIn(BlockTags.SAND)
            || state.isIn(BlockTags.TERRACOTTA)
            || state.isIn(BlockTags.NYLIUM)
            || state.isOf(Blocks.GRAVEL)
            || state.isOf(Blocks.CLAY)
            || state.isOf(Blocks.MUD)
            || state.isOf(Blocks.PACKED_MUD)
            || state.isOf(Blocks.SOUL_SAND)
            || state.isOf(Blocks.SOUL_SOIL)
            || state.isOf(Blocks.NETHERRACK)
            || state.isOf(Blocks.END_STONE)
            || state.isOf(Blocks.BLACKSTONE)
            || state.isOf(Blocks.BASALT)
            || state.isOf(Blocks.SMOOTH_BASALT)
            || state.isOf(Blocks.CALCITE)
            || state.isOf(Blocks.TUFF)
            || state.isOf(Blocks.DRIPSTONE_BLOCK)
            || state.isOf(Blocks.MAGMA_BLOCK)
            || state.isOf(Blocks.OBSIDIAN)
            || state.isOf(Blocks.CRYING_OBSIDIAN)
            || state.isOf(Blocks.SNOW_BLOCK)
            || state.isOf(Blocks.POWDER_SNOW)
            || state.isOf(Blocks.ICE)
            || state.isOf(Blocks.PACKED_ICE)
            || state.isOf(Blocks.BLUE_ICE)
            || state.isOf(Blocks.MOSS_BLOCK)
            || state.isOf(Blocks.ROOTED_DIRT)
            || state.isOf(Blocks.MUDDY_MANGROVE_ROOTS);
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
        // 兼容旧测试：非自然附着的实心默认视为工程地表材料
        return isEngineeringTerrainTraits(air, hasFluid, replaceable, inLogsTag, inLeavesTag, inFlowersTag, true);
    }

    static boolean isEngineeringTerrainTraits(
            boolean air,
            boolean hasFluid,
            boolean replaceable,
            boolean inLogsTag,
            boolean inLeavesTag,
            boolean inFlowersTag,
            boolean naturalGroundMaterial) {
        return !air
            && !hasFluid
            && !isNaturalDecorationTraits(replaceable, inLogsTag, inLeavesTag, inFlowersTag)
            && naturalGroundMaterial;
    }

    static EngineeringTerrainBlockRole classifyTraits(
            boolean air,
            boolean hasFluid,
            boolean replaceable,
            boolean inLogsTag,
            boolean inLeavesTag,
            boolean inFlowersTag) {
        return classifyTraits(air, hasFluid, replaceable, inLogsTag, inLeavesTag, inFlowersTag, true);
    }

    static EngineeringTerrainBlockRole classifyTraits(
            boolean air,
            boolean hasFluid,
            boolean replaceable,
            boolean inLogsTag,
            boolean inLeavesTag,
            boolean inFlowersTag,
            boolean naturalGroundMaterial) {
        if (air) {
            return EngineeringTerrainBlockRole.AIR;
        }
        if (hasFluid) {
            return EngineeringTerrainBlockRole.FLUID;
        }
        if (isNaturalDecorationTraits(replaceable, inLogsTag, inLeavesTag, inFlowersTag)) {
            return EngineeringTerrainBlockRole.NATURAL_DECORATION;
        }
        if (isEngineeringTerrainTraits(
                air, hasFluid, replaceable, inLogsTag, inLeavesTag, inFlowersTag, naturalGroundMaterial)) {
            return EngineeringTerrainBlockRole.ENGINEERING_TERRAIN;
        }
        return EngineeringTerrainBlockRole.OTHER_SOLID;
    }

    /**
     * 非自然附着物、且不计入工程自然地形的其它实心方块（如人工构筑）。
     */
    public static boolean isStructure(BlockState state) {
        return classifyBlock(state) == EngineeringTerrainBlockRole.OTHER_SOLID;
    }
}
