package com.plot.plugin.building.site;

import com.plot.api.world.ICoordinateService;
import com.plot.core.terrain.EngineeringTerrainBlockRole;
import com.plot.core.terrain.EngineeringTerrainService;
import com.plot.plugin.building.BuildingFoundationUtils;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.generation.BuildingGenerationContext.GridCell;
import com.plot.plugin.building.generation.resolve.MassingGeometryResolver;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * 观察世界：对 footprint 列采样，产出 {@link BuildingSiteAnalysis}。
 * <p>
 * 不决定最终标高（由 GenerationSiteResolver），不改地形。
 */
public final class BuildingSiteAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/BuildingSiteAnalyzer");

    public static final double PARTIAL_WATER_THRESHOLD = 0.10;
    public static final double WATER_DOMINANT_THRESHOLD = 0.50;
    public static final int STEEP_WARNING_THRESHOLD = 4;
    public static final int SEVERE_STEEP_THRESHOLD = 8;
    public static final int HEAVY_EARTHWORK_CELLS_FACTOR = 4;

    private BuildingSiteAnalyzer() {
    }

    /**
     * 生产路径：采样 + 汇总。返回 analysis 与列缓存（供 SitePreparation / Foundation 复用）。
     */
    public static AnalysisBundle analyze(
            MassingGeometryResolver.ResolvedMassingGeometry massing,
            World world,
            ICoordinateService coordinateService) {
        if (massing == null || !massing.valid() || world == null || coordinateService == null) {
            return AnalysisBundle.fallback();
        }
        try {
            Map<Long, BuildingSiteColumnSample> cache = new LinkedHashMap<>();
            List<BuildingSiteColumnSample> samples = new ArrayList<>();
            for (GridCell cell : massing.footprintCells()) {
                BlockPos column = BuildingGeometryUtils.canvasToBlockXZ(cell.center(), coordinateService);
                long key = packColumn(column.getX(), column.getZ());
                BuildingSiteColumnSample sample = cache.computeIfAbsent(
                    key,
                    ignored -> sampleColumn(world, column.getX(), column.getZ()));
                samples.add(sample);
            }
            SampledAnalysis sampled = analyzeSamplesInternal(samples, TerrainElevationStrategy.BALANCED);
            return new AnalysisBundle(sampled.analysis(), Map.copyOf(cache), sampled.groundElevations());
        } catch (Exception e) {
            LOGGER.warn("场地分析失败，回退默认标高: {}", e.getMessage());
            return AnalysisBundle.fallback();
        }
    }

    /**
     * 测试 / 纯函数路径：由已有列样本汇总。
     */
    public static BuildingSiteAnalysis analyzeSamples(
            List<BuildingSiteColumnSample> samples,
            TerrainElevationStrategy strategy) {
        return analyzeSamplesInternal(samples, strategy).analysis();
    }

    private static SampledAnalysis analyzeSamplesInternal(
            List<BuildingSiteColumnSample> samples,
            TerrainElevationStrategy strategy) {
        if (samples == null || samples.isEmpty()) {
            return new SampledAnalysis(
                BuildingSiteAnalysis.emptyFallback(EngineeringTerrainService.DEFAULT_GROUND_ELEVATION),
                List.of());
        }

        List<Integer> grounds = new ArrayList<>(samples.size());
        List<Integer> waterYs = new ArrayList<>();
        int waterColumns = 0;
        int natural = 0;
        int structures = 0;
        int minG = Integer.MAX_VALUE;
        int maxG = Integer.MIN_VALUE;

        for (BuildingSiteColumnSample sample : samples) {
            if (sample == null) {
                continue;
            }
            grounds.add(sample.groundY());
            minG = Math.min(minG, sample.groundY());
            maxG = Math.max(maxG, sample.groundY());
            natural += sample.naturalDecorationCount();
            structures += sample.structureConflictCount();
            if (sample.hasWater()) {
                waterColumns++;
                waterYs.add(sample.waterSurfaceY().getAsInt());
            }
        }
        if (grounds.isEmpty()) {
            return new SampledAnalysis(
                BuildingSiteAnalysis.emptyFallback(EngineeringTerrainService.DEFAULT_GROUND_ELEVATION),
                List.of());
        }

        int dominant = BuildingFoundationUtils.computeDominantElevation(grounds);
        int median = BuildingFoundationUtils.computeMedianElevation(grounds);
        int balanced = BuildingFoundationUtils.computeBalancedElevation(grounds);
        int terrainElevation = switch (strategy != null ? strategy : TerrainElevationStrategy.BALANCED) {
            case DOMINANT -> dominant;
            case MEDIAN -> median;
            case BALANCED -> balanced;
        };

        int range = maxG - minG;
        double waterRatio = (double) waterColumns / grounds.size();
        Integer dominantWater = waterYs.isEmpty()
            ? null
            : BuildingFoundationUtils.computeDominantElevation(waterYs);
        Integer maxWater = waterYs.isEmpty()
            ? null
            : waterYs.stream().mapToInt(Integer::intValue).max().orElseThrow();

        BuildingFoundationUtils.EarthworkEstimate estimate =
            BuildingFoundationUtils.estimateEarthwork(grounds, terrainElevation);

        EnumSet<SiteIssue> issues = EnumSet.noneOf(SiteIssue.class);
        if (waterRatio >= WATER_DOMINANT_THRESHOLD) {
            issues.add(SiteIssue.WATER_DOMINANT);
        } else if (waterRatio >= PARTIAL_WATER_THRESHOLD) {
            issues.add(SiteIssue.PARTIAL_WATER);
        }
        if (range >= SEVERE_STEEP_THRESHOLD) {
            issues.add(SiteIssue.SEVERE_STEEP);
        } else if (range >= STEEP_WARNING_THRESHOLD) {
            issues.add(SiteIssue.STEEP);
        }
        if (structures > 0) {
            issues.add(SiteIssue.STRUCTURE_CONFLICT);
        }
        if (estimate.total() > grounds.size() * HEAVY_EARTHWORK_CELLS_FACTOR) {
            issues.add(SiteIssue.HEAVY_EARTHWORK);
        }

        BuildingSiteAnalysis analysis = new BuildingSiteAnalysis(
            grounds.size(),
            minG,
            maxG,
            dominant,
            median,
            balanced,
            range,
            waterColumns,
            waterRatio,
            dominantWater,
            maxWater,
            natural,
            structures,
            estimate.cut(),
            estimate.fill(),
            issues);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                "site analysis: columns={} groundRange={}-{} balanced={} waterCoverage={} conflicts={}",
                analysis.sampledColumnCount(),
                analysis.minGroundElevation(),
                analysis.maxGroundElevation(),
                analysis.balancedGroundElevation(),
                String.format("%.2f", analysis.waterCoverageRatio()),
                analysis.structureConflictCount());
        }
        return new SampledAnalysis(analysis, List.copyOf(grounds));
    }

    private record SampledAnalysis(BuildingSiteAnalysis analysis, List<Integer> groundElevations) {
    }

    public static BuildingSiteColumnSample sampleColumn(World world, int worldX, int worldZ) {
        EngineeringTerrainService terrain = EngineeringTerrainService.of(world);
        int groundY = terrain.sampleGroundSurface(worldX, worldZ);
        int rawY = terrain.sampleRawSurface(worldX, worldZ);
        OptionalInt waterY = terrain.findWaterSurface(worldX, worldZ);

        int natural = 0;
        int structures = 0;
        int top = Math.max(rawY, groundY + 2);
        for (int y = groundY + 1; y <= top; y++) {
            if (!terrain.isChunkLoaded(worldX, worldZ)) {
                break;
            }
            BlockState state = world.getBlockState(new BlockPos(worldX, y, worldZ));
            EngineeringTerrainBlockRole role = EngineeringTerrainService.classifyBlock(state);
            if (role == EngineeringTerrainBlockRole.NATURAL_DECORATION) {
                natural++;
            } else if (role == EngineeringTerrainBlockRole.OTHER_SOLID) {
                structures++;
            }
        }
        return new BuildingSiteColumnSample(groundY, rawY, waterY, natural, structures);
    }

    public static long packColumn(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    /**
     * 分析产物：汇总 + 列缓存 + 地面高度列表（供标高决策复用）。
     */
    public record AnalysisBundle(
            BuildingSiteAnalysis analysis,
            Map<Long, BuildingSiteColumnSample> columnSamples,
            List<Integer> groundElevations) {

        public AnalysisBundle {
            analysis = analysis != null
                ? analysis
                : BuildingSiteAnalysis.emptyFallback(EngineeringTerrainService.DEFAULT_GROUND_ELEVATION);
            columnSamples = columnSamples == null || columnSamples.isEmpty()
                ? Map.of()
                : Map.copyOf(columnSamples);
            groundElevations = groundElevations == null || groundElevations.isEmpty()
                ? List.of()
                : List.copyOf(groundElevations);
        }

        public static AnalysisBundle fallback() {
            return new AnalysisBundle(
                BuildingSiteAnalysis.emptyFallback(EngineeringTerrainService.DEFAULT_GROUND_ELEVATION),
                Map.of(),
                List.of());
        }

        public static AnalysisBundle of(BuildingSiteAnalysis analysis, List<Integer> groundElevations) {
            return new AnalysisBundle(analysis, Map.of(), groundElevations);
        }
    }
}
