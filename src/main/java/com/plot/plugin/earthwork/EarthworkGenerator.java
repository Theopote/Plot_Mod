package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.ZoneEdgeSettings;
import com.plot.plugin.earthwork.pipeline.DefaultSiteEarthworkOperations;
import com.plot.plugin.earthwork.pipeline.EarthworkPipelineContext;
import com.plot.plugin.earthwork.pipeline.LegacyRegionPipeline;
import com.plot.plugin.earthwork.pipeline.SiteEarthworkPipeline;
import com.plot.plugin.earthwork.volume.EarthworkVolumeCalculator;
import com.plot.plugin.earthwork.voxel.EarthworkVoxelizer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 土方整平生成器。
 * <p>
 * 场地级生成委托 {@link com.plot.plugin.earthwork.pipeline.SiteEarthworkPipeline}；
 * 单分区 {@link #generate(GradingRegion, World, TerrainSnapshot, ZoneEdgeSettings)} 委托
 * {@link LegacyRegionPipeline}。
 */
public class EarthworkGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/EarthworkGenerator");

    /**
     * 测试用：按坐标采样方块 ID，优先于 {@link World} 读取。
     */
    @FunctionalInterface
    public interface BlockSampler {
        String sampleBlockId(BlockPos pos);
    }

    private final ICoordinateService coordinateTransformer;
    private final BlockSampler blockSampler;
    private final SiteEarthworkPipeline sitePipeline;
    private final LegacyRegionPipeline legacyRegionPipeline;
    private final EarthworkVoxelizer voxelizer;
    private final EarthworkVolumeCalculator volumeCalculator;

    public EarthworkGenerator(ICoordinateService coordinateTransformer) {
        this(coordinateTransformer, null);
    }

    EarthworkGenerator(ICoordinateService coordinateTransformer, BlockSampler blockSampler) {
        this.coordinateTransformer = coordinateTransformer;
        this.blockSampler = blockSampler;
        this.voxelizer = new EarthworkVoxelizer(blockSampler);
        this.volumeCalculator = new EarthworkVolumeCalculator(voxelizer);
        this.legacyRegionPipeline = new LegacyRegionPipeline(coordinateTransformer, volumeCalculator);
        this.sitePipeline = new SiteEarthworkPipeline(new DefaultSiteEarthworkOperations(
            coordinateTransformer, volumeCalculator, legacyRegionPipeline));
    }

    public EarthworkGenerator withBlockSampler(BlockSampler blockSampler) {
        return new EarthworkGenerator(coordinateTransformer, blockSampler);
    }

    public enum ChangeType {
        CUT, FILL
    }

    public static class GridSample {
        public final Vec2d center;
        public final int groundY;
        public final ChangeType changeType;

        public GridSample(Vec2d center, int groundY, ChangeType changeType) {
            this.center = center;
            this.groundY = groundY;
            this.changeType = changeType;
        }
    }

    public static class EarthworkGenerationResult {
        public TerrainSnapshot existingTerrainSnapshot = TerrainSnapshot.empty();
        public final Map<BlockPos, BlockRecord> placementRecords = new LinkedHashMap<>();
        public final Map<BlockPos, ChangeType> changeTypes = new LinkedHashMap<>();
        public final List<GridSample> gridSamples = new ArrayList<>();
        public EarthworkVolumeReport volumeReport = EarthworkVolumeReport.empty();
        public SiteEarthworkReport siteVolumeReport = SiteEarthworkReport.empty();
        public EarthworkProjectReport projectReport = EarthworkProjectReport.empty();
        public DesignTerrainGrid designTerrainGrid;
        public int resolvedElevation;
        public int resolvedElevationMin;
        public int resolvedElevationMax;
        public boolean slopedSurface;
        public boolean siteGeneration;
        public final List<String> warnings = new ArrayList<>();
        public int calculationCellCount;
    }

    public EarthworkGenerationResult generate(GradingRegion region, World world) {
        return generate(region, world, null);
    }

    public EarthworkGenerationResult generate(
            GradingRegion region,
            World world,
            TerrainSnapshot terrainSnapshot) {
        return generate(region, world, terrainSnapshot, null);
    }

    public EarthworkGenerationResult generate(
            GradingRegion region,
            World world,
            TerrainSnapshot terrainSnapshot,
            ZoneEdgeSettings edgeSettings) {
        return legacyRegionPipeline.execute(region, world, terrainSnapshot, edgeSettings);
    }

    public EarthworkGenerationResult generateSite(EarthworkSite site, World world) {
        return generateSite(site, world, null, null, BuildingFootprintLookup.NONE);
    }

    public EarthworkGenerationResult generateSite(
            EarthworkSite site,
            World world,
            TerrainSnapshot terrainSnapshot,
            GradingRegion previewRegion) {
        return generateSite(site, world, terrainSnapshot, previewRegion, BuildingFootprintLookup.NONE);
    }

    public EarthworkGenerationResult generateSite(
            EarthworkSite site,
            World world,
            TerrainSnapshot terrainSnapshot,
            GradingRegion previewRegion,
            BuildingFootprintLookup buildingLookup) {
        return generateSite(site, world, terrainSnapshot, previewRegion, buildingLookup, RoadSurfaceLookup.NONE);
    }

    public EarthworkGenerationResult generateSite(
            EarthworkSite site,
            World world,
            TerrainSnapshot terrainSnapshot,
            GradingRegion previewRegion,
            BuildingFootprintLookup buildingLookup,
            RoadSurfaceLookup roadLookup) {
        return sitePipeline.execute(EarthworkPipelineContext.of(
            site, world, terrainSnapshot, previewRegion, buildingLookup, roadLookup));
    }

    SiteEarthworkPipeline sitePipeline() {
        return sitePipeline;
    }

    static boolean shouldApplyBlockChange(String previousBlockId, String newBlockId) {
        return EarthworkVoxelizer.shouldApplyBlockChange(previousBlockId, newBlockId);
    }

    static String normalizeBlockId(String blockId) {
        return EarthworkVoxelizer.normalizeBlockId(blockId);
    }
}
