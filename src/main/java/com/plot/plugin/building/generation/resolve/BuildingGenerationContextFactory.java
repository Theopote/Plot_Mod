package com.plot.plugin.building.generation.resolve;

import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.building.BuildingFoundationUtils;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.site.BuildingSiteAnalysis;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 编排 Definition / Massing / Site / Material 解析，组装 {@link BuildingGenerationContext}。
 * <p>
 * Context 本身不持有业务生成逻辑；本 Factory 也不做 Facade / Roof / Accessory 解析。
 */
public final class BuildingGenerationContextFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/BuildingGenerationContextFactory");

    private BuildingGenerationContextFactory() {
    }

    public static BuildingGenerationContext create(
            BuildingFootprint footprint,
            World world,
            ICoordinateService coordinateService,
            IBlockProjectionService projectionService) {
        Objects.requireNonNull(projectionService, "projectionService");
        BuildingGenerationResult result = new BuildingGenerationResult();

        if (footprint == null || world == null) {
            LOGGER.warn("建筑轮廓或世界为空");
            return BuildingGenerationContext.fromResolved(
                footprint, null, world, coordinateService, projectionService, result, null);
        }
        if (footprint.getOuterPoints().size() < 3) {
            LOGGER.warn("建筑轮廓点数不足");
            return BuildingGenerationContext.fromResolved(
                footprint, null, world, coordinateService, projectionService, result, null);
        }

        BuildingDefinition definition = BuildingDefinitionResolver.fromFootprint(footprint);
        return fromDefinition(definition, footprint, world, coordinateService, projectionService, result);
    }

    public static BuildingGenerationContext createFromDefinition(
            BuildingDefinition definition,
            BuildingFootprint footprint,
            World world,
            ICoordinateService coordinateService,
            IBlockProjectionService projectionService) {
        Objects.requireNonNull(projectionService, "projectionService");
        BuildingGenerationResult result = new BuildingGenerationResult();

        if (definition == null || world == null) {
            LOGGER.warn("建筑定义或世界为空");
            return BuildingGenerationContext.fromResolved(
                footprint, definition, world, coordinateService, projectionService, result, null);
        }
        if (definition.footprint().outerPoints().size() < 3) {
            LOGGER.warn("建筑轮廓点数不足");
            return BuildingGenerationContext.fromResolved(
                footprint, definition, world, coordinateService, projectionService, result, null);
        }

        return fromDefinition(definition, footprint, world, coordinateService, projectionService, result);
    }

    public static BuildingGenerationContext forTesting(
            BuildingDefinition definition,
            ICoordinateService coordinateService,
            IBlockProjectionService projectionService,
            BuildingGenerationResult result) {
        Objects.requireNonNull(definition, "definition");
        if (definition.footprint().outerPoints().size() < 3) {
            return BuildingGenerationContext.fromResolved(
                null, definition, null, coordinateService, projectionService, result, null);
        }
        BuildingFootprint footprint = BuildingDefinitionResolver.footprintForTesting(definition);
        return forTesting(footprint, coordinateService, projectionService, result);
    }

    public static BuildingGenerationContext forTesting(
            BuildingFootprint footprint,
            ICoordinateService coordinateService,
            IBlockProjectionService projectionService,
            BuildingGenerationResult result) {
        Objects.requireNonNull(footprint, "footprint");
        Objects.requireNonNull(coordinateService, "coordinateService");
        Objects.requireNonNull(projectionService, "projectionService");
        Objects.requireNonNull(result, "result");

        if (footprint.getOuterPoints().size() < 3) {
            LOGGER.warn("建筑轮廓点数不足");
            return BuildingGenerationContext.fromResolved(
                footprint, null, null, coordinateService, projectionService, result, null);
        }

        BuildingDefinition definition = BuildingDefinitionResolver.fromFootprint(footprint);
        ResolvedBuildingDefinition resolved = resolveForTesting(definition, result);
        return BuildingGenerationContext.fromResolved(
            footprint, definition, null, coordinateService, projectionService, result, resolved);
    }

    /**
     * 生产路径：Raw Definition → Resolved → Context。
     */
    public static ResolvedBuildingDefinition resolve(
            BuildingDefinition definition,
            BuildingFootprint footprint,
            World world,
            ICoordinateService coordinateService,
            BuildingGenerationResult result) {
        MassingGeometryResolver.ResolvedMassingGeometry massing =
            MassingGeometryResolver.resolve(definition, result);
        if (!massing.valid()) {
            return new ResolvedBuildingDefinition(
                definition,
                massing,
                BuildingSiteAnalysis.emptyFallback(
                    com.plot.core.terrain.EngineeringTerrainService.DEFAULT_GROUND_ELEVATION),
                GenerationSiteResolver.resolveForTesting(definition),
                MaterialResolver.resolve(definition),
                Map.of()
            );
        }
        GenerationSiteResolver.SiteResolveBundle siteBundle = GenerationSiteResolver.resolve(
            definition, footprint, massing, world, coordinateService, result);
        MaterialResolver.ResolvedMaterials materials = MaterialResolver.resolve(definition);
        attachSitePreview(result, siteBundle);
        return new ResolvedBuildingDefinition(
            definition,
            massing,
            siteBundle.analysis(),
            siteBundle.site(),
            materials,
            siteBundle.columnSamples());
    }

    private static void attachSitePreview(
            BuildingGenerationResult result,
            GenerationSiteResolver.SiteResolveBundle siteBundle) {
        if (result == null || siteBundle == null) {
            return;
        }
        var site = siteBundle.site();
        var analysis = siteBundle.analysis();
        BuildingFoundationUtils.EarthworkEstimate estimate =
            BuildingFoundationUtils.estimateEarthwork(
                siteBundle.groundElevations(),
                site.actualFoundationElevation());
        int cut = estimate.cut();
        int fill = estimate.fill();
        if (siteBundle.groundElevations().isEmpty()) {
            cut = analysis.estimatedCutVolume();
            fill = analysis.estimatedFillVolume();
        }
        result.sitePreview = new BuildingGenerationResult.SitePreviewSummary(
            site.actualFoundationElevation(),
            site.source(),
            site.waterAdjusted(),
            analysis.minGroundElevation(),
            analysis.maxGroundElevation(),
            analysis.waterCoverageRatio(),
            cut,
            fill);
    }

    public static ResolvedBuildingDefinition resolveForTesting(
            BuildingDefinition definition,
            BuildingGenerationResult result) {
        MassingGeometryResolver.ResolvedMassingGeometry massing =
            MassingGeometryResolver.resolve(definition, result);
        return new ResolvedBuildingDefinition(
            definition,
            massing,
            BuildingSiteAnalysis.emptyFallback(
                com.plot.core.terrain.EngineeringTerrainService.DEFAULT_GROUND_ELEVATION),
            GenerationSiteResolver.resolveForTesting(definition),
            MaterialResolver.resolve(definition),
            Map.of()
        );
    }

    private static BuildingGenerationContext fromDefinition(
            BuildingDefinition definition,
            BuildingFootprint footprint,
            World world,
            ICoordinateService coordinateService,
            IBlockProjectionService projectionService,
            BuildingGenerationResult result) {
        ResolvedBuildingDefinition resolved = resolve(
            definition, footprint, world, coordinateService, result);
        return BuildingGenerationContext.fromResolved(
            footprint, definition, world, coordinateService, projectionService, result, resolved);
    }
}
