package com.plot.plugin.earthwork.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.material.MaterialConversionModel;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.grading.GradingPlane;
import com.plot.plugin.earthwork.design.GradingSurfaceResolver;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.plugin.earthwork.design.RegionSurfaceEvaluator;
import com.plot.plugin.earthwork.model.EarthworkSiteBoundaryUtils;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.ZoneEdgeSettings;
import com.plot.plugin.earthwork.terrain.SiteTerrainCapture;
import com.plot.plugin.earthwork.volume.EarthworkVolumeCalculator;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 单分区 legacy 管线：Capture → Resolve → Volume/Voxel。
 */
public final class LegacyRegionPipeline {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/LegacyRegionPipeline");

    private final ICoordinateService coordinateService;
    private final EarthworkVolumeCalculator volumeCalculator;

    public LegacyRegionPipeline(
            ICoordinateService coordinateService,
            EarthworkVolumeCalculator volumeCalculator) {
        this.coordinateService = coordinateService;
        this.volumeCalculator = volumeCalculator;
    }

    public EarthworkGenerationResult execute(
            GradingRegion region,
            World world,
            TerrainSnapshot terrainSnapshot,
            ZoneEdgeSettings edgeSettings) {
        return execute(region, world, terrainSnapshot, edgeSettings, null);
    }

    public EarthworkGenerationResult execute(
            GradingRegion region,
            World world,
            TerrainSnapshot terrainSnapshot,
            ZoneEdgeSettings edgeSettings,
            MaterialConversionModel siteMaterialModel) {
        EarthworkGenerationResult result = new EarthworkGenerationResult();
        if (region == null) {
            LOGGER.warn("整平区域为空");
            return result;
        }
        if (world == null && (terrainSnapshot == null || terrainSnapshot.isEmpty())) {
            LOGGER.warn("整平区域或现状快照为空");
            return result;
        }

        MaterialConversionModel balanceMaterials = region.resolveMaterialModel(siteMaterialModel);

        List<Vec2d> outerPoints = region.getOuterPoints();
        if (outerPoints.size() < 3) {
            LOGGER.warn("整平区域轮廓点数不足");
            return result;
        }
        if (edgeSettings != null && edgeSettings.hasActiveTreatment()) {
            outerPoints = EarthworkSiteBoundaryUtils.expandAxisAlignedBoundary(
                outerPoints, edgeSettings.getMaximumReachBlocks());
        }

        TerrainSnapshot terrain = SiteTerrainCapture.captureRegion(
            coordinateService, world, outerPoints, terrainSnapshot);
        if (terrain.isEmpty()) {
            LOGGER.warn("整平区域无有效 footprint 格点");
            return result;
        }
        result.existingTerrainSnapshot = terrain;
        result.calculationCellCount = terrain.columnCount();

        GradingSurfaceResolver.ResolvedSurface surface = RegionSurfaceEvaluator.resolve(
            region, terrain, coordinateService, false, balanceMaterials);
        GradingPlane plane = surface.plane();
        result.resolvedElevation = plane.isFlat()
            ? surface.elevationMin()
            : (surface.elevationMin() + surface.elevationMax()) / 2;
        result.resolvedElevationMin = surface.elevationMin();
        result.resolvedElevationMax = surface.elevationMax();
        result.slopedSurface = !plane.isFlat();

        volumeCalculator.computeFromPlane(
            region, world, terrain, plane, result, region.getPreviewGridSize(), edgeSettings, balanceMaterials);
        result.syncChangedBlocksFromPlacements();
        result.attachPlayerInsights();

        region.setLastVolumeReport(result.volumeReport);
        region.setLastResolvedElevation(result.resolvedElevation);
        region.setLastResolvedElevationMin(result.resolvedElevationMin);
        region.setLastResolvedElevationMax(result.resolvedElevationMax);
        return result;
    }
}
