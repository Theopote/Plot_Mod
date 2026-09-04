package com.plot.plugin.building.site;

import com.plot.api.geometry.Vec2d;
import com.plot.api.plugin.IPlugin;
import com.plot.core.plugin.PluginManager;
import com.plot.plugin.EarthworkPlugin;
import com.plot.plugin.building.model.BuildingFootprint;

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
}
