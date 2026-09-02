package com.plot.plugin.earthwork;

import com.plot.api.world.ICoordinateService;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.ZoneEdgeSettings;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.pipeline.EarthworkPipelineContext;
import com.plot.plugin.earthwork.pipeline.EarthworkPipelines;
import com.plot.plugin.earthwork.pipeline.LegacyRegionPipeline;
import com.plot.plugin.earthwork.pipeline.SiteEarthworkPipeline;
import com.plot.plugin.earthwork.voxel.EarthworkVoxelizer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 土方整平生成器（已弃用）。
 * <p>
 * 请改用 {@link EarthworkPipelines#create(ICoordinateService)} 获取
 * {@link SiteEarthworkPipeline} / {@link LegacyRegionPipeline}。
 *
 * @deprecated 2.0 起由 {@code pipeline/*} 承担；本类保留兼容，后续版本移除。
 */
@Deprecated
public class EarthworkGenerator {

    /**
     * @deprecated 使用 {@link EarthworkVoxelizer.BlockSampler}。
     */
    @Deprecated
    @FunctionalInterface
    public interface BlockSampler {
        String sampleBlockId(BlockPos pos);
    }

    private final ICoordinateService coordinateService;
    private final EarthworkPipelines.Bundle pipelines;

    public EarthworkGenerator(ICoordinateService coordinateTransformer) {
        this(coordinateTransformer, null);
    }

    EarthworkGenerator(ICoordinateService coordinateTransformer, BlockSampler blockSampler) {
        this.coordinateService = coordinateTransformer;
        EarthworkVoxelizer.BlockSampler adapter = blockSampler != null ? blockSampler::sampleBlockId : null;
        this.pipelines = EarthworkPipelines.create(coordinateTransformer, adapter);
    }

    public EarthworkGenerator withBlockSampler(BlockSampler blockSampler) {
        return new EarthworkGenerator(coordinateService, blockSampler);
    }

    @Deprecated
    public EarthworkGenerationResult generate(
            GradingRegion region,
            World world,
            TerrainSnapshot terrainSnapshot,
            ZoneEdgeSettings edgeSettings) {
        return pipelines.legacy().execute(region, world, terrainSnapshot, edgeSettings);
    }

    @Deprecated
    public EarthworkGenerationResult generate(
            GradingRegion region,
            World world,
            TerrainSnapshot terrainSnapshot) {
        return generate(region, world, terrainSnapshot, null);
    }

    @Deprecated
    public EarthworkGenerationResult generate(GradingRegion region, World world) {
        return generate(region, world, null);
    }

    @Deprecated
    public EarthworkGenerationResult generateSite(
            EarthworkSite site,
            World world,
            TerrainSnapshot terrainSnapshot,
            GradingRegion previewRegion,
            BuildingFootprintLookup buildingLookup,
            RoadSurfaceLookup roadLookup) {
        return pipelines.site().execute(EarthworkPipelineContext.of(
            site, world, terrainSnapshot, previewRegion, buildingLookup, roadLookup));
    }

    @Deprecated
    public EarthworkGenerationResult generateSite(
            EarthworkSite site,
            World world,
            TerrainSnapshot terrainSnapshot,
            GradingRegion previewRegion,
            BuildingFootprintLookup buildingLookup) {
        return generateSite(site, world, terrainSnapshot, previewRegion, buildingLookup, RoadSurfaceLookup.NONE);
    }

    @Deprecated
    public EarthworkGenerationResult generateSite(
            EarthworkSite site,
            World world,
            TerrainSnapshot terrainSnapshot,
            GradingRegion previewRegion) {
        return generateSite(site, world, terrainSnapshot, previewRegion, BuildingFootprintLookup.NONE);
    }

    @Deprecated
    public EarthworkGenerationResult generateSite(EarthworkSite site, World world) {
        return generateSite(site, world, null, null);
    }

    @Deprecated
    SiteEarthworkPipeline sitePipeline() {
        return pipelines.site();
    }

    static boolean shouldApplyBlockChange(String previousBlockId, String newBlockId) {
        return EarthworkVoxelizer.shouldApplyBlockChange(previousBlockId, newBlockId);
    }

    static String normalizeBlockId(String blockId) {
        return EarthworkVoxelizer.normalizeBlockId(blockId);
    }
}
