package com.plot.plugin.earthwork;

import com.plot.api.world.ICoordinateService;
import com.plot.plugin.earthwork.model.BakedElevationGrid;
import com.plot.plugin.earthwork.model.DesignSurface;
import com.plot.plugin.earthwork.model.DesignSurfaceKind;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingZone;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 将分区设计面解析为格点目标高程求值器。
 */
public final class DesignSurfaceResolver {

    @FunctionalInterface
    public interface ZoneTargetEvaluator {
        int evaluateAt(DesignTerrainCell cell);
    }

    private DesignSurfaceResolver() {
    }

    public static Map<String, ZoneTargetEvaluator> resolveZoneEvaluators(
            EarthworkSite site,
            TerrainSnapshot terrain,
            BuildingFootprintLookup buildingLookup,
            ICoordinateService transformer) {
        return resolveZoneEvaluators(site, terrain, buildingLookup, RoadSurfaceLookup.NONE, transformer);
    }

    public static Map<String, ZoneTargetEvaluator> resolveZoneEvaluators(
            EarthworkSite site,
            TerrainSnapshot terrain,
            BuildingFootprintLookup buildingLookup,
            RoadSurfaceLookup roadLookup,
            ICoordinateService transformer) {
        Map<String, ZoneTargetEvaluator> evaluators = new HashMap<>();
        if (site == null || terrain == null) {
            return evaluators;
        }
        int siteDefaultElevation = resolveSiteDefaultElevation(terrain);
        boolean deferBalanceToSite = site.getCompositionPolicy().isSiteWideBalance()
            && site.getZoneCount() >= 2;
        for (GradingZone zone : site.getGradingZones().values()) {
            if (zone == null || !zone.isEnabled() || !zone.getType().isSupportedInComposer()) {
                continue;
            }
            ZoneTargetEvaluator evaluator = resolveZoneEvaluator(
                zone,
                terrain,
                buildingLookup,
                roadLookup,
                transformer,
                siteDefaultElevation,
                deferBalanceToSite);
            if (evaluator != null) {
                evaluators.put(zone.getId(), evaluator);
            }
        }
        return evaluators;
    }

    private static ZoneTargetEvaluator resolveZoneEvaluator(
            GradingZone zone,
            TerrainSnapshot terrain,
            BuildingFootprintLookup buildingLookup,
            RoadSurfaceLookup roadLookup,
            ICoordinateService transformer,
            int siteDefaultElevation,
            boolean deferBalanceToSite) {
        DesignSurface surface = zone.getDesignSurface();
        DesignSurfaceKind kind = BuildingFootprintResolver.effectiveKind(zone);
        if (surface.hasBakedElevation()) {
            BakedElevationGrid bakedGrid = surface.getBakedElevationGrid();
            return cell -> bakedGrid.evaluateAt(cell.worldX(), cell.worldZ(), siteDefaultElevation);
        }
        return switch (kind) {
            case CONSTANT_ELEVATION -> {
                int elevation = BuildingFootprintResolver.resolveConstantElevation(
                    zone,
                    surface,
                    terrain,
                    buildingLookup,
                    siteDefaultElevation);
                yield cell -> elevation;
            }
            case MATCH_EXISTING -> {
                int offset = surface.getVerticalOffset();
                yield cell -> cell.existingGroundY() + offset;
            }
            case MULTI_PLANE -> {
                DesignSurfaceResolver.ZoneTargetEvaluator multi =
                    MultiPlaneSurfaceEvaluator.createEvaluator(zone, surface, terrain, transformer);
                if (multi != null) {
                    yield multi;
                }
                yield cell -> cell.existingGroundY();
            }
            case DRAINAGE_SURFACE -> {
                ZoneSamples samples = collectZoneSamples(terrain, zone.getOuterPoints());
                GradingRegion drainageRegion = zone.getRegion();
                drainageRegion.setSurfaceMode(DesignSurfaceKind.BEST_FIT_PLANE.toSurfaceMode());
                drainageRegion.setFitSlopeBalanceCutFill(surface.isFitSlopeBalanceCutFill());
                GradingPlane plane = GradingSurfaceResolver.resolve(
                    drainageRegion,
                    samples.centers(),
                    samples.heights(),
                    transformer,
                    deferBalanceToSite).plane();
                yield cell -> plane.evaluateAt(cell.worldX(), cell.worldZ());
            }
            case EXCAVATION_PIT -> {
                int bottom = surface.getBottomElevation() != null
                    ? surface.getBottomElevation()
                    : siteDefaultElevation;
                int workingMargin = surface.getWorkingMarginBlocks();
                int slopePitch = surface.getSlopePitchRatio();
                List<com.plot.api.geometry.Vec2d> polygon = zone.getOuterPoints();
                yield cell -> ExcavationPitSurfaceEvaluator.evaluateTargetY(
                    cell.center(),
                    polygon,
                    bottom,
                    workingMargin,
                    slopePitch);
            }
            case ROAD_CORRIDOR -> {
                yield cell -> RoadCorridorSurfaceResolver.evaluateTargetY(
                    cell.center(),
                    zone,
                    surface,
                    roadLookup,
                    siteDefaultElevation);
            }
            default -> {
                ZoneSamples samples = collectZoneSamples(terrain, zone.getOuterPoints());
                GradingSurfaceResolver.ResolvedSurface resolved = GradingSurfaceResolver.resolve(
                    zone.getRegion(),
                    samples.centers(),
                    samples.heights(),
                    transformer,
                    deferBalanceToSite);
                GradingPlane plane = resolved.plane();
                yield cell -> plane.evaluateAt(cell.worldX(), cell.worldZ());
            }
        };
    }

    private static int resolveSiteDefaultElevation(TerrainSnapshot terrain) {
        if (terrain == null || terrain.isEmpty()) {
            return 64;
        }
        long sum = 0L;
        int count = 0;
        for (TerrainSnapshot.Column column : terrain.columns()) {
            sum += column.groundY();
            count++;
        }
        return count > 0 ? (int) Math.round(sum / (double) count) : 64;
    }

    private static ZoneSamples collectZoneSamples(TerrainSnapshot terrain, List<com.plot.api.geometry.Vec2d> zonePolygon) {
        List<com.plot.api.geometry.Vec2d> centers = new java.util.ArrayList<>();
        List<Integer> heights = new java.util.ArrayList<>();
        for (TerrainSnapshot.Column column : terrain.columns()) {
            if (!EarthworkGeometryUtils.containsCanvasPoint(zonePolygon, column.center())) {
                continue;
            }
            centers.add(column.center());
            heights.add(column.groundY());
        }
        return new ZoneSamples(centers, heights);
    }

    private record ZoneSamples(List<com.plot.api.geometry.Vec2d> centers, List<Integer> heights) {
    }
}
