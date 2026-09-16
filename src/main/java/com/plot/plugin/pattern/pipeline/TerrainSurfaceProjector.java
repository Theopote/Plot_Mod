package com.plot.plugin.pattern.pipeline;

import com.plot.api.world.ICoordinateService;
import com.plot.core.terrain.EngineeringTerrainService;
import com.plot.plugin.pattern.PatternGeometryUtils;
import com.plot.plugin.pattern.space.PatternSample;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Objects;

/**
 * 地形投影：Pattern Space 采样点 → 世界地表方块位置。
 */
public final class TerrainSurfaceProjector {
    public record SurfaceProjection(BlockPos pos, boolean usedFallbackElevation) {
    }

    private final ICoordinateService coordinates;
    private final EngineeringTerrainService terrain;

    private TerrainSurfaceProjector(ICoordinateService coordinates, EngineeringTerrainService terrain) {
        this.coordinates = Objects.requireNonNull(coordinates, "coordinates");
        this.terrain = Objects.requireNonNull(terrain, "terrain");
    }

    public static TerrainSurfaceProjector of(World world, ICoordinateService coordinates) {
        return new TerrainSurfaceProjector(coordinates, EngineeringTerrainService.of(world));
    }

    public SurfaceProjection projectSurface(PatternSample sample) {
        BlockPos column = PatternGeometryUtils.canvasToBlockXZ(sample.toCanvas(), coordinates);
        int worldX = column.getX();
        int worldZ = column.getZ();
        boolean chunkLoaded = terrain.isChunkLoaded(worldX, worldZ);
        int surfaceY = terrain.sampleGroundSurface(worldX, worldZ);
        boolean usedFallback = !chunkLoaded;
        return new SurfaceProjection(new BlockPos(worldX, surfaceY, worldZ), usedFallback);
    }
}
