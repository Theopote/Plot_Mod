package com.plot.plugin.earthwork.grading;

import com.plot.api.world.ICoordinateService;
import com.plot.plugin.earthwork.design.BuildingFootprintLookup;
import com.plot.plugin.earthwork.design.DesignSurfaceResolver;
import com.plot.plugin.earthwork.design.DesignTerrainComposer;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.design.RoadSurfaceLookup;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.plugin.earthwork.model.EarthworkSite;

import java.util.Map;

/**
 * 合成场地级 {@link DesignTerrainGrid}（含分区覆盖、全场/分区平衡、边坡与混合）。
 * <p>
 * 包装 {@link DesignTerrainComposer}，作为 grading 层对外入口。
 */
public final class DesignTerrainBuilder {

    public record BuildResult(
            DesignTerrainGrid grid,
            Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> zoneEvaluators) {
    }

    private DesignTerrainBuilder() {
    }

    public static BuildResult build(
            EarthworkSite site,
            TerrainSnapshot terrain,
            ICoordinateService transformer) {
        return build(site, terrain, transformer, BuildingFootprintLookup.NONE, RoadSurfaceLookup.NONE);
    }

    public static BuildResult build(
            EarthworkSite site,
            TerrainSnapshot terrain,
            ICoordinateService transformer,
            BuildingFootprintLookup buildingLookup,
            RoadSurfaceLookup roadLookup) {
        DesignTerrainComposer.ComposeResult composed = DesignTerrainComposer.compose(
            site, terrain, transformer, buildingLookup, roadLookup);
        return new BuildResult(composed.grid(), composed.zoneEvaluators());
    }
}
