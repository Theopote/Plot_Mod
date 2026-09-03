package com.plot.plugin.earthwork.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.plugin.earthwork.volume.EarthworkProjectReport;
import com.plot.plugin.earthwork.volume.SiteEarthworkReport;
import com.plot.plugin.earthwork.grading.DesignTerrainBuilder;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.EarthworkSiteBoundaryUtils;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * 场地级土方管线：Capture → Compose → Volume/Voxel → Retaining Wall。
 * <p>
 * Phase 17a：场地级土方编排入口；
 * Phase 17c：设计面合成经 {@link DesignTerrainBuilder}，步骤实现见 {@link DefaultSiteEarthworkOperations}。
 */
public final class SiteEarthworkPipeline {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/SiteEarthworkPipeline");

    private final SiteEarthworkOperations operations;

    public SiteEarthworkPipeline(SiteEarthworkOperations operations) {
        this.operations = operations;
    }

    public EarthworkGenerationResult execute(EarthworkPipelineContext context) {
        EarthworkGenerationResult result = new EarthworkGenerationResult();
        if (context == null || context.site() == null) {
            LOGGER.warn("场地为空");
            return result;
        }
        EarthworkSite site = context.site();
        if (context.world() == null
            && (context.terrainSnapshot() == null || context.terrainSnapshot().isEmpty())) {
            LOGGER.warn("场地或现状快照为空");
            return result;
        }

        if (site.delegatesToLegacyGenerator()) {
            return executeLegacyDelegate(site, context, result);
        }

        List<Vec2d> siteBoundary = site.getSiteBoundary();
        if (siteBoundary.size() < 3) {
            LOGGER.warn("场地红线点数不足");
            return result;
        }

        int edgeMargin = EarthworkSiteBoundaryUtils.resolveEdgeSlopeMarginBlocks(site.getGradingZones().values());
        if (edgeMargin > 0) {
            siteBoundary = EarthworkSiteBoundaryUtils.expandAxisAlignedBoundary(siteBoundary, edgeMargin);
        }

        result.siteGeneration = true;
        TerrainSnapshot terrain = operations.captureSiteTerrain(
            site, context.world(), siteBoundary, context.terrainSnapshot());
        if (terrain.isEmpty()) {
            LOGGER.warn("场地无有效 footprint 格点");
            return result;
        }
        result.existingTerrainSnapshot = terrain;
        result.calculationCellCount = terrain.columnCount();

        DesignTerrainBuilder.BuildResult design = DesignTerrainBuilder.build(
            site,
            terrain,
            operations.coordinateService(),
            context.buildingLookup(),
            context.roadLookup());
        result.designTerrainGrid = design.grid();
        result.resolvedElevationMin = design.grid().minTargetY();
        result.resolvedElevationMax = design.grid().maxTargetY();
        result.resolvedElevation = (result.resolvedElevationMin + result.resolvedElevationMax) / 2;
        result.slopedSurface = result.resolvedElevationMin != result.resolvedElevationMax;

        int previewGridSize = context.previewRegion() != null
            ? context.previewRegion().getPreviewGridSize()
            : GradingRegion.DEFAULT_PREVIEW_GRID_SIZE;
        operations.computeEarthworkFromDesignGrid(
            site, context.world(), design.grid(), result, previewGridSize);
        operations.generateRetainingWalls(
            site, context.world(), result, design.grid(), design.zoneEvaluators());

        result.syncChangedBlocksFromPlacements();
        result.attachPlayerInsights();
        result.projectReport = EarthworkProjectReport.Builder.buildFromProject(
            null, site, result.siteVolumeReport);
        operations.applyZoneLastReports(site, result);
        return result;
    }

    private EarthworkGenerationResult executeLegacyDelegate(
            EarthworkSite site,
            EarthworkPipelineContext context,
            EarthworkGenerationResult result) {
        GradingZone zone = site.getLegacyDelegateZone();
        EarthworkGenerationResult delegated = operations.generateLegacyRegion(
            zone.getRegion(),
            context.world(),
            context.terrainSnapshot(),
            zone.getEdgeSettings());
        operations.copyGenerationResult(result, delegated);
        result.syncChangedBlocksFromPlacements();
        result.attachPlayerInsights();
        result.siteVolumeReport = new SiteEarthworkReport(
            delegated.volumeReport,
            Map.of(zone.getId(), delegated.volumeReport));
        result.projectReport = EarthworkProjectReport.Builder.buildFromProject(
            null, site, result.siteVolumeReport);
        site.setLastReport(result.siteVolumeReport.totals());
        return result;
    }
}
