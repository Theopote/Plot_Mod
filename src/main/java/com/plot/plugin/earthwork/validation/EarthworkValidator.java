package com.plot.plugin.earthwork.validation;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.design.BuildingFootprintLookup;
import com.plot.plugin.earthwork.design.BuildingFootprintResolver;
import com.plot.plugin.earthwork.design.GradingSurfaceResolver;
import com.plot.plugin.earthwork.grading.ZoneOverlapAnalyzer;
import com.plot.plugin.earthwork.model.CompositionPolicy;
import com.plot.plugin.earthwork.model.DesignSurfaceElevationSource;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.ExclusionZone;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingSurfaceMode;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.GradingZoneType;

import java.util.ArrayList;
import java.util.List;

/**
 * 土方预览/生成前工程检查（对标 {@link com.plot.plugin.road.RoadNetworkEngineeringValidator}）。
 */
public final class EarthworkValidator {

    private static final double COLLINEAR_TOLERANCE = 1e-4;

    private EarthworkValidator() {
    }

    /**
     * 不依赖 World / 地形采样的硬校验。所有预览入口应先执行。
     */
    public static EarthworkValidationReport analyzePrePreview(
            EarthworkProject project,
            GradingRegion previewRegion) {
        return analyzePrePreview(project, previewRegion, null);
    }

    /**
     * @param buildingLookup 可选；提供时校验建筑引用是否可解析（fail closed）。
     */
    public static EarthworkValidationReport analyzePrePreview(
            EarthworkProject project,
            GradingRegion previewRegion,
            BuildingFootprintLookup buildingLookup) {
        List<EarthworkValidationReport.Item> items = new ArrayList<>();
        if (project == null) {
            items.add(EarthworkValidationReport.Item.error("plugin.earthwork.validation.project_missing"));
            return new EarthworkValidationReport(items);
        }
        if (previewRegion == null) {
            items.add(EarthworkValidationReport.Item.error("plugin.earthwork.validation.region_missing"));
            return new EarthworkValidationReport(items);
        }

        validateRegion(previewRegion, items);
        EarthworkSite site = project.getActiveSite();
        if (site.delegatesToLegacyGenerator()) {
            return new EarthworkValidationReport(items);
        }

        validateSite(site, previewRegion, buildingLookup, items);
        return new EarthworkValidationReport(items);
    }

    private static void validateRegion(GradingRegion region, List<EarthworkValidationReport.Item> items) {
        if (region.getOuterPoints().size() < 3) {
            items.add(EarthworkValidationReport.Item.error(
                "plugin.earthwork.validation.region_outline_insufficient",
                region.getName()));
            return;
        }

        if (isBlank(region.getFillMaterial())) {
            items.add(EarthworkValidationReport.Item.error(
                "plugin.earthwork.validation.fill_material_missing",
                region.getName()));
        }

        if (!region.isAutoBalance() && region.getManualTargetElevation() == null) {
            items.add(EarthworkValidationReport.Item.error(
                "plugin.earthwork.validation.manual_elevation_missing",
                region.getName()));
        }

        if (region.getPreviewGridSize() < 1) {
            items.add(EarthworkValidationReport.Item.error(
                "plugin.earthwork.validation.preview_grid_invalid",
                region.getName()));
        }

        validateSurfaceMode(region, items);
        validateHoles(region, items);
    }

    private static void validateSurfaceMode(GradingRegion region, List<EarthworkValidationReport.Item> items) {
        GradingSurfaceMode mode = region.getSurfaceMode();
        if (mode == null) {
            return;
        }
        switch (mode) {
            case SINGLE_SLOPE_PLANE -> {
                if (region.getSlopePitchRatio() < 1) {
                    items.add(EarthworkValidationReport.Item.error(
                        "plugin.earthwork.validation.slope_pitch_invalid",
                        region.getName()));
                }
            }
            case THREE_POINT_PLANE -> {
                if (areThreePointsCollinear(region)) {
                    items.add(EarthworkValidationReport.Item.warning(
                        "plugin.earthwork.validation.three_point_collinear",
                        region.getName()));
                }
            }
            case MULTI_PLANE -> items.add(EarthworkValidationReport.Item.warning(
                "plugin.earthwork.validation.multi_plane_limited",
                region.getName()));
            default -> {
            }
        }
    }

    private static void validateHoles(GradingRegion region, List<EarthworkValidationReport.Item> items) {
        int holeIndex = 0;
        for (List<Vec2d> hole : region.getHoles()) {
            holeIndex++;
            if (hole == null || hole.size() < 3) {
                items.add(EarthworkValidationReport.Item.error(
                    "plugin.earthwork.validation.hole_outline_insufficient",
                    region.getName(),
                    holeIndex));
            }
        }
    }

    private static void validateSite(
            EarthworkSite site,
            GradingRegion previewRegion,
            BuildingFootprintLookup buildingLookup,
            List<EarthworkValidationReport.Item> items) {
        site.refreshSiteBoundaryIfNeeded();
        if (site.getSiteBoundary().size() < 3 && site.getZoneCount() == 0) {
            items.add(EarthworkValidationReport.Item.error("plugin.earthwork.validation.site_boundary_insufficient"));
        }

        int enabledZones = 0;
        for (GradingZone zone : site.getGradingZones().values()) {
            if (zone == null || !zone.isEnabled()) {
                continue;
            }
            enabledZones++;
            validateZone(zone, buildingLookup, items);
        }
        if (enabledZones == 0) {
            items.add(EarthworkValidationReport.Item.error("plugin.earthwork.validation.no_enabled_zones"));
        }

        int exclusionIndex = 0;
        for (ExclusionZone exclusion : site.getExclusionZones()) {
            exclusionIndex++;
            if (exclusion == null || exclusion.getOuterPoints().size() < 3) {
                items.add(EarthworkValidationReport.Item.error(
                    "plugin.earthwork.validation.exclusion_outline_insufficient",
                    exclusionIndex));
            }
        }

        if (site.getZoneCount() >= 2) {
            List<ZoneOverlapAnalyzer.ZoneOverlap> overlaps = ZoneOverlapAnalyzer.findOverlaps(site);
            if (!overlaps.isEmpty()) {
                items.add(EarthworkValidationReport.Item.warning(
                    "plugin.earthwork.validation.zone_overlap_detected",
                    overlaps.size()));
            }
        }

        if (previewRegion != null
            && site.getZone(previewRegion.getId()) == null
            && site.getZoneCount() > 0) {
            items.add(EarthworkValidationReport.Item.warning(
                "plugin.earthwork.validation.preview_region_not_in_site",
                previewRegion.getName()));
        }

        validateDesignConstraints(site, items);
    }

    /**
     * Design Constraint：场地平衡与控制标高语义冲突等。
     * <p>
     * {@link com.plot.plugin.earthwork.model.VerticalAdjustmentPolicy.Mode#LOCKED} / {@code DERIVED}
     * 分区已排除在 Solver 变量之外；若建筑地坪仍允许竖向调整，则警告场地平衡可能改动控制标高。
     */
    private static void validateDesignConstraints(
            EarthworkSite site,
            List<EarthworkValidationReport.Item> items) {
        CompositionPolicy policy = site.getCompositionPolicy();
        if (policy == null || !policy.isVerticalOptimizationEnabled()) {
            return;
        }

        for (GradingZone zone : site.getGradingZones().values()) {
            if (zone == null || !zone.isEnabled()) {
                continue;
            }
            if (zone.getType() != GradingZoneType.BUILDING_PAD) {
                continue;
            }
            if (zone.isElevationLocked()) {
                continue;
            }
            items.add(EarthworkValidationReport.Item.warning(
                "plugin.earthwork.validation.site_balance_may_adjust_building_pad",
                zone.getName()));
        }
    }

    private static void validateZone(
            GradingZone zone,
            BuildingFootprintLookup buildingLookup,
            List<EarthworkValidationReport.Item> items) {
        GradingRegion region = zone.getRegion();
        if (region.getOuterPoints().size() < 3) {
            items.add(EarthworkValidationReport.Item.error(
                "plugin.earthwork.validation.zone_outline_insufficient",
                zone.getName()));
            return;
        }

        GradingZoneType type = zone.getType();
        if (type != null && !type.isSupportedInComposer()) {
            items.add(EarthworkValidationReport.Item.error(
                "plugin.earthwork.validation.zone_type_unsupported",
                zone.getName(),
                type.name()));
        }

        if (type == GradingZoneType.BUILDING_PAD
            && isBlank(zone.getBuildingFootprintRef())
            && zone.getDesignSurface().getElevation() == null) {
            items.add(EarthworkValidationReport.Item.warning(
                "plugin.earthwork.validation.building_pad_no_reference",
                zone.getName()));
        }

        if (type == GradingZoneType.EXCAVATION_PIT
            && zone.getDesignSurface().getElevationSource() == DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION) {
            validateExcavationPitBuildingReference(zone, buildingLookup, items);
        }

        if (type == GradingZoneType.ROAD_CORRIDOR && isBlank(zone.getRoadEdgeRef())) {
            items.add(EarthworkValidationReport.Item.warning(
                "plugin.earthwork.validation.road_corridor_no_reference",
                zone.getName()));
        }

        validateHoles(region, items);
    }

    /**
     * 自动坑底依赖建筑基准：缺引用或无法解析一律 ERROR（禁止回退到场地默认标高后仍生成）。
     */
    private static void validateExcavationPitBuildingReference(
            GradingZone zone,
            BuildingFootprintLookup buildingLookup,
            List<EarthworkValidationReport.Item> items) {
        String ref = BuildingFootprintResolver.resolveFootprintRef(zone, zone.getDesignSurface());
        if (isBlank(ref)) {
            items.add(EarthworkValidationReport.Item.error(
                "plugin.earthwork.validation.excavation_pit_no_reference",
                zone.getName()));
            return;
        }
        if (buildingLookup != null && buildingLookup.getFootprint(ref) == null) {
            items.add(EarthworkValidationReport.Item.error(
                "plugin.earthwork.validation.excavation_pit_unresolved_reference",
                zone.getName(),
                ref));
        }
    }

    private static boolean areThreePointsCollinear(GradingRegion region) {
        double x1 = region.getThreePointCanvasX(0);
        double z1 = region.getThreePointCanvasY(0);
        double x2 = region.getThreePointCanvasX(1);
        double z2 = region.getThreePointCanvasY(1);
        double x3 = region.getThreePointCanvasX(2);
        double z3 = region.getThreePointCanvasY(2);
        double det = x1 * (z2 - z3) + x2 * (z3 - z1) + x3 * (z1 - z2);
        if (Math.abs(det) < COLLINEAR_TOLERANCE) {
            return true;
        }
        int y1 = region.getThreePointElevation(0);
        int y2 = region.getThreePointElevation(1);
        int y3 = region.getThreePointElevation(2);
        return GradingSurfaceResolver.solveThreePointPlane(
            x1, z1, y1, x2, z2, y2, x3, z3, y3) == null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
