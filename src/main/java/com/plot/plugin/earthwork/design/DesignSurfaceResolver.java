package com.plot.plugin.earthwork.design;

import com.plot.plugin.earthwork.geometry.EarthworkGeometryUtils;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.GradingPlane;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.earthwork.model.BakedElevationGrid;
import com.plot.plugin.earthwork.model.DesignSurface;
import com.plot.plugin.earthwork.model.DesignSurfaceElevationSource;
import com.plot.plugin.earthwork.model.DesignSurfaceKind;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.VerticalAdjustmentPolicy;
import com.plot.core.geometry.RegionGeometry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将分区 {@link DesignSurface} 解析为 {@link ResolvedDesignSurface}
 * （求值器 + 来源 / 状态 / 竖向策略）。
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
        return ResolvedDesignSurface.toEvaluatorMap(
            resolveZoneSurfaces(site, terrain, buildingLookup, roadLookup, transformer));
    }

    public static Map<String, ResolvedDesignSurface> resolveZoneSurfaces(
            EarthworkSite site,
            TerrainSnapshot terrain,
            BuildingFootprintLookup buildingLookup,
            ICoordinateService transformer) {
        return resolveZoneSurfaces(site, terrain, buildingLookup, RoadSurfaceLookup.NONE, transformer);
    }

    public static Map<String, ResolvedDesignSurface> resolveZoneSurfaces(
            EarthworkSite site,
            TerrainSnapshot terrain,
            BuildingFootprintLookup buildingLookup,
            RoadSurfaceLookup roadLookup,
            ICoordinateService transformer) {
        Map<String, ResolvedDesignSurface> resolved = new LinkedHashMap<>();
        if (site == null || terrain == null) {
            return resolved;
        }
        int siteDefaultElevation = resolveSiteDefaultElevation(terrain);
        boolean deferBalanceToSite = site.getCompositionPolicy().getBalanceScopeEnum().defersPerZoneBalance()
            && site.getZoneCount() >= 2;
        for (GradingZone zone : site.getGradingZones().values()) {
            if (zone == null || !zone.isEnabled() || !zone.getType().isSupportedInComposer()) {
                continue;
            }
            ResolvedDesignSurface surface = resolveZoneSurface(
                zone,
                terrain,
                buildingLookup,
                roadLookup,
                transformer,
                siteDefaultElevation,
                deferBalanceToSite);
            if (surface != null) {
                resolved.put(zone.getId(), surface);
            }
        }
        return resolved;
    }

    private static ResolvedDesignSurface resolveZoneSurface(
            GradingZone zone,
            TerrainSnapshot terrain,
            BuildingFootprintLookup buildingLookup,
            RoadSurfaceLookup roadLookup,
            ICoordinateService transformer,
            int siteDefaultElevation,
            boolean deferBalanceToSite) {
        DesignSurface surface = zone.getDesignSurface();
        VerticalAdjustmentPolicy policy = zone.getVerticalAdjustmentPolicy();
        DesignSurfaceKind kind = BuildingFootprintResolver.effectiveKind(zone);

        if (surface.hasBakedElevation()) {
            BakedElevationGrid bakedGrid = surface.getBakedElevationGrid();
            ZoneTargetEvaluator evaluator =
                cell -> bakedGrid.evaluateAt(cell.worldX(), cell.worldZ(), siteDefaultElevation);
            return new ResolvedDesignSurface(
                zone.getId(),
                ResolvedDesignSource.BAKED_ELEVATION,
                ResolutionResult.Status.RESOLVED,
                policy,
                evaluator,
                "baked elevation grid");
        }

        return switch (kind) {
            case CONSTANT_ELEVATION -> resolveConstant(zone, surface, terrain, buildingLookup,
                siteDefaultElevation, policy);
            case MATCH_EXISTING -> {
                int offset = surface.getVerticalOffset();
                yield ResolvedDesignSurface.of(
                    zone.getId(),
                    ResolvedDesignSource.MATCH_EXISTING,
                    ResolutionResult.Status.RESOLVED,
                    policy,
                    cell -> cell.existingGroundY() + offset);
            }
            case MULTI_PLANE -> {
                ZoneTargetEvaluator multi =
                    MultiPlaneSurfaceEvaluator.createEvaluator(zone, surface, terrain, transformer);
                ZoneTargetEvaluator evaluator = multi != null
                    ? multi
                    : (ZoneTargetEvaluator) DesignTerrainCell::existingGroundY;
                yield new ResolvedDesignSurface(
                    zone.getId(),
                    ResolvedDesignSource.MULTI_PLANE,
                    multi != null ? ResolutionResult.Status.RESOLVED : ResolutionResult.Status.FALLBACK,
                    policy,
                    evaluator,
                    multi != null ? "multi-plane facets" : "multi-plane fallback to existing");
            }
            case DRAINAGE_SURFACE -> {
                ZoneSamples samples = collectZoneSamples(terrain, zone.getGeometry());
                GradingRegion drainageRegion = zone.getRegion();
                drainageRegion.setSurfaceMode(DesignSurfaceKind.BEST_FIT_PLANE.toSurfaceMode());
                drainageRegion.setFitSlopeBalanceCutFill(surface.isFitSlopeBalanceCutFill());
                GradingPlane plane = GradingSurfaceResolver.resolve(
                    drainageRegion,
                    samples.centers(),
                    samples.heights(),
                    transformer,
                    deferBalanceToSite).plane();
                yield ResolvedDesignSurface.of(
                    zone.getId(),
                    ResolvedDesignSource.DRAINAGE,
                    ResolutionResult.Status.RESOLVED,
                    policy,
                    cell -> plane.evaluateAt(cell.worldX(), cell.worldZ()));
            }
            case EXCAVATION_PIT -> resolveExcavationPit(
                zone, surface, terrain, buildingLookup, siteDefaultElevation, policy);
            case ROAD_CORRIDOR -> ResolvedDesignSurface.of(
                zone.getId(),
                ResolvedDesignSource.ROAD_CORRIDOR,
                ResolutionResult.Status.RESOLVED,
                policy,
                cell -> RoadCorridorSurfaceResolver.evaluateTargetY(
                    cell.center(),
                    zone,
                    surface,
                    roadLookup,
                    siteDefaultElevation));
            case LEVEL_PAD, SINGLE_SLOPE_PLANE, THREE_POINT_PLANE, BEST_FIT_PLANE ->
                resolvePlaneBased(zone, kind, terrain, transformer, deferBalanceToSite, policy);
            default -> resolvePlaneBased(
                zone, DesignSurfaceKind.BEST_FIT_PLANE, terrain, transformer, deferBalanceToSite, policy);
        };
    }

    private static ResolvedDesignSurface resolveConstant(
            GradingZone zone,
            DesignSurface surface,
            TerrainSnapshot terrain,
            BuildingFootprintLookup buildingLookup,
            int siteDefaultElevation,
            VerticalAdjustmentPolicy policy) {
        ResolutionResult<Integer> elevation = BuildingFootprintResolver.resolveConstantElevation(
            zone, surface, terrain, buildingLookup, siteDefaultElevation);
        int y = elevation.valueOrFallback(siteDefaultElevation);
        ResolvedDesignSource source =
            surface.getElevationSource() == DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION
                ? ResolvedDesignSource.BUILDING_BASE_ELEVATION
                : ResolvedDesignSource.MANUAL_CONSTANT;
        return new ResolvedDesignSurface(
            zone.getId(),
            source,
            elevation.status(),
            policy,
            cell -> y,
            elevation.detail());
    }

    private static ResolvedDesignSurface resolveExcavationPit(
            GradingZone zone,
            DesignSurface surface,
            TerrainSnapshot terrain,
            BuildingFootprintLookup buildingLookup,
            int siteDefaultElevation,
            VerticalAdjustmentPolicy policy) {
        ResolutionResult<Integer> bottomResult = BuildingFootprintResolver.resolvePitBottomElevation(
            zone, surface, terrain, buildingLookup, siteDefaultElevation);
        boolean buildingLinked =
            surface.getElevationSource() == DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION;
        int bottom = buildingLinked
            ? bottomResult.requireResolved("building-linked pit bottom")
            : bottomResult.valueOrFallback(siteDefaultElevation);
        int workingMargin = surface.getWorkingMarginBlocks();
        int slopePitch = surface.getSlopePitchRatio();
        List<com.plot.api.geometry.Vec2d> polygon = zone.getOuterPoints();
        ZoneTargetEvaluator evaluator = cell -> ExcavationPitSurfaceEvaluator.evaluateTargetY(
            cell.center(),
            polygon,
            bottom,
            workingMargin,
            slopePitch);
        return new ResolvedDesignSurface(
            zone.getId(),
            buildingLinked ? ResolvedDesignSource.DERIVED_BUILDING_PIT : ResolvedDesignSource.MANUAL_PIT_BOTTOM,
            buildingLinked ? ResolutionResult.Status.RESOLVED : bottomResult.status(),
            policy,
            evaluator,
            bottomResult.detail());
    }

    private static ResolvedDesignSurface resolvePlaneBased(
            GradingZone zone,
            DesignSurfaceKind kind,
            TerrainSnapshot terrain,
            ICoordinateService transformer,
            boolean deferBalanceToSite,
            VerticalAdjustmentPolicy policy) {
        ZoneSamples samples = collectZoneSamples(terrain, zone.getGeometry());
        GradingSurfaceResolver.ResolvedSurface resolved = GradingSurfaceResolver.resolve(
            zone.getRegion(),
            samples.centers(),
            samples.heights(),
            transformer,
            deferBalanceToSite);
        GradingPlane plane = resolved.plane();
        ResolvedDesignSource source = switch (kind) {
            case LEVEL_PAD -> ResolvedDesignSource.LEVEL_PAD;
            case SINGLE_SLOPE_PLANE -> ResolvedDesignSource.SINGLE_SLOPE;
            case THREE_POINT_PLANE -> ResolvedDesignSource.THREE_POINT;
            case BEST_FIT_PLANE -> ResolvedDesignSource.BEST_FIT;
            default -> ResolvedDesignSource.BEST_FIT;
        };
        return ResolvedDesignSurface.of(
            zone.getId(),
            source,
            ResolutionResult.Status.RESOLVED,
            policy,
            cell -> plane.evaluateAt(cell.worldX(), cell.worldZ()));
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

    private static ZoneSamples collectZoneSamples(TerrainSnapshot terrain, RegionGeometry zoneGeometry) {
        List<com.plot.api.geometry.Vec2d> centers = new java.util.ArrayList<>();
        List<Integer> heights = new java.util.ArrayList<>();
        for (TerrainSnapshot.Column column : terrain.columns()) {
            if (!EarthworkGeometryUtils.containsCanvasPoint(zoneGeometry, column.center())) {
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
