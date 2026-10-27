package com.plot.plugin.earthwork.model;

import java.util.List;

/** 玩家任务入口；旧领域类型仍用于读取已有方案，不在渲染时改写。 */
public enum EarthworkTerrainTask {
    FLATTEN, SLOPE, FOLLOW_TERRAIN, BUILDING_PAD, EXCAVATE, ROAD_CONNECTION;

    public String i18nKey() {
        return "plugin.earthwork.task." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public static EarthworkTerrainTask fromZone(GradingZone zone) {
        return switch (zone.getType()) {
            case BUILDING_PAD -> BUILDING_PAD;
            case EXCAVATION_PIT -> EXCAVATE;
            case ROAD_CORRIDOR -> ROAD_CONNECTION;
            case LANDSCAPE, TERRAIN_FIT -> FOLLOW_TERRAIN;
            case FLAT, SLOPED -> switch (zone.getRegion().getSurfaceMode()) {
                case LEVEL_PAD -> FLATTEN;
                case SINGLE_SLOPE_PLANE, THREE_POINT_PLANE, MULTI_PLANE -> SLOPE;
                case BEST_FIT_PLANE, MATCH_EXISTING, DRAINAGE_SURFACE -> FOLLOW_TERRAIN;
            };
        };
    }

    public List<GradingSurfaceMode> surfaceModes() {
        return switch (this) {
            case FLATTEN -> List.of(GradingSurfaceMode.LEVEL_PAD);
            case SLOPE -> List.of(GradingSurfaceMode.SINGLE_SLOPE_PLANE, GradingSurfaceMode.THREE_POINT_PLANE);
            case FOLLOW_TERRAIN -> List.of(GradingSurfaceMode.MATCH_EXISTING, GradingSurfaceMode.BEST_FIT_PLANE);
            default -> List.of();
        };
    }

    /** 仅在玩家明确更改任务时调用；不把烘焙的旧设计面带入新任务。 */
    public void applyTo(GradingZone zone) {
        zone.resetTaskSpecificState();
        zone.getDesignSurface().setBakedElevationGrid(new BakedElevationGrid());
        switch (this) {
            case BUILDING_PAD -> zone.setType(GradingZoneType.BUILDING_PAD);
            case EXCAVATE -> zone.setType(GradingZoneType.EXCAVATION_PIT);
            case ROAD_CONNECTION -> zone.setType(GradingZoneType.ROAD_CORRIDOR);
            default -> applySurfaceMode(zone, surfaceModes().getFirst());
        }
    }

    public static void applySurfaceMode(GradingZone zone, GradingSurfaceMode mode) {
        zone.setType(GradingZoneType.fromSurfaceMode(mode));
        GradingRegion region = zone.getRegion();
        region.setSurfaceMode(mode);
        zone.getDesignSurface().setKind(DesignSurfaceKind.fromSurfaceMode(mode));
        zone.getDesignSurface().setBakedElevationGrid(new BakedElevationGrid());
        zone.syncDesignSurfaceToRegion();
        zone.syncVerticalPolicyWithAutoBalance();
    }
}
