package com.plot.plugin.building.generation.resolve;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.terrain.EngineeringTerrainService;
import com.plot.plugin.building.BuildingFoundationUtils;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.site.BuildingSiteAnalysis;
import com.plot.plugin.building.site.BuildingSiteAnalyzer;
import com.plot.plugin.building.site.BuildingSiteColumnSample;
import com.plot.plugin.building.site.BuildingSiteElevationResolver;
import com.plot.plugin.building.site.SiteIssue;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;

/**
 * 场地标高决策：在 {@link BuildingSiteAnalyzer} 观察结果之上选择 actualFoundationElevation。
 * <p>
 * 标高 ownership 三分开：
 * <ul>
 *   <li>{@code requestedBaseElevation} — 手动 ±0</li>
 *   <li>{@code resolvedPadElevation} — 土方垫层</li>
 *   <li>{@code actualFoundationElevation} — 生成实际地基标高</li>
 * </ul>
 * 优先级：manual &gt; earthwork pad &gt; auto terrain（balanced）。
 * 仅 Auto Terrain 可因水域被抬高；manual/pad 低于水面只 warning。
 */
public final class GenerationSiteResolver {
    private GenerationSiteResolver() {
    }

    public record ResolvedSiteElevation(
            Integer requestedBaseElevation,
            Integer resolvedPadElevation,
            Integer terrainSampledElevation,
            int actualFoundationElevation,
            FoundationElevationSource source,
            boolean waterAdjusted) {

        public ResolvedSiteElevation(
                Integer requestedBaseElevation,
                Integer resolvedPadElevation,
                Integer terrainSampledElevation,
                int actualFoundationElevation,
                FoundationElevationSource source) {
            this(
                requestedBaseElevation,
                resolvedPadElevation,
                terrainSampledElevation,
                actualFoundationElevation,
                source,
                false);
        }

        /** @deprecated 使用 {@link #actualFoundationElevation()} */
        @Deprecated
        public int baseElevation() {
            return actualFoundationElevation;
        }

        /** @deprecated 使用 {@link #resolvedPadElevation()} */
        @Deprecated
        public Integer earthworkPadElevation() {
            return resolvedPadElevation;
        }

        /** @deprecated 使用 {@code source == EARTHWORK_PAD} */
        @Deprecated
        public boolean usedEarthworkPad() {
            return source == FoundationElevationSource.EARTHWORK_PAD;
        }
    }

    /**
     * 生产路径：分析场地 → 决策标高 → 写入 warnings。
     */
    public static SiteResolveBundle resolve(
            BuildingDefinition definition,
            BuildingFootprint footprint,
            MassingGeometryResolver.ResolvedMassingGeometry massing,
            World world,
            ICoordinateService coordinateService,
            BuildingGenerationResult result) {
        BuildingSiteAnalyzer.AnalysisBundle bundle =
            BuildingSiteAnalyzer.analyze(massing, world, coordinateService);
        ResolvedSiteElevation site = resolveWithAnalysis(
            definition,
            footprint,
            massing,
            bundle.analysis(),
            bundle.groundElevations(),
            result);
        return new SiteResolveBundle(site, bundle.analysis(), bundle.columnSamples());
    }

    /**
     * 在已有 analysis 上决策（测试友好）。
     */
    public static ResolvedSiteElevation resolveWithAnalysis(
            BuildingDefinition definition,
            BuildingFootprint footprint,
            MassingGeometryResolver.ResolvedMassingGeometry massing,
            BuildingSiteAnalysis analysis,
            List<Integer> groundElevations,
            BuildingGenerationResult result) {
        BuildingSiteAnalysis siteAnalysis = analysis != null
            ? analysis
            : BuildingSiteAnalysis.emptyFallback(EngineeringTerrainService.DEFAULT_GROUND_ELEVATION);

        Integer requested = definition != null ? definition.foundation().manualBaseElevation() : null;

        Integer resolvedPad = null;
        if (footprint != null) {
            resolvedPad = BuildingSiteElevationResolver.resolveEarthworkPadElevation(footprint);
        } else if (definition != null) {
            List<Vec2d> outer = massing != null ? massing.outerPoints() : definition.footprint().outerPoints();
            resolvedPad = BuildingSiteElevationResolver.resolveEarthworkPadElevation(
                definition.footprint().id(), outer);
        }

        return decide(requested, resolvedPad, siteAnalysis, groundElevations, result);
    }

    /**
     * 纯决策逻辑（测试可直接注入 pad / analysis）。
     */
    public static ResolvedSiteElevation decide(
            Integer requested,
            Integer resolvedPad,
            BuildingSiteAnalysis analysis,
            List<Integer> groundElevations,
            BuildingGenerationResult result) {
        BuildingSiteAnalysis siteAnalysis = analysis != null
            ? analysis
            : BuildingSiteAnalysis.emptyFallback(EngineeringTerrainService.DEFAULT_GROUND_ELEVATION);

        int terrainElevation = siteAnalysis.sampledColumnCount() > 0
            ? siteAnalysis.balancedGroundElevation()
            : EngineeringTerrainService.DEFAULT_GROUND_ELEVATION;
        Integer terrain = siteAnalysis.sampledColumnCount() > 0 ? terrainElevation : null;

        int candidate;
        FoundationElevationSource source;
        if (requested != null) {
            candidate = requested;
            source = FoundationElevationSource.MANUAL;
        } else if (resolvedPad != null) {
            candidate = resolvedPad;
            source = FoundationElevationSource.EARTHWORK_PAD;
            addWarning(result, "plugin.building.warn.using_earthwork_pad_elevation");
        } else {
            candidate = terrainElevation;
            source = FoundationElevationSource.TERRAIN;
        }

        boolean waterAdjusted = false;
        Integer dominantWater = siteAnalysis.dominantWaterElevation();
        if (source == FoundationElevationSource.TERRAIN
                && siteAnalysis.waterCoverageRatio() >= BuildingSiteAnalyzer.WATER_DOMINANT_THRESHOLD
                && dominantWater != null) {
            int minimumDry = dominantWater + 1;
            if (candidate < minimumDry) {
                candidate = minimumDry;
                waterAdjusted = true;
            }
        }

        emitSiteWarnings(
            siteAnalysis, source, candidate, waterAdjusted, dominantWater, groundElevations, result);

        return new ResolvedSiteElevation(
            requested, resolvedPad, terrain, candidate, source, waterAdjusted);
    }

    /**
     * 测试路径：无 World；仅手动 / 默认地形。
     */
    public static ResolvedSiteElevation resolveForTesting(BuildingDefinition definition) {
        Integer requested = definition != null ? definition.foundation().manualBaseElevation() : null;
        int actual = BuildingFoundationUtils.computeBaseElevation(List.of(), requested);
        FoundationElevationSource source = requested != null
            ? FoundationElevationSource.MANUAL
            : FoundationElevationSource.TERRAIN;
        Integer terrain = requested == null ? actual : null;
        return new ResolvedSiteElevation(requested, null, terrain, actual, source, false);
    }

    public static int sampleTopHeight(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return EngineeringTerrainService.DEFAULT_GROUND_ELEVATION;
        }
        return EngineeringTerrainService.of(world).sampleGroundSurface(pos.getX(), pos.getZ());
    }

    static void emitSiteWarnings(
            BuildingSiteAnalysis analysis,
            FoundationElevationSource source,
            int actualElevation,
            boolean waterAdjusted,
            Integer dominantWater,
            List<Integer> groundElevations,
            BuildingGenerationResult result) {
        if (result == null || analysis == null) {
            return;
        }
        if (analysis.hasIssue(SiteIssue.PARTIAL_WATER)) {
            addWarning(result, "plugin.building.warn.partial_water_site");
        }
        if (analysis.hasIssue(SiteIssue.WATER_DOMINANT)) {
            addWarning(result, "plugin.building.warn.water_site");
        }
        if (waterAdjusted) {
            addWarning(result, "plugin.building.warn.foundation_raised_above_water");
        }
        if (dominantWater != null && actualElevation <= dominantWater) {
            if (source == FoundationElevationSource.MANUAL) {
                addWarning(result, "plugin.building.warn.manual_below_water");
            } else if (source == FoundationElevationSource.EARTHWORK_PAD) {
                addWarning(result, "plugin.building.warn.earthwork_pad_below_water");
            }
        }
        if (analysis.hasIssue(SiteIssue.SEVERE_STEEP)) {
            addWarning(result, "plugin.building.warn.severe_steep_site");
        } else if (analysis.hasIssue(SiteIssue.STEEP)) {
            addWarning(result, "plugin.building.warn.steep_site");
        }
        if (analysis.hasIssue(SiteIssue.STRUCTURE_CONFLICT)) {
            addWarning(result, "plugin.building.warn.structure_conflict");
        }

        if (groundElevations != null && !groundElevations.isEmpty()) {
            warnHeavyEarthworkIfNeeded(groundElevations, actualElevation, result);
        } else if (analysis.hasIssue(SiteIssue.HEAVY_EARTHWORK)) {
            addWarning(result, "plugin.building.warn.heavy_earthwork");
        }
    }

    private static void addWarning(BuildingGenerationResult result, String key) {
        if (result != null && key != null && !result.warnings.contains(key)) {
            result.warnings.add(key);
        }
    }

    /**
     * resolve 生产路径返回值：标高 + analysis + 列缓存。
     */
    public record SiteResolveBundle(
            ResolvedSiteElevation site,
            BuildingSiteAnalysis analysis,
            Map<Long, BuildingSiteColumnSample> columnSamples) {

        public SiteResolveBundle {
            analysis = analysis != null
                ? analysis
                : BuildingSiteAnalysis.emptyFallback(EngineeringTerrainService.DEFAULT_GROUND_ELEVATION);
            columnSamples = columnSamples == null ? Map.of() : Map.copyOf(columnSamples);
        }
    }

    /**
     * 用地面样本列表重估切填（供测试与精确 heavy-earthwork）。
     */
    public static void warnHeavyEarthworkIfNeeded(
            List<Integer> groundSamples,
            int actualElevation,
            BuildingGenerationResult result) {
        if (groundSamples == null || groundSamples.isEmpty() || result == null) {
            return;
        }
        BuildingFoundationUtils.EarthworkEstimate estimate =
            BuildingFoundationUtils.estimateEarthwork(groundSamples, actualElevation);
        if (estimate.total() > groundSamples.size() * BuildingSiteAnalyzer.HEAVY_EARTHWORK_CELLS_FACTOR) {
            addWarning(result, "plugin.building.warn.heavy_earthwork");
        }
    }
}
