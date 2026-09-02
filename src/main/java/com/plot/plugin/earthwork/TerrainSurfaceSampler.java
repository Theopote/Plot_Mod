package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.terrain.EngineeringTerrainService;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 土方插件地形采样入口；委托 {@link EngineeringTerrainService} 统一语义。
 */
public final class TerrainSurfaceSampler {
    private TerrainSurfaceSampler() {
    }

    public static int sampleAtBlock(World world, int blockX, int blockZ) {
        return EngineeringTerrainService.of(world).sampleGroundSurface(blockX, blockZ);
    }

    public static int sampleAtCanvas(World world, Vec2d canvasPos, ICoordinateService transformer) {
        if (world == null || canvasPos == null || transformer == null) {
            return EngineeringTerrainService.DEFAULT_GROUND_ELEVATION;
        }
        BlockPos column = EarthworkGeometryUtils.canvasToBlockXZ(canvasPos, transformer);
        return sampleAtBlock(world, column.getX(), column.getZ());
    }
}
