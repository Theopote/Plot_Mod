package com.plot.plugin.road.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.terrain.EngineeringTerrainSampler;
import com.plot.core.terrain.EngineeringTerrainService;
import com.plot.plugin.road.RoadGeometryUtils;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 基于 Minecraft {@link World} 的地形采样；委托 {@link EngineeringTerrainSampler}。
 */
public final class MinecraftTerrainSampler implements TerrainSampler {

    private final World world;
    private final EngineeringTerrainService terrainService;
    private final ICoordinateService transformer;

    public MinecraftTerrainSampler(World world, ICoordinateService transformer) {
        this.world = world;
        this.terrainService = EngineeringTerrainService.of(world);
        this.transformer = transformer;
    }

    public static TerrainSampler of(World world, ICoordinateService transformer) {
        return new MinecraftTerrainSampler(world, transformer);
    }

    @Override
    public int sampleSurfaceY(Vec2d planPoint) {
        if (planPoint == null) {
            return DEFAULT_SEA_LEVEL;
        }
        BlockPos column = RoadGeometryUtils.canvasToBlockXZ(planPoint, transformer);
        return EngineeringTerrainSampler.sampleGroundSurface(world, column.getX(), column.getZ());
    }

    @Override
    public boolean isSolidBlock(int worldX, int y, int worldZ) {
        return terrainService.isSolidEngineeringBlock(worldX, y, worldZ);
    }

    @Override
    public int sampleColumnTopY(Vec2d planPoint) {
        if (planPoint == null) {
            return DEFAULT_SEA_LEVEL;
        }
        BlockPos column = RoadGeometryUtils.canvasToBlockXZ(planPoint, transformer);
        return EngineeringTerrainSampler.sampleRawSurface(world, column.getX(), column.getZ());
    }

    @Override
    public boolean isRoadClearableDecoration(int worldX, int y, int worldZ) {
        return terrainService.isClearableNaturalDecoration(worldX, y, worldZ);
    }

    /** @deprecated 请改用 {@link EngineeringTerrainService#isEngineeringTerrain} */
    @Deprecated
    static boolean countsAsEngineeringTerrain(net.minecraft.block.BlockState state) {
        return EngineeringTerrainService.isEngineeringTerrain(state);
    }

    /** @deprecated 请改用 {@link EngineeringTerrainService#isNaturalDecoration} */
    @Deprecated
    static boolean isNaturalDecoration(net.minecraft.block.BlockState state) {
        return EngineeringTerrainService.isNaturalDecoration(state);
    }
}
