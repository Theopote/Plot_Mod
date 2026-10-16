package com.plot.plugin.road.pipeline.corridor;

import com.plot.plugin.road.solid.RoadSolidLayer;
import net.minecraft.util.math.BlockPos;

/**
 * Expected final world surface block for post-merge junction validation.
 */
public record JunctionSurfaceExpectation(BlockPos position, RoadSolidLayer expectedLayer) {
}
