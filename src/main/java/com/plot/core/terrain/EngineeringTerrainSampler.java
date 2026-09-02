package com.plot.core.terrain;

import net.minecraft.world.World;

/**
 * Plot 工程插件共享的地形采样门面；委托 {@link EngineeringTerrainService}。
 * <p>
 * 道路、土方、建筑等插件应通过本类（或 {@link EngineeringTerrainService}）采样现状地面，
 * 避免各插件自行定义「什么叫地面」。
 */
public final class EngineeringTerrainSampler {
    private EngineeringTerrainSampler() {
    }

    public static int sampleGroundSurface(World world, int blockX, int blockZ) {
        return EngineeringTerrainService.of(world).sampleGroundSurface(blockX, blockZ);
    }

    public static int sampleRawSurface(World world, int blockX, int blockZ) {
        return EngineeringTerrainService.of(world).sampleRawSurface(blockX, blockZ);
    }

    public static int sampleWaterSurface(World world, int blockX, int blockZ) {
        return EngineeringTerrainService.of(world).sampleWaterSurface(blockX, blockZ);
    }

    public static int sampleSolidSurface(World world, int blockX, int blockZ) {
        return EngineeringTerrainService.of(world).sampleSolidSurface(blockX, blockZ);
    }

    public static boolean isChunkLoaded(World world, int blockX, int blockZ) {
        return EngineeringTerrainService.of(world).isChunkLoaded(blockX, blockZ);
    }

    public static int defaultGroundElevation() {
        return EngineeringTerrainService.DEFAULT_GROUND_ELEVATION;
    }
}
