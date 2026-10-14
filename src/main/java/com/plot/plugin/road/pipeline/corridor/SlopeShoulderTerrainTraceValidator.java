package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.geometry.RoadCorridorWidth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Traces outward from outer shoulder surface columns along the local shoulder normal
 * (away from the road surface centroid) toward natural terrain.
 */
public final class SlopeShoulderTerrainTraceValidator {

    private static final double DIRECTION_EPSILON = 1e-6;
    private static final double LOCAL_CENTER_RADIUS = 4.0;

    private SlopeShoulderTerrainTraceValidator() {
    }

    public static int countShoulderToTerrainGaps(RoadCorridorPlan plan, TerrainSampler terrain) {
        if (plan == null || terrain == null || plan.slopeEntries().isEmpty()) {
            return 0;
        }
        List<SurfaceSample> surfaceSamples = collectSurfaceSamples(plan);
        if (surfaceSamples.isEmpty()) {
            return 0;
        }
        int gaps = 0;
        Set<String> tracedRays = new HashSet<>();
        for (SurfaceSample outer : outerShoulderSamples(plan, surfaceSamples)) {
            if (isStructureInteriorColumn(plan, outer.columnKey())) {
                continue;
            }
            Vec2d localCenter = localSurfaceCenter(surfaceSamples, outer.planPoint());
            Vec2d outward = outer.planPoint().subtract(localCenter);
            if (outward.lengthSquared() < DIRECTION_EPSILON) {
                continue;
            }
            Vec2d direction = outward.normalize();
            String rayKey = rayKey(outer.planPoint(), direction);
            if (!tracedRays.add(rayKey)) {
                continue;
            }
            gaps += traceRay(plan, terrain, outer.planPoint(), direction, outer.roadY());
        }
        return gaps;
    }

    private static List<SurfaceSample> collectSurfaceSamples(RoadCorridorPlan plan) {
        List<SurfaceSample> samples = new ArrayList<>();
        for (var entry : plan.surfaceMask().entries()) {
            SurfaceCell cell = entry.getValue();
            if (!plan.surfaceMask().isRoadSurfaceLayer(cell.layer())) {
                continue;
            }
            samples.add(new SurfaceSample(cell.planPoint(), cell.surfaceY(), entry.getKey()));
        }
        return samples;
    }

    private static List<SurfaceSample> outerShoulderSamples(
            RoadCorridorPlan plan,
            List<SurfaceSample> surfaceSamples) {
        List<SurfaceSample> outer = new ArrayList<>();
        for (SurfaceSample sample : surfaceSamples) {
            if (isExposedShoulderEdge(plan, sample.columnKey())) {
                outer.add(sample);
            }
        }
        return outer;
    }

    private static boolean isStructureInteriorColumn(RoadCorridorPlan plan, ColumnKey key) {
        ClearanceCell clearance = plan.clearanceCell(key);
        if (clearance == null) {
            return false;
        }
        RoadConstructionType type = clearance.constructionType();
        return type == RoadConstructionType.BRIDGE || type == RoadConstructionType.TUNNEL;
    }

    private static boolean isExposedShoulderEdge(RoadCorridorPlan plan, ColumnKey key) {
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : offsets) {
            ColumnKey neighbor = new ColumnKey(key.worldX() + offset[0], key.worldZ() + offset[1]);
            SurfaceCell cell = plan.surfaceMask().get(neighbor);
            if (cell == null || !plan.surfaceMask().isRoadSurfaceLayer(cell.layer())) {
                return true;
            }
        }
        return false;
    }

    private static Vec2d localSurfaceCenter(List<SurfaceSample> samples, Vec2d point) {
        double sumX = 0;
        double sumY = 0;
        int count = 0;
        double radiusSq = LOCAL_CENTER_RADIUS * LOCAL_CENTER_RADIUS;
        for (SurfaceSample sample : samples) {
            if (sample.planPoint().distanceSquared(point) <= radiusSq + DIRECTION_EPSILON) {
                sumX += sample.planPoint().x;
                sumY += sample.planPoint().y;
                count++;
            }
        }
        if (count == 0) {
            return point;
        }
        return new Vec2d(sumX / count, sumY / count);
    }

    private static String rayKey(Vec2d origin, Vec2d direction) {
        int octant = (int) Math.round(Math.atan2(direction.y, direction.x) * 4.0 / Math.PI);
        int station = (int) Math.floor(origin.x + origin.y * 0.01);
        return station + ":" + octant;
    }

    private static int traceRay(
            RoadCorridorPlan plan,
            TerrainSampler terrain,
            Vec2d shoulderPoint,
            Vec2d direction,
            int roadY) {
        int maxRun = RoadCorridorWidth.MAX_SLOPE_BATTER_RUN_BLOCKS;
        double x = shoulderPoint.x;
        double y = shoulderPoint.y;
        for (int step = 0; step < maxRun; step++) {
            x += direction.x;
            y += direction.y;
            Vec2d planPoint = new Vec2d(x, y);
            int worldX = (int) Math.round(x);
            int worldZ = (int) Math.round(y);
            if (!terrain.isChunkLoaded(planPoint) && !terrain.isChunkLoaded(worldX, worldZ)) {
                return 0;
            }
            int groundY = terrain.sampleSurfaceY(planPoint);
            if (groundY <= roadY + 1) {
                return 0;
            }
            if (plan.slopeCell(new ColumnKey(worldX, worldZ)) == null) {
                if (!hasSlopeFurtherOut(plan, x, y, direction)) {
                    return 0;
                }
                return 1;
            }
        }
        return 0;
    }

    private static boolean hasSlopeFurtherOut(
            RoadCorridorPlan plan,
            double x,
            double y,
            Vec2d direction) {
        for (int i = 0; i < 8; i++) {
            x += direction.x;
            y += direction.y;
            int worldX = (int) Math.round(x);
            int worldZ = (int) Math.round(y);
            if (plan.slopeCell(new ColumnKey(worldX, worldZ)) != null) {
                return true;
            }
        }
        return false;
    }

    private record SurfaceSample(Vec2d planPoint, int roadY, ColumnKey columnKey) {
    }
}
