package com.plot.plugin.earthwork.pipeline;

import com.plot.plugin.earthwork.BuildingFootprintLookup;
import com.plot.plugin.earthwork.RoadSurfaceLookup;
import com.plot.plugin.earthwork.TerrainSnapshot;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingRegion;
import net.minecraft.world.World;

/**
 * {@link SiteEarthworkPipeline} 单次执行的输入上下文。
 */
public record EarthworkPipelineContext(
        EarthworkSite site,
        World world,
        TerrainSnapshot terrainSnapshot,
        GradingRegion previewRegion,
        BuildingFootprintLookup buildingLookup,
        RoadSurfaceLookup roadLookup) {

    public EarthworkPipelineContext {
        buildingLookup = buildingLookup != null ? buildingLookup : BuildingFootprintLookup.NONE;
        roadLookup = roadLookup != null ? roadLookup : RoadSurfaceLookup.NONE;
    }

    public static EarthworkPipelineContext of(
            EarthworkSite site,
            World world,
            TerrainSnapshot terrainSnapshot) {
        return new EarthworkPipelineContext(
            site, world, terrainSnapshot, null, BuildingFootprintLookup.NONE, RoadSurfaceLookup.NONE);
    }

    public static EarthworkPipelineContext of(
            EarthworkSite site,
            World world,
            TerrainSnapshot terrainSnapshot,
            GradingRegion previewRegion) {
        return new EarthworkPipelineContext(
            site, world, terrainSnapshot, previewRegion, BuildingFootprintLookup.NONE, RoadSurfaceLookup.NONE);
    }

    public static EarthworkPipelineContext of(
            EarthworkSite site,
            World world,
            TerrainSnapshot terrainSnapshot,
            GradingRegion previewRegion,
            BuildingFootprintLookup buildingLookup,
            RoadSurfaceLookup roadLookup) {
        return new EarthworkPipelineContext(
            site, world, terrainSnapshot, previewRegion, buildingLookup, roadLookup);
    }
}
