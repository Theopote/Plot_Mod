package com.plot.plugin.earthwork.manager;

import com.plot.api.world.GhostBlockOwners;
import com.plot.api.world.IGhostBlockService;
import com.plot.core.command.BlockRecord;
import com.plot.core.context.PluginContext;
import com.plot.plugin.earthwork.design.BuildingFootprintLookup;
import com.plot.plugin.earthwork.design.InsufficientTerrainSamplesException;
import com.plot.plugin.earthwork.design.BuildingFootprintResolver;
import com.plot.plugin.earthwork.design.RoadSurfaceLookup;
import com.plot.plugin.earthwork.design.RoadCorridorSurfaceResolver;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.EarthworkWorkMode;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.pipeline.EarthworkPipelines;
import com.plot.plugin.earthwork.pipeline.SiteEarthworkPipeline;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.plugin.earthwork.terrain.TerrainSnapshotCache;
import com.plot.plugin.earthwork.validation.EarthworkValidationReport;
import com.plot.plugin.earthwork.validation.EarthworkValidator;
import com.plot.plugin.earthwork.volume.EarthworkProjectReport;
import com.plot.plugin.earthwork.volume.EarthworkReportExporter;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import com.plot.utils.PlotI18n;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 土方预览生成、虚影投影与结果缓存（对标 {@link com.plot.plugin.road.manager.RoadPreviewManager}）。
 */
public final class EarthworkPreviewManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/EarthworkPreview");

    public static final String CUT_GHOST_BLOCK = "minecraft:red_stained_glass";
    public static final String FILL_GHOST_BLOCK = "minecraft:light_blue_stained_glass";
    public static final String STRUCTURE_GHOST_BLOCK = "minecraft:gray_stained_glass";

    public record GhostProjectionSummary(int shown, int total, int stride) {
        public boolean simplified() {
            return stride > 1;
        }
    }

    private final PluginContext host;
    private final SiteEarthworkPipeline sitePipeline;
    private final TerrainSnapshotCache terrainCache;
    private final Consumer<String> statusSink;
    private volatile com.plot.plugin.earthwork.model.EarthworkStatusLevel lastStatusLevel =
        com.plot.plugin.earthwork.model.EarthworkStatusLevel.INFO;

    public com.plot.plugin.earthwork.model.EarthworkStatusLevel lastStatusLevel() {
        return lastStatusLevel;
    }

    private void publishStatus(com.plot.plugin.earthwork.model.EarthworkStatusLevel level, String message) {
        lastStatusLevel = level;
        statusSink.accept(message);
    }

    private volatile EarthworkGenerationResult lastGenerationResult;
    private volatile EarthworkValidationReport lastValidationReport = EarthworkValidationReport.empty();
    private volatile EarthworkPreviewState previewState = EarthworkPreviewState.NONE;
    /** 递增令牌：参数变更后使在途计算结果作废。 */
    private final java.util.concurrent.atomic.AtomicLong calculationGeneration =
        new java.util.concurrent.atomic.AtomicLong(0L);

    /** 大场地全量对比可达数十万格；UI 每帧调用会卡死渲染线程，必须缓存。 */
    private static final long TERRAIN_COMPARE_CACHE_TTL_MS = 5_000L;
    private volatile TerrainSnapshot.ComparisonResult cachedTerrainComparison;
    private volatile long cachedTerrainComparisonAtMs;
    private volatile EarthworkGenerationResult cachedTerrainComparisonSource;
    private volatile String cachedTerrainComparisonWorldKey;

    public EarthworkValidationReport getLastValidationReport() {
        return lastValidationReport;
    }

    public EarthworkPreviewState getPreviewState() {
        return previewState;
    }

    public long getPreviewGeneration() {
        return calculationGeneration.get();
    }

    public EarthworkPreviewManager(
            PluginContext host,
            EarthworkPipelines.Bundle pipelines,
            TerrainSnapshotCache terrainCache,
            Consumer<String> statusSink) {
        this(host, pipelines.site(), terrainCache, statusSink);
    }

    public EarthworkPreviewManager(
            PluginContext host,
            SiteEarthworkPipeline sitePipeline,
            TerrainSnapshotCache terrainCache,
            Consumer<String> statusSink) {
        this.host = Objects.requireNonNull(host, "host");
        this.sitePipeline = Objects.requireNonNull(sitePipeline, "sitePipeline");
        this.terrainCache = Objects.requireNonNull(terrainCache, "terrainCache");
        this.statusSink = statusSink != null ? statusSink : msg -> {};
    }

    /** 单测：注入已生成的预览结果，验证 Build 与 Preview 使用同一份 placement 集。 */
    static EarthworkPreviewManager withGenerationResult(
            PluginContext host,
            SiteEarthworkPipeline sitePipeline,
            TerrainSnapshotCache terrainCache,
            Consumer<String> statusSink,
            EarthworkGenerationResult generationResult) {
        EarthworkPreviewManager manager = new EarthworkPreviewManager(
            host, sitePipeline, terrainCache, statusSink);
        manager.lastGenerationResult = generationResult;
        manager.previewState = generationResult != null && !generationResult.placementRecords.isEmpty()
            ? EarthworkPreviewState.VALID
            : EarthworkPreviewState.NONE;
        return manager;
    }

    public EarthworkGenerationResult getLastGenerationResult() {
        return lastGenerationResult;
    }

    /** 有可展示的缓存（含 DIRTY 旧图），不代表可施工。 */
    public boolean hasCachedPreview() {
        return lastGenerationResult != null && previewState.hasCachedResult();
    }

    /** 可安全施工的预览。 */
    public boolean hasValidPreview() {
        EarthworkGenerationResult result = lastGenerationResult;
        return previewState.allowsBuild()
            && result != null
            && !result.placementRecords.isEmpty();
    }

    /**
     * 参数变更时立即标记 DIRTY：保留旧图参考，但撤销施工资格。
     */
    public void markDirty() {
        calculationGeneration.incrementAndGet();
        if (lastGenerationResult != null) {
            boolean wasBuildable = previewState.allowsBuild();
            previewState = EarthworkPreviewState.DIRTY;
            if (wasBuildable) {
                publishStatus(com.plot.plugin.earthwork.model.EarthworkStatusLevel.WARNING,
                    PlotI18n.tr("plugin.earthwork.preview_invalidated"));
            }
        } else if (previewState != EarthworkPreviewState.NONE
            && previewState != EarthworkPreviewState.DIRTY) {
            previewState = EarthworkPreviewState.NONE;
        }
    }

    public void markCalculating() {
        previewState = EarthworkPreviewState.CALCULATING;
    }

    public boolean calculatePreview(
            EarthworkProject project,
            GradingRegion region,
            BuildingFootprintLookup buildingLookup,
            RoadSurfaceLookup roadLookup) {
        return calculatePreview(project, region, buildingLookup, roadLookup, EarthworkWorkMode.QUICK);
    }

    public boolean calculatePreview(
            EarthworkProject project,
            GradingRegion region,
            BuildingFootprintLookup buildingLookup,
            RoadSurfaceLookup roadLookup,
            EarthworkWorkMode workMode) {
        long generation = calculationGeneration.incrementAndGet();
        previewState = EarthworkPreviewState.CALCULATING;

        World world = getClientWorld();
        if (world == null) {
            failPreview(generation, PlotI18n.tr("plugin.earthwork.generate_world_unavailable"));
            return false;
        }
        if (project == null || region == null) {
            failPreview(generation, PlotI18n.tr("plugin.earthwork.generate_empty_result"));
            return false;
        }

        EarthworkValidationReport validation = EarthworkValidator.analyzePrePreview(
            project, region, buildingLookup, roadLookup);
        lastValidationReport = validation;
        if (validation.blocksPreview()) {
            failPreview(generation, validation.firstBlockingMessage());
            return false;
        }

        long started = System.nanoTime();
        EarthworkSite site = project.getActiveSite();
        EarthworkGenerationResult computed;
        TerrainSnapshot terrain;
        try {
            terrain = terrainCache.captureFreshSite(site, world, host.coordinates());
            if (terrain.exceedsUnloadedThreshold()) {
                failPreview(
                    generation,
                    PlotI18n.tr(
                        "plugin.earthwork.unloaded_chunks_blocked",
                        terrain.unloadedColumnCount(),
                        terrain.columnCount()));
                return false;
            }
            if (terrain.loadedColumnCount() <= 0) {
                failPreview(generation, PlotI18n.tr("plugin.earthwork.no_loaded_terrain_samples"));
                return false;
            }
            computed = sitePipeline.execute(
                com.plot.plugin.earthwork.pipeline.EarthworkPipelineContext.of(
                    site, world, terrain, region, buildingLookup, roadLookup, workMode));
        } catch (BuildingFootprintResolver.UnresolvedBuildingReferenceException e) {
            LOGGER.error("土方预览被建筑引用阻断: {}", e.getMessage());
            failPreview(generation, e.getMessage());
            return false;
        } catch (RoadCorridorSurfaceResolver.UnresolvedRoadDesignSurfaceException e) {
            LOGGER.error("土方预览被道路设计面阻断: {}", e.getMessage());
            failPreview(generation, e.getMessage());
            return false;
        } catch (InsufficientTerrainSamplesException e) {
            failPreview(generation, PlotI18n.tr("plugin.earthwork.no_loaded_terrain_samples"));
            return false;
        } catch (Exception e) {
            LOGGER.error("土方预览生成失败: {}", e.getMessage(), e);
            failPreview(generation, PlotI18n.tr("plugin.earthwork.generate_empty_result"));
            return false;
        }

        if (generation != calculationGeneration.get()) {
            LOGGER.debug("丢弃过期土方预览结果 generation={}", generation);
            return false;
        }

        if (computed == null || computed.placementRecords.isEmpty()) {
            failPreview(generation, PlotI18n.tr("plugin.earthwork.generate_empty_result"));
            return false;
        }

        long designAtStart = EarthworkPreviewIdentity.designRevision(project, region);
        EarthworkPreviewIdentity.stamp(computed, project, region, world, terrain);
        if (generation != calculationGeneration.get()
            || computed.designRevision != designAtStart
            || !EarthworkPreviewIdentity.matchesDesign(computed, project, region.getId())) {
            LOGGER.debug(
                "丢弃发布前已过期的土方预览 generation={} designChanged={}",
                generation,
                computed.designRevision != designAtStart);
            return false;
        }

        enrichProjectReport(project, site, region, computed);
        computed.warnings.addAll(validation.warningKeys());

        if (generation != calculationGeneration.get()) {
            LOGGER.debug("丢弃写入前已过期的土方预览 generation={}", generation);
            return false;
        }

        lastGenerationResult = computed;
        previewState = EarthworkPreviewState.VALID;
        invalidateTerrainComparisonCache();

        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        if (elapsedMs >= 50L) {
            LOGGER.warn(
                "土方预览耗时 {} ms，cells={}, placements={}",
                elapsedMs,
                lastGenerationResult.calculationCellCount,
                lastGenerationResult.placementRecords.size());
        }
        publishStatus(com.plot.plugin.earthwork.model.EarthworkStatusLevel.SUCCESS,
            resolvePreviewReadyMessage(projectPreview()));
        return true;
    }

    private void failPreview(long generation, String message) {
        if (generation != calculationGeneration.get()) {
            return;
        }
        clearGhostsOnly();
        lastGenerationResult = null;
        previewState = EarthworkPreviewState.FAILED;
        invalidateTerrainComparisonCache();
        if (message != null && !message.isBlank()) {
            publishStatus(com.plot.plugin.earthwork.model.EarthworkStatusLevel.ERROR, message);
        }
    }

    private String resolvePreviewReadyMessage(GhostProjectionSummary ghost) {
        if (ghost != null && ghost.simplified()) {
            return PlotI18n.tr(
                "plugin.earthwork.preview_ghost_simplified",
                ghost.shown(),
                ghost.total());
        }
        return PlotI18n.tr("plugin.earthwork.generate_preview_ready");
    }

    private static void enrichProjectReport(
            EarthworkProject project,
            EarthworkSite site,
            GradingRegion region,
            EarthworkGenerationResult result) {
        if (result.siteVolumeReport.byZone().isEmpty()) {
            result.projectReport = EarthworkProjectReport.Builder.buildFromSingleZone(
                project,
                site,
                region.getId(),
                result.volumeReport,
                result.balanceBestOffset,
                result.balanceZoneVerticalOffsets);
        } else if (result.projectReport == null
            || result.projectReport == EarthworkProjectReport.empty()) {
            result.projectReport = EarthworkProjectReport.Builder.buildFromProject(
                project,
                site,
                result.siteVolumeReport,
                result.balanceBestOffset,
                result.balanceZoneVerticalOffsets);
        }
    }

    public GhostProjectionSummary projectPreview() {
        EarthworkGenerationResult result = lastGenerationResult;
        if (result == null) {
            return new GhostProjectionSummary(0, 0, 1);
        }
        IGhostBlockService ghostBlockManager = host.ghosts();
        if (ghostBlockManager == null) {
            return new GhostProjectionSummary(0, result.placementRecords.size(), 1);
        }
        ghostBlockManager.clearGhostBlocks(GhostBlockOwners.EARTHWORK);
        int total = result.placementRecords.size();
        int stride = total > 4000 ? (int) Math.ceil(total / 4000.0) : 1;
        int index = 0;
        int shown = 0;
        for (BlockRecord record : result.placementRecords.values()) {
            if ((index++ % stride) != 0) {
                continue;
            }
            shown++;
            EarthworkGenerationResult.ChangeType changeType = result.changeTypes.get(record.pos);
            String ghostBlock = ghostBlockFor(changeType);
            ghostBlockManager.addGhostBlock(GhostBlockOwners.EARTHWORK, record.pos, ghostBlock);
        }
        GhostProjectionSummary summary = new GhostProjectionSummary(shown, total, stride);
        return summary;
    }

    private static String ghostBlockFor(EarthworkGenerationResult.ChangeType changeType) {
        if (changeType == EarthworkGenerationResult.ChangeType.CUT
            || changeType == EarthworkGenerationResult.ChangeType.CLEARANCE) {
            return CUT_GHOST_BLOCK;
        }
        if (changeType == EarthworkGenerationResult.ChangeType.STRUCTURE) {
            return STRUCTURE_GHOST_BLOCK;
        }
        return FILL_GHOST_BLOCK;
    }

    public void clearPreview() {
        calculationGeneration.incrementAndGet();
        clearGhostsOnly();
        lastGenerationResult = null;
        lastValidationReport = EarthworkValidationReport.empty();
        previewState = EarthworkPreviewState.NONE;
        invalidateTerrainComparisonCache();
    }

    private void clearGhostsOnly() {
        IGhostBlockService ghostBlockManager = host.ghosts();
        if (ghostBlockManager != null) {
            ghostBlockManager.clearGhostBlocks(GhostBlockOwners.EARTHWORK);
        }
    }

    /**
     * 参数/工程变更后使预览失效：立即收回施工资格；若需彻底清除虚影与缓存则走 {@link #clearPreview()}。
     * 默认标记 DIRTY（保留旧结果参考）；无缓存时等同 NONE。
     */
    public void invalidatePreview(EarthworkProject project) {
        boolean hadPreview = lastGenerationResult != null;
        if (hadPreview) {
            // 保留虚影/旧结果作参考，但立即收回施工资格
            markDirty();
        } else {
            calculationGeneration.incrementAndGet();
            previewState = EarthworkPreviewState.NONE;
        }
        invalidateTerrainComparisonCache();
        if (project != null) {
            EarthworkSite site = project.getActiveSite();
            site.setLastReport(EarthworkVolumeReport.empty());
            for (GradingRegion region : project.getRegions().values()) {
                region.setLastVolumeReport(EarthworkVolumeReport.empty());
            }
        }
    }

    public void invalidateTerrainComparisonCache() {
        cachedTerrainComparison = null;
        cachedTerrainComparisonAtMs = 0L;
        cachedTerrainComparisonSource = null;
        cachedTerrainComparisonWorldKey = null;
    }

    /**
     * 与当前世界对比预览快照（带短 TTL 缓存，供 UI 每帧查询）。
     */
    public TerrainSnapshot.ComparisonResult comparePreviewTerrainWithWorld(World world) {
        return comparePreviewTerrainWithWorld(world, false);
    }

    /**
     * @param forceRefresh {@code true} 时忽略缓存（正式施工前必须用）
     */
    public TerrainSnapshot.ComparisonResult comparePreviewTerrainWithWorld(World world, boolean forceRefresh) {
        EarthworkGenerationResult result = lastGenerationResult;
        if (result == null || result.existingTerrainSnapshot.isEmpty() || world == null) {
            return null;
        }
        String worldKey = TerrainSnapshotCache.worldKey(world);
        long now = System.currentTimeMillis();
        TerrainSnapshot.ComparisonResult cached = cachedTerrainComparison;
        if (!forceRefresh
            && cached != null
            && cachedTerrainComparisonSource == result
            && Objects.equals(cachedTerrainComparisonWorldKey, worldKey)
            && now - cachedTerrainComparisonAtMs < TERRAIN_COMPARE_CACHE_TTL_MS) {
            return cached;
        }
        long started = System.nanoTime();
        TerrainSnapshot.ComparisonResult fresh = result.existingTerrainSnapshot.compareWithCurrentWorld(world);
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        if (elapsedMs >= 50L) {
            LOGGER.info(
                "地形对比耗时 {} ms（标高变化 {} / 地表漂移 {} / 总格 {}）",
                elapsedMs,
                fresh.groundElevationChangedColumns(),
                fresh.surfaceOnlyChangedColumns(),
                fresh.totalColumns());
        }
        cachedTerrainComparison = fresh;
        cachedTerrainComparisonAtMs = now;
        cachedTerrainComparisonSource = result;
        cachedTerrainComparisonWorldKey = worldKey;
        return fresh;
    }

    /**
     * 将当前预览报告导出为 CSV + JSON，写入默认目录 {@link EarthworkReportExporter#defaultExportDirectory()}。
     *
     * @return 是否成功
     */
    public boolean exportLastReport(EarthworkProject project, GradingRegion region) {
        EarthworkGenerationResult result = lastGenerationResult;
        if (result == null || result.volumeReport == null || !result.volumeReport.hasGeometricVolume()) {
            publishStatus(com.plot.plugin.earthwork.model.EarthworkStatusLevel.WARNING,
                PlotI18n.tr("plugin.earthwork.export_report_no_preview"));
            return false;
        }
        try {
            EarthworkReportExporter.ExportResult paths =
                EarthworkReportExporter.exportPreview(result, project, region);
            publishStatus(com.plot.plugin.earthwork.model.EarthworkStatusLevel.SUCCESS, PlotI18n.tr(
                "plugin.earthwork.export_report_success",
                paths.csvPath().getFileName().toString(),
                paths.csvPath().getParent().toString()));
            return true;
        } catch (Exception e) {
            LOGGER.error("土方报表导出失败: {}", e.getMessage(), e);
            publishStatus(com.plot.plugin.earthwork.model.EarthworkStatusLevel.ERROR,
                PlotI18n.tr("plugin.earthwork.export_report_failed", e.getMessage()));
            return false;
        }
    }

    private static World getClientWorld() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null ? client.world : null;
    }
}
