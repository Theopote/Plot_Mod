package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Authoritative post-merge validation expectations captured during junction generation.
 */
public record JunctionValidationManifest(
        String nodeId,
        List<JunctionSurfaceExpectation> surfaceExpectations,
        List<BlockPos> expectedProtectedBlocks,
        List<TunnelPortalSeed> tunnelPortalSeeds,
        List<Vec2d> footprintPolygon,
        int junctionY,
        Integer elevatedDeckY,
        int underpassYMin,
        int underpassYMax) {

    public static JunctionValidationManifest empty(String nodeId) {
        return new JunctionValidationManifest(
            nodeId,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            0,
            null,
            0,
            0);
    }

    public boolean hasTunnelValidation() {
        return !tunnelPortalSeeds.isEmpty() && underpassYMax >= underpassYMin;
    }

    public boolean hasGradeSeparation() {
        return elevatedDeckY != null;
    }
}
