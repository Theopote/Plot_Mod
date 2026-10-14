package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.core.terrain.TerrainSampler;

import java.util.List;

/**
 * Clears terrain and decorations above a junction road surface inside a polygon.
 */
public final class JunctionTerrainClearance {
    private JunctionTerrainClearance() {
    }

    public static void clearOverheadInPolygon(
            RoadSolidModel solids,
            List<Vec2d> polygon,
            int junctionY,
            int tunnelThreshold,
            TerrainSampler terrain,
            int worldX,
            int worldZ,
            Vec2d planPoint) {
        if (solids == null || polygon == null || polygon.size() < 3 || planPoint == null || terrain == null) {
            return;
        }
        if (!RoadGeometryUtils.pointInPolygon(planPoint, polygon)) {
            return;
        }
        if (!terrain.isChunkLoaded(worldX, worldZ)) {
            return;
        }
        int surfaceY = terrain.sampleSurfaceY(planPoint);
        RoadTerrainClearanceUtils.OverheadMode mode = RoadTerrainClearanceUtils.classify(
            junctionY, surfaceY, worldX, worldZ, tunnelThreshold, terrain);
        RoadTerrainClearanceUtils.applyClearance(
            solids, planPoint, junctionY, surfaceY, mode, tunnelThreshold, worldX, worldZ, terrain);
    }
}
