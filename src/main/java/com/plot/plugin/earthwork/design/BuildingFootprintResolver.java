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
import com.plot.plugin.earthwork.model.GradingZoneType;
import com.plot.utils.PlotI18n;

import java.util.ArrayList;
import java.util.List;

/**
 * 从建筑轮廓解析室外地坪 ±0.000 / 基坑基准标高。
 * <p>
 * 一律返回 {@link ResolutionResult}，由下游决定：
 * <ul>
 *   <li>手动恒定标高：{@link ResolutionResult.Status#FALLBACK} 可用场地推荐值</li>
 *   <li>显式 {@code BUILDING_BASE_ELEVATION}（地坪或基坑）：仅 {@link ResolutionResult.Status#RESOLVED}
 *       可写入设计面（fail closed；诊断 value 不得当设计值）</li>
 * </ul>
 */
public final class BuildingFootprintResolver {
    private BuildingFootprintResolver() {
    }

    /** 要求 RESOLVED 但未满足时抛出（由 {@link ResolutionResult#requireResolved} 使用）。 */
    public static final class UnresolvedBuildingReferenceException extends IllegalStateException {
        public UnresolvedBuildingReferenceException(String message) {
            super(message);
        }
    }

    /**
     * 建筑地坪 / 恒定标高解析。引用失败时返回 {@code MISSING_REFERENCE}/{@code INVALID_REFERENCE}
     * 并附带诊断用场地默认值（不得伪装成 RESOLVED）。合成层对建筑联动路径须 {@code requireResolved}。
     */
    public static ResolutionResult<Integer> resolveConstantElevation(
            GradingZone zone,
            DesignSurface surface,
            TerrainSnapshot terrain,
            BuildingFootprintLookup lookup,
            int siteDefaultElevation) {
        if (zone == null || surface == null) {
            return ResolutionResult.fallback(siteDefaultElevation, "zone or surface is null");
        }
        if (surface.getElevation() != null) {
            return ResolutionResult.resolved(surface.getElevation(), "explicit design elevation");
        }
        if (surface.getElevationSource() == DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION) {
            return resolveBuildingBaseElevation(zone, surface, terrain, lookup, siteDefaultElevation);
        }
        if (surface.getManualTargetElevation() != null) {
            return ResolutionResult.resolved(surface.getManualTargetElevation(), "manual target elevation");
        }
        return ResolutionResult.fallback(siteDefaultElevation, "no elevation source; using site default");
    }

    /**
     * 基坑坑底：手动 {@link DesignSurface#getBottomElevation()}，或
     * {@code referenceElevation - basementFloorDepth - foundationDepth - workingAllowance}。
     * <p>
     * 建筑联动时引用失败返回 {@code MISSING_REFERENCE}/{@code INVALID_REFERENCE}（value 仅为诊断用场地默认，
     * 下游须 {@link ResolutionResult#requireResolved}，不得当设计值使用）。
     */
    public static ResolutionResult<Integer> resolvePitBottomElevation(
            GradingZone zone,
            DesignSurface surface,
            TerrainSnapshot terrain,
            BuildingFootprintLookup lookup,
            int siteDefaultElevation) {
        if (surface == null) {
            return ResolutionResult.fallback(siteDefaultElevation, "surface is null");
        }
        if (surface.getElevationSource() == DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION) {
            if (zone == null) {
                return ResolutionResult.missingReference(
                    siteDefaultElevation,
                    "Excavation pit requires a grading zone for building-linked pit bottom");
            }
            ResolutionResult<Integer> reference = resolveBuildingBaseElevation(
                zone, surface, terrain, lookup, siteDefaultElevation);
            if (!reference.isResolved()) {
                return reference;
            }
            int bottom = surface.getExcavationPit().pitBottomFrom(reference.value());
            return ResolutionResult.resolved(bottom, "building-linked pit bottom");
        }
        if (surface.getBottomElevation() != null) {
            return ResolutionResult.resolved(surface.getBottomElevation(), "manual pit bottom");
        }
        return ResolutionResult.fallback(siteDefaultElevation, "manual pit bottom unset; using site default");
    }

    /** 解析基坑基准标高（建筑 ±0.000 / 基础底），不扣减埋深。 */
    public static ResolutionResult<Integer> resolvePitReferenceElevation(
            GradingZone zone,
            DesignSurface surface,
            TerrainSnapshot terrain,
            BuildingFootprintLookup lookup,
            int siteDefaultElevation) {
        if (surface == null) {
            return ResolutionResult.fallback(siteDefaultElevation, "surface is null");
        }
        if (surface.getElevationSource() == DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION) {
            if (zone == null) {
                return ResolutionResult.missingReference(
                    siteDefaultElevation,
                    "Excavation pit requires a grading zone for building-linked reference elevation");
            }
            return resolveBuildingBaseElevation(zone, surface, terrain, lookup, siteDefaultElevation);
        }
        if (surface.getBottomElevation() != null) {
            int reference = surface.getBottomElevation() + surface.getExcavationPit().totalExcavationDepth();
            return ResolutionResult.resolved(reference, "inferred from manual pit bottom");
        }
        return ResolutionResult.fallback(siteDefaultElevation, "pit reference unset; using site default");
    }

    /**
     * @return true 当引用非空且（无 lookup 时仅检查引用存在，有 lookup 时 footprint 可解析）
     */
    public static boolean isBuildingReferenceResolvable(
            GradingZone zone,
            DesignSurface surface,
            BuildingFootprintLookup lookup) {
        String ref = resolveFootprintRef(zone, surface);
        if (ref == null || ref.isBlank()) {
            return false;
        }
        if (lookup == null) {
            return true;
        }
        return lookup.getFootprint(ref) != null;
    }

    private static ResolutionResult<Integer> resolveBuildingBaseElevation(
            GradingZone zone,
            DesignSurface surface,
            TerrainSnapshot terrain,
            BuildingFootprintLookup lookup,
            int siteDefaultElevation) {
        if (surface.getElevation() != null) {
            return ResolutionResult.resolved(surface.getElevation(), "explicit elevation overrides building base");
        }
        String ref = resolveFootprintRef(zone, surface);
        String zoneName = zone != null ? zone.getName() : "";
        boolean pit = zone != null && zone.getType() == GradingZoneType.EXCAVATION_PIT;
        if (ref == null || ref.isBlank()) {
            return ResolutionResult.missingReference(
                siteDefaultElevation,
                PlotI18n.tr(
                    pit
                        ? "plugin.earthwork.validation.excavation_pit_no_reference"
                        : "plugin.earthwork.validation.building_pad_linked_no_reference",
                    zoneName));
        }
        BuildingFootprint footprint = lookup != null ? lookup.getFootprint(ref) : null;
        if (footprint == null) {
            return ResolutionResult.invalidReference(
                siteDefaultElevation,
                PlotI18n.tr(
                    pit
                        ? "plugin.earthwork.validation.excavation_pit_unresolved_reference"
                        : "plugin.earthwork.validation.building_pad_unresolved_reference",
                    zoneName,
                    ref));
        }
        List<Integer> samples = collectFootprintGroundSamples(terrain, footprint.getOuterPoints());
        int elevation = BuildingFoundationUtils.computeBaseElevation(
            samples,
            footprint.getManualBaseElevation());
        return ResolutionResult.resolved(elevation, "building footprint " + ref);
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
