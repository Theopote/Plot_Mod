package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.geometry.RoadCorridorWidth;

/**
 * Traces outward from outer shoulder surface columns toward natural terrain,
 * checking that slope mask columns exist while cut/fill is still required.
 */
public final class SlopeShoulderTerrainTraceValidator {

    private SlopeShoulderTerrainTraceValidator() {
    }

    public static int countShoulderToTerrainGaps(RoadCorridorPlan plan, TerrainSampler terrain) {
        if (plan == null || terrain == null || plan.slopeEntries().isEmpty()) {
            return 0;
        }
        Bounds bounds = Bounds.from(plan);
        if (bounds == null) {
            return 0;
        }
        if (bounds.spanX() >= bounds.spanZ()) {
            return traceEastWestRoad(plan, terrain, bounds);
        }
        return traceNorthSouthRoad(plan, terrain, bounds);
    }

    private static int traceEastWestRoad(RoadCorridorPlan plan, TerrainSampler terrain, Bounds bounds) {
        int gaps = 0;
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            Integer roadY = roadYOnColumn(plan, x, bounds.minZ(), bounds.maxZ());
            if (roadY == null) {
                continue;
            }
            gaps += traceLine(plan, terrain, x, bounds.minZ(), 0, -1, roadY);
            gaps += traceLine(plan, terrain, x, bounds.maxZ(), 0, 1, roadY);
        }
        return gaps;
    }

    private static int traceNorthSouthRoad(RoadCorridorPlan plan, TerrainSampler terrain, Bounds bounds) {
        int gaps = 0;
        for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
            Integer roadY = roadYOnRow(plan, bounds.minX(), bounds.maxX(), z);
            if (roadY == null) {
                continue;
            }
            gaps += traceLine(plan, terrain, bounds.minX(), z, -1, 0, roadY);
            gaps += traceLine(plan, terrain, bounds.maxX(), z, 1, 0, roadY);
        }
        return gaps;
    }

    private static Integer roadYOnColumn(RoadCorridorPlan plan, int x, int minZ, int maxZ) {
        for (int z = minZ; z <= maxZ; z++) {
            SurfaceCell cell = plan.surfaceMask().get(new ColumnKey(x, z));
            if (cell != null && plan.surfaceMask().isRoadSurfaceLayer(cell.layer())) {
                return cell.surfaceY();
            }
        }
        return null;
    }

    private static Integer roadYOnRow(RoadCorridorPlan plan, int minX, int maxX, int z) {
        for (int x = minX; x <= maxX; x++) {
            SurfaceCell cell = plan.surfaceMask().get(new ColumnKey(x, z));
            if (cell != null && plan.surfaceMask().isRoadSurfaceLayer(cell.layer())) {
                return cell.surfaceY();
            }
        }
        return null;
    }

    private static int traceLine(
            RoadCorridorPlan plan,
            TerrainSampler terrain,
            int startX,
            int startZ,
            int dx,
            int dz,
            int roadY) {
        int maxRun = RoadCorridorWidth.MAX_SLOPE_BATTER_RUN_BLOCKS;
        int x = startX;
        int z = startZ;
        for (int step = 0; step < maxRun; step++) {
            x += dx;
            z += dz;
            Vec2d planPoint = new Vec2d(x, z);
            if (!terrain.isChunkLoaded(planPoint) && !terrain.isChunkLoaded(x, z)) {
                return 0;
            }
            int groundY = terrain.sampleSurfaceY(planPoint);
            if (groundY <= roadY + 1) {
                return 0;
            }
            if (plan.slopeCell(new ColumnKey(x, z)) == null) {
                if (!hasSlopeFurtherOut(plan, x, z, dx, dz)) {
                    return 0;
                }
                return 1;
            }
        }
        return 0;
    }

    private static boolean hasSlopeFurtherOut(
            RoadCorridorPlan plan,
            int x,
            int z,
            int dx,
            int dz) {
        for (int i = 0; i < 8; i++) {
            x += dx;
            z += dz;
            if (plan.slopeCell(new ColumnKey(x, z)) != null) {
                return true;
            }
        }
        return false;
    }

    private record Bounds(int minX, int maxX, int minZ, int maxZ) {
        int spanX() {
            return maxX - minX;
        }

        int spanZ() {
            return maxZ - minZ;
        }

        static Bounds from(RoadCorridorPlan plan) {
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            boolean any = false;
            for (var entry : plan.surfaceMask().entries()) {
                SurfaceCell cell = entry.getValue();
                if (!plan.surfaceMask().isRoadSurfaceLayer(cell.layer())) {
                    continue;
                }
                any = true;
                ColumnKey key = entry.getKey();
                minX = Math.min(minX, key.worldX());
                maxX = Math.max(maxX, key.worldX());
                minZ = Math.min(minZ, key.worldZ());
                maxZ = Math.max(maxZ, key.worldZ());
            }
            return any ? new Bounds(minX, maxX, minZ, maxZ) : null;
        }
    }
}
