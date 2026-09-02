package com.plot.plugin.earthwork.design;

import com.plot.api.world.ICoordinateService;
import com.plot.plugin.earthwork.BuildingFootprintLookup;
import com.plot.plugin.earthwork.DesignSurfaceResolver;
import com.plot.plugin.earthwork.RoadSurfaceLookup;
import com.plot.plugin.earthwork.TerrainSnapshot;
import com.plot.plugin.earthwork.model.EarthworkSite;

import java.util.Map;

/**
 * 场地多分区设计面求值器注册表门面。
 */
public final class ZoneSurfaceEvaluatorRegistry {

    /**
     * 与 {@link DesignSurfaceResolver.ZoneTargetEvaluator} 同义；2.0 命名。
     */
    @FunctionalInterface
    public interface ZoneSurfaceEvaluator {
        int evaluateAt(com.plot.plugin.earthwork.DesignTerrainCell cell);
    }

    private ZoneSurfaceEvaluatorRegistry() {
    }

    public static Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> resolve(
            EarthworkSite site,
            TerrainSnapshot terrain,
            BuildingFootprintLookup buildingLookup,
            RoadSurfaceLookup roadLookup,
            ICoordinateService transformer) {
        return DesignSurfaceResolver.resolveZoneEvaluators(
            site, terrain, buildingLookup, roadLookup, transformer);
    }
}
