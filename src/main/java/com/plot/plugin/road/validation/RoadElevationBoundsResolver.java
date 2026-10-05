package com.plot.plugin.road.validation;

import com.plot.api.world.ICoordinateService;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.vertical.RoadElevationBounds;
import com.plot.plugin.road.vertical.RoadWorldElevationBounds;
import net.minecraft.world.World;

/** Resolves {@link RoadElevationBounds} for validation and preflight (client world when available). */
public final class RoadElevationBoundsResolver {

    private RoadElevationBoundsResolver() {
    }

    public static RoadElevationBounds resolve(TerrainSampler terrain) {
        return RoadWorldElevationBounds.resolve(terrain);
    }

    public static RoadElevationBounds resolveClientWorld(ICoordinateService coordinates) {
        World world = RoadNetworkGenerator.getClientWorld();
        return RoadWorldElevationBounds.resolve(world, coordinates);
    }
}
