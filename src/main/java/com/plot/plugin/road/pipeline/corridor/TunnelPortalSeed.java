package com.plot.plugin.road.pipeline.corridor;

import net.minecraft.util.math.BlockPos;

/**
 * Walkable tunnel air seed at a portal approach frame inside a junction footprint.
 */
public record TunnelPortalSeed(String edgeId, BlockPos walkableAirPosition) {
}
