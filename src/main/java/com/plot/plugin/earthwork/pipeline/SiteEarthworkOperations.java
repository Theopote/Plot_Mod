package com.plot.plugin.earthwork.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.earthwork.design.DesignSurfaceResolver;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.ZoneEdgeSettings;
import net.minecraft.world.World;

import java.util.List;

/**
 * 场地土方管线步骤抽象；默认实现为 {@link DefaultSiteEarthworkOperations}。
 */
public interface SiteEarthworkOperations {

    ICoordinateService coordinateService();

    TerrainSnapshot captureSiteTerrain(
            EarthworkSite site,
            World world,
            List<Vec2d> siteBoundary,
            TerrainSnapshot terrainSnapshot);

    EarthworkGenerationResult generateLegacyRegion(
            GradingRegion region,
            World world,
            TerrainSnapshot terrainSnapshot,
            ZoneEdgeSettings edgeSettings);

    void copyGenerationResult(
            EarthworkGenerationResult target,
            EarthworkGenerationResult source);

    void computeEarthworkFromDesignGrid(
            EarthworkSite site,
            World world,
            DesignTerrainGrid grid,
            EarthworkGenerationResult result,
            int previewGridSize);

    void generateRetainingWalls(
            EarthworkSite site,
            World world,
            EarthworkGenerationResult result,
            DesignTerrainGrid grid,
            java.util.Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> zoneEvaluators);

    void applyZoneLastReports(
            EarthworkSite site,
            EarthworkGenerationResult result);
}
