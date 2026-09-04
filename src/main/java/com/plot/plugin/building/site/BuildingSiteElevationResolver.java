package com.plot.plugin.building.site;

import com.plot.api.geometry.Vec2d;
import com.plot.api.plugin.IPlugin;
import com.plot.core.plugin.PluginManager;
import com.plot.plugin.EarthworkPlugin;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.earthwork.design.BuildingPadElevationService;
import com.plot.plugin.earthwork.design.BuildingPadElevationService.PadElevationStatus;

import java.util.List;

/**
 * 从土方插件解析与建筑关联的垫层设计标高（单向：土方 → 建筑）。
 */
public final class BuildingSiteElevationResolver {
    private BuildingSiteElevationResolver() {
    }

    public static Integer resolveEarthworkPadElevation(BuildingFootprint footprint) {
        if (footprint == null || footprint.getId() == null || footprint.getId().isBlank()) {
            return null;
        }
        return resolveEarthworkPadElevation(footprint.getId(), footprint.getOuterPoints());
    }

    public static Integer resolveEarthworkPadElevation(String buildingId, List<Vec2d> footprintPoints) {
        IPlugin plugin = PluginManager.getInstance().getPlugin("earthwork_balance");
        if (plugin instanceof EarthworkPlugin earthwork) {
            return earthwork.resolveBuildingPadDesignElevation(buildingId, footprintPoints);
        }
        return null;
    }

    public static PadElevationStatus describePadLink(BuildingFootprint footprint) {
        if (footprint == null || footprint.getId() == null || footprint.getId().isBlank()) {
            return PadElevationStatus.none();
        }
        IPlugin plugin = PluginManager.getInstance().getPlugin("earthwork_balance");
        if (plugin instanceof EarthworkPlugin earthwork) {
            return earthwork.describeBuildingPadLink(footprint.getId(), footprint.getOuterPoints());
        }
        return PadElevationStatus.none();
    }

    /**
     * 供土方 UI 显示：建筑侧不含循环依赖的基准标高（仅手动标高；地形采样不在 UI 中展开）。
     */
    public static Integer resolveBuildingManualBaseElevation(BuildingFootprint footprint) {
        if (footprint == null) {
            return null;
        }
        return footprint.getManualBaseElevation();
    }
}
