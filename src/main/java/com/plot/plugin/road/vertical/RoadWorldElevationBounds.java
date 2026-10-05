package com.plot.plugin.road.vertical;

import com.plot.api.world.ICoordinateService;
import com.plot.core.terrain.MinecraftTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.world.World;

/**
 * Resolves {@link RoadElevationBounds} from the active Minecraft world or terrain sampler.
 */
public final class RoadWorldElevationBounds {

    private RoadWorldElevationBounds() {
    }

    public static RoadElevationBounds fallback() {
        return fromInclusiveRange(
            TerrainSampler.DEFAULT_WORLD_BOTTOM_Y,
            TerrainSampler.DEFAULT_WORLD_TOP_EXCLUSIVE_Y - 1);
    }

    public static RoadElevationBounds resolve(TerrainSampler terrain) {
        if (terrain == null) {
            return fallback();
        }
        return fromInclusiveRange(terrain.worldBottomY(), terrain.worldTopExclusiveY() - 1);
    }

    public static RoadElevationBounds resolve(World world, ICoordinateService coordinates) {
        if (world == null || coordinates == null) {
            return fallback();
        }
        return resolve(MinecraftTerrainSampler.of(world, coordinates));
    }

    public static RoadElevationBounds fromInclusiveRange(int minY, int maxY) {
        return new RoadElevationBounds(minY, maxY);
    }
}
