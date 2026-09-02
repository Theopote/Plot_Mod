package com.plot.plugin.earthwork.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.earthwork.DesignSurfaceResolver;
import com.plot.plugin.earthwork.DesignTerrainGrid;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.EarthworkVolumeReport;
import com.plot.plugin.earthwork.RetainingWallGenerator;
import com.plot.plugin.earthwork.TerrainSnapshot;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.ZoneEdgeSettings;
import com.plot.plugin.earthwork.terrain.SiteTerrainCapture;
import com.plot.plugin.earthwork.volume.EarthworkVolumeCalculator;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;

/**
 * {@link SiteEarthworkPipeline} 默认步骤实现，不依赖 {@link EarthworkGenerator} 内部类。
 */
public final class DefaultSiteEarthworkOperations implements SiteEarthworkOperations {

    private final ICoordinateService coordinateService;
    private final EarthworkVolumeCalculator volumeCalculator;
    private final LegacyRegionPipeline legacyRegionPipeline;

    public DefaultSiteEarthworkOperations(
            ICoordinateService coordinateService,
            EarthworkVolumeCalculator volumeCalculator,
            LegacyRegionPipeline legacyRegionPipeline) {
        this.coordinateService = coordinateService;
        this.volumeCalculator = volumeCalculator;
        this.legacyRegionPipeline = legacyRegionPipeline;
    }

    @Override
    public ICoordinateService coordinateService() {
        return coordinateService;
    }

    @Override
    public TerrainSnapshot captureSiteTerrain(
            EarthworkSite site,
            World world,
            List<Vec2d> siteBoundary,
            TerrainSnapshot terrainSnapshot) {
        return SiteTerrainCapture.captureSite(
            coordinateService, site, world, siteBoundary, terrainSnapshot);
    }

    @Override
    public EarthworkGenerationResult generateLegacyRegion(
            GradingRegion region,
            World world,
            TerrainSnapshot terrainSnapshot,
            ZoneEdgeSettings edgeSettings) {
        return legacyRegionPipeline.execute(region, world, terrainSnapshot, edgeSettings);
    }

    @Override
    public void copyGenerationResult(
            EarthworkGenerationResult target,
            EarthworkGenerationResult source) {
        EarthworkGenerationResults.copyInto(target, source);
    }

    @Override
    public void computeEarthworkFromDesignGrid(
            EarthworkSite site,
            World world,
            DesignTerrainGrid grid,
            EarthworkGenerationResult result,
            int previewGridSize) {
        volumeCalculator.computeFromDesignGrid(site, world, grid, result, previewGridSize);
    }

    @Override
    public void generateRetainingWalls(
            EarthworkSite site,
            World world,
            EarthworkGenerationResult result,
            DesignTerrainGrid grid,
            Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> zoneEvaluators) {
        RetainingWallGenerator.generate(
            site, world, coordinateService, result, grid, zoneEvaluators);
    }

    @Override
    public void applyZoneLastReports(
            EarthworkSite site,
            EarthworkGenerationResult result) {
        site.setLastReport(result.siteVolumeReport.totals());
        for (GradingZone zone : site.getGradingZones().values()) {
            EarthworkVolumeReport zoneReport = result.siteVolumeReport.zoneReport(zone.getId());
            zone.getRegion().setLastVolumeReport(zoneReport);
            zone.getRegion().setLastResolvedElevation(result.resolvedElevation);
            zone.getRegion().setLastResolvedElevationMin(result.resolvedElevationMin);
            zone.getRegion().setLastResolvedElevationMax(result.resolvedElevationMax);
        }
    }
}
