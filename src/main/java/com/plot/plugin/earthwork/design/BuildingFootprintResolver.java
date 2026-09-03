package com.plot.plugin.earthwork.design;
import com.plot.plugin.earthwork.geometry.EarthworkGeometryUtils;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.BuildingFoundationUtils;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.earthwork.model.DesignSurface;
import com.plot.plugin.earthwork.model.DesignSurfaceElevationSource;
import com.plot.plugin.earthwork.model.DesignSurfaceKind;
import com.plot.plugin.earthwork.model.GradingZone;

import java.util.ArrayList;
import java.util.List;

/**
 * 从建筑轮廓解析室外地坪 ±0.000 标高。
 */
public final class BuildingFootprintResolver {
    private BuildingFootprintResolver() {
    }

    public static int resolveConstantElevation(
            GradingZone zone,
            DesignSurface surface,
            TerrainSnapshot terrain,
            BuildingFootprintLookup lookup,
            int siteDefaultElevation) {
        if (zone == null || surface == null) {
            return siteDefaultElevation;
        }
        if (surface.getElevation() != null) {
            return surface.getElevation();
        }
        if (surface.getElevationSource() == DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION) {
            return resolveBuildingBaseElevation(zone, surface, terrain, lookup, siteDefaultElevation);
        }
        if (surface.getManualTargetElevation() != null) {
            return surface.getManualTargetElevation();
        }
        return siteDefaultElevation;
    }

    /**
     * 基坑坑底标高：手动 {@link DesignSurface#getBottomElevation()}，或
     * {@code referenceElevation - basementFloorDepth - foundationDepth - workingAllowance}。
     */
    public static int resolvePitBottomElevation(
            GradingZone zone,
            DesignSurface surface,
            TerrainSnapshot terrain,
            BuildingFootprintLookup lookup,
            int siteDefaultElevation) {
        if (surface == null) {
            return siteDefaultElevation;
        }
        if (surface.getElevationSource() == DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION) {
            if (zone == null) {
                return siteDefaultElevation;
            }
            int referenceElevation = resolveBuildingBaseElevation(
                zone, surface, terrain, lookup, siteDefaultElevation);
            return surface.getExcavationPit().pitBottomFrom(referenceElevation);
        }
        if (surface.getBottomElevation() != null) {
            return surface.getBottomElevation();
        }
        return siteDefaultElevation;
    }

    /** 解析基坑基准标高（建筑 ±0.000 / 基础底），不扣减埋深。 */
    public static int resolvePitReferenceElevation(
            GradingZone zone,
            DesignSurface surface,
            TerrainSnapshot terrain,
            BuildingFootprintLookup lookup,
            int siteDefaultElevation) {
        if (surface == null) {
            return siteDefaultElevation;
        }
        if (surface.getElevationSource() == DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION) {
            if (zone == null) {
                return siteDefaultElevation;
            }
            return resolveBuildingBaseElevation(zone, surface, terrain, lookup, siteDefaultElevation);
        }
        if (surface.getBottomElevation() != null) {
            return surface.getBottomElevation() + surface.getExcavationPit().totalExcavationDepth();
        }
        return siteDefaultElevation;
    }

    private static int resolveBuildingBaseElevation(
            GradingZone zone,
            DesignSurface surface,
            TerrainSnapshot terrain,
            BuildingFootprintLookup lookup,
            int siteDefaultElevation) {
        if (surface.getElevation() != null) {
            return surface.getElevation();
        }
        String ref = resolveFootprintRef(zone, surface);
        BuildingFootprint footprint = lookup != null ? lookup.getFootprint(ref) : null;
        if (footprint != null) {
            List<Integer> samples = collectFootprintGroundSamples(terrain, footprint.getOuterPoints());
            return BuildingFoundationUtils.computeBaseElevation(
                samples,
                footprint.getManualBaseElevation());
        }
        return siteDefaultElevation;
    }

    public static String resolveFootprintRef(GradingZone zone, DesignSurface surface) {
        if (zone != null && zone.getBuildingFootprintRef() != null && !zone.getBuildingFootprintRef().isBlank()) {
            return zone.getBuildingFootprintRef();
        }
        if (surface != null && surface.getBuildingFootprintRef() != null && !surface.getBuildingFootprintRef().isBlank()) {
            return surface.getBuildingFootprintRef();
        }
        return "";
    }

    public static DesignSurfaceKind effectiveKind(GradingZone zone) {
        if (zone == null) {
            return DesignSurfaceKind.LEVEL_PAD;
        }
        return switch (zone.getType()) {
            case BUILDING_PAD -> DesignSurfaceKind.CONSTANT_ELEVATION;
            case EXCAVATION_PIT -> DesignSurfaceKind.EXCAVATION_PIT;
            case ROAD_CORRIDOR -> DesignSurfaceKind.ROAD_CORRIDOR;
            default -> zone.getDesignSurface().getKind();
        };
    }

    private static List<Integer> collectFootprintGroundSamples(TerrainSnapshot terrain, List<Vec2d> footprint) {
        List<Integer> samples = new ArrayList<>();
        if (terrain == null || footprint == null || footprint.size() < 3) {
            return samples;
        }
        for (TerrainSnapshot.Column column : terrain.columns()) {
            if (EarthworkGeometryUtils.containsCanvasPoint(footprint, column.center())) {
                samples.add(column.groundY());
            }
        }
        return samples;
    }
}
