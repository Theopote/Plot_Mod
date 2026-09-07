package com.plot.plugin.building.site;

import com.plot.api.building.BuildingPadElevationMode;
import com.plot.api.building.BuildingPadElevationStatus;
import com.plot.api.building.IBuildingPadElevationService;
import com.plot.api.geometry.Vec2d;
import com.plot.api.plugin.IPlugin;
import com.plot.core.plugin.PluginManager;
import com.plot.plugin.building.model.BuildingFootprint;

import java.util.List;

/**
 * 从土方插件解析与建筑关联的垫层设计标高（单向：土方 → 建筑）。
 * <p>
 * 仅依赖 {@link IBuildingPadElevationService} 契约，不引用 Earthwork 实现类。
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
        IBuildingPadElevationService service = padElevationService();
        if (service == null) {
            return null;
        }
        return service.resolveEarthworkOwnedPadElevation(buildingId, footprintPoints);
    }

    public static BuildingPadElevationStatus describePadLink(BuildingFootprint footprint) {
        if (footprint == null || footprint.getId() == null || footprint.getId().isBlank()) {
            return BuildingPadElevationStatus.none();
        }
        return describePadLink(footprint.getId(), footprint.getOuterPoints());
    }

    public static BuildingPadElevationStatus describePadLink(String buildingId, List<Vec2d> footprintPoints) {
        if (buildingId == null || buildingId.isBlank()) {
            return BuildingPadElevationStatus.none();
        }
        IBuildingPadElevationService service = padElevationService();
        if (service == null) {
            return BuildingPadElevationStatus.none();
        }
        return service.describePadLink(buildingId, footprintPoints);
    }

    /** EARTHWORK_OWNED 垫层已关联但当前无法解析设计标高。 */
    public static boolean isEarthworkOwnedUnresolved(BuildingPadElevationStatus status) {
        return status != null
            && status.mode() == BuildingPadElevationMode.EARTHWORK_OWNED
            && status.isLinked()
            && status.resolvedElevation() == null;
    }

    private static IBuildingPadElevationService padElevationService() {
        IPlugin plugin = PluginManager.getInstance().getPlugin("earthwork_balance");
        if (plugin instanceof IBuildingPadElevationService service) {
            return service;
        }
        return null;
    }
}
