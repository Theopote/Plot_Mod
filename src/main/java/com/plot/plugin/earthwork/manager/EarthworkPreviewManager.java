package com.plot.plugin.earthwork.manager;

import com.plot.api.world.IGhostBlockService;
import com.plot.core.command.BlockRecord;
import com.plot.core.context.PluginContext;
import com.plot.plugin.earthwork.design.BuildingFootprintLookup;
import com.plot.plugin.earthwork.design.BuildingFootprintResolver;
import com.plot.plugin.earthwork.design.RoadSurfaceLookup;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.pipeline.EarthworkPipelines;
import com.plot.plugin.earthwork.pipeline.LegacyRegionPipeline;
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

    private final PluginContext host;
    private final SiteEarthworkPipeline sitePipeline;
    private final LegacyRegionPipeline legacyPipeline;
    private final TerrainSnapshotCache terrainCache;
    private final Consumer<String> statusSink;

    private volatile EarthworkGenerationResult lastGenerationResult;
    private volatile EarthworkValidationReport lastValidationReport = EarthworkValidationReport.empty();

    public EarthworkValidationReport getLastValidationReport() {
        return lastValidationReport;
    }

    public EarthworkPreviewManager(
            PluginContext host,
            EarthworkPipelines.Bundle pipelines,
            TerrainSnapshotCache terrainCache,
            Consumer<String> statusSink) {
        this(host, pipelines.site(), pipelines.legacy(), terrainCache, statusSink);
    }

    public EarthworkPreviewManager(
            PluginContext host,
            SiteEarthworkPipeline sitePipeline,
            LegacyRegionPipeline legacyPipeline,
            TerrainSnapshotCache terrainCache,
            Consumer<String> statusSink) {
        this.host = Objects.requireNonNull(host, "host");
        this.sitePipeline = Objects.requireNonNull(sitePipeline, "sitePipeline");
        this.legacyPipeline = Objects.requireNonNull(legacyPipeline, "legacyPipeline");
        this.terrainCache = Objects.requireNonNull(terrainCache, "terrainCache");
        this.statusSink = statusSink != null ? statusSink : msg -> {};
    }

    public EarthworkGenerationResult getLastGenerationResult() {
        return lastGenerationResult;
    }

    public boolean hasValidPreview() {
        EarthworkGenerationResult result = lastGenerationResult;
        return result != null && !result.placementRecords.isEmpty();
    }

    public boolean calculatePreview(
            EarthworkProject project,
            GradingRegion region,
            BuildingFootprintLookup buildingLookup,
            RoadSurfaceLookup roadLookup) {
        World world = getClientWorld();
        if (world == null) {
            statusSink.accept(PlotI18n.tr("plugin.earthwork.generate_world_unavailable"));
            return false;
        }
        if (project == null || region == null) {
            statusSink.accept(PlotI18n.tr("plugin.earthwork.generate_empty_result"));
            return false;
        }

        EarthworkValidationReport validation = EarthworkValidator.analyzePrePreview(
            project, region, buildingLookup);
        lastValidationReport = validation;
        if (validation.blocksPreview()) {
            statusSink.accept(validation.firstBlockingMessage());
            return false;
        }

        IGhostBlockService ghostBlockManager = host.ghosts();
        if (ghostBlockManager != null) {
            ghostBlockManager.clearAllGhostBlocks();
        }

        EarthworkSite site = project.getActiveSite();
        try {
            if (site.delegatesToLegacyGenerator()) {
                TerrainSnapshot terrain = terrainCache.captureFresh(region, world, host.coordinates());
                lastGenerationResult = legacyPipeline.execute(
                    region, world, terrain, null, site.getMaterialModel());
            } else {
                TerrainSnapshot terrain = terrainCache.captureFreshSite(site, world, host.coordinates());
                lastGenerationResult = sitePipeline.execute(
                    com.plot.plugin.earthwork.pipeline.EarthworkPipelineContext.of(
                        site, world, terrain, region, buildingLookup, roadLookup));
            }
        } catch (BuildingFootprintResolver.UnresolvedBuildingReferenceException e) {
            LOGGER.error("土方预览被建筑引用阻断: {}", e.getMessage());
            lastGenerationResult = null;
            statusSink.accept(e.getMessage());
            return false;
        } catch (Exception e) {
            LOGGER.error("土方预览生成失败: {}", e.getMessage(), e);
            lastGenerationResult = null;
            statusSink.accept(PlotI18n.tr("plugin.earthwork.generate_empty_result"));
            return false;
        }

        if (lastGenerationResult == null || lastGenerationResult.placementRecords.isEmpty()) {
            statusSink.accept(PlotI18n.tr("plugin.earthwork.generate_empty_result"));
            return false;
        }

        enrichProjectReport(project, site, region, lastGenerationResult);
        lastGenerationResult.warnings.addAll(validation.warningKeys());
        statusSink.accept(PlotI18n.tr("plugin.earthwork.generate_preview_ready"));
        return true;
    }

    private static void enrichProjectReport(
            EarthworkProject project,
            EarthworkSite site,
            GradingRegion region,
            EarthworkGenerationResult result) {
        if (result.siteVolumeReport.byZone().isEmpty()) {
            result.projectReport = EarthworkProjectReport.Builder.buildFromSingleZone(
                project, site, region.getId(), result.volumeReport);
        } else if (result.projectReport == null
            || result.projectReport == EarthworkProjectReport.empty()) {
            result.projectReport = EarthworkProjectReport.Builder.buildFromProject(
                project, site, result.siteVolumeReport);
        }
    }

    public void projectPreview() {
        EarthworkGenerationResult result = lastGenerationResult;
        if (result == null) {
            return;
        }
        IGhostBlockService ghostBlockManager = host.ghosts();
        if (ghostBlockManager == null) {
            return;
        }
        ghostBlockManager.clearAllGhostBlocks();
        for (BlockRecord record : result.placementRecords.values()) {
            EarthworkGenerationResult.ChangeType changeType = result.changeTypes.get(record.pos);
            String ghostBlock = changeType == EarthworkGenerationResult.ChangeType.CUT
                ? CUT_GHOST_BLOCK
                : FILL_GHOST_BLOCK;
            ghostBlockManager.addGhostBlock(record.pos, ghostBlock);
        }
    }

    public void clearPreview() {
        IGhostBlockService ghostBlockManager = host.ghosts();
        if (ghostBlockManager != null) {
            ghostBlockManager.clearAllGhostBlocks();
        }
        lastGenerationResult = null;
        lastValidationReport = EarthworkValidationReport.empty();
    }

    /**
     * 参数/工程变更后使预览失效，并清零区域上次统计，避免陈旧数据误导。
     */
    public void invalidatePreview(EarthworkProject project) {
        boolean hadPreview = lastGenerationResult != null;
        clearPreview();
        if (project != null) {
            EarthworkSite site = project.getActiveSite();
            site.setLastReport(EarthworkVolumeReport.empty());
            for (GradingRegion region : project.getRegions().values()) {
                region.setLastVolumeReport(EarthworkVolumeReport.empty());
            }
        }
        if (hadPreview) {
            statusSink.accept(PlotI18n.tr("plugin.earthwork.preview_invalidated"));
        }
    }

    public TerrainSnapshot.ComparisonResult comparePreviewTerrainWithWorld(World world) {
        EarthworkGenerationResult result = lastGenerationResult;
        if (result == null || result.existingTerrainSnapshot.isEmpty() || world == null) {
            return null;
        }
        return result.existingTerrainSnapshot.compareWithCurrentWorld(world);
    }

    /**
     * 将当前预览报告导出为 CSV + JSON，写入默认目录 {@link EarthworkReportExporter#defaultExportDirectory()}。
     *
     * @return 是否成功
     */
    public boolean exportLastReport(EarthworkProject project, GradingRegion region) {
        EarthworkGenerationResult result = lastGenerationResult;
        if (result == null || result.volumeReport == null || !result.volumeReport.hasGeometricVolume()) {
            statusSink.accept(PlotI18n.tr("plugin.earthwork.export_report_no_preview"));
            return false;
        }
        try {
            EarthworkReportExporter.ExportResult paths =
                EarthworkReportExporter.exportPreview(result, project, region);
            statusSink.accept(PlotI18n.tr(
                "plugin.earthwork.export_report_success",
                paths.csvPath().getFileName().toString(),
                paths.csvPath().getParent().toString()));
            return true;
        } catch (Exception e) {
            LOGGER.error("土方报表导出失败: {}", e.getMessage(), e);
            statusSink.accept(PlotI18n.tr("plugin.earthwork.export_report_failed", e.getMessage()));
            return false;
        }
    }

    private static World getClientWorld() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null ? client.world : null;
    }
}
