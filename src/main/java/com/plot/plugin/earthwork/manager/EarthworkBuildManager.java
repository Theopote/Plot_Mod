package com.plot.plugin.earthwork.manager;

import com.plot.api.world.PlacementReadiness;
import com.plot.core.command.BlockRecord;
import com.plot.core.command.commands.EarthworkGenerateCommand;
import com.plot.core.context.PluginContext;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.TerrainSnapshotCache;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.utils.PlotI18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 土方落地、取消与撤销入栈（对标道路 {@link com.plot.plugin.road.manager.RoadPreviewManager#buildRoadInWorld} 的构建职责）。
 */
public final class EarthworkBuildManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/EarthworkBuild");

    private final PluginContext host;
    private final TerrainSnapshotCache terrainCache;
    private final EarthworkPreviewManager previewManager;
    private final Consumer<String> statusSink;

    public EarthworkBuildManager(
            PluginContext host,
            TerrainSnapshotCache terrainCache,
            EarthworkPreviewManager previewManager,
            Consumer<String> statusSink) {
        this.host = Objects.requireNonNull(host, "host");
        this.terrainCache = Objects.requireNonNull(terrainCache, "terrainCache");
        this.previewManager = Objects.requireNonNull(previewManager, "previewManager");
        this.statusSink = statusSink != null ? statusSink : msg -> {};
    }

    public void buildInWorld(EarthworkProject project, String builtRegionId) {
        EarthworkGenerationResult resultSnapshot = previewManager.getLastGenerationResult();
        if (resultSnapshot == null || resultSnapshot.placementRecords.isEmpty()) {
            statusSink.accept(PlotI18n.tr("plugin.earthwork.build_no_blocks"));
            return;
        }

        PlacementReadiness readiness = host.projection().checkWorldModificationReadiness();
        if (!readiness.ready()) {
            statusSink.accept(readiness.message());
            return;
        }

        if (host.placement().isBusy()) {
            statusSink.accept(PlotI18n.tr("plugin.earthwork.build_in_progress_wait"));
            return;
        }

        List<BlockRecord> records = new ArrayList<>(resultSnapshot.placementRecords.values());
        EarthworkGenerateCommand command = new EarthworkGenerateCommand(
            records, host.projection(), host.placement());
        statusSink.accept(PlotI18n.tr("plugin.earthwork.build_in_progress", records.size()));
        command.executeScheduled(() -> {
            EarthworkGenerateCommand.ExecutionResult result = command.getLastExecutionResult();
            if (result != null && result.cancelled()) {
                if (command.hasAppliedRecords()) {
                    host.commands().pushExecuted(command);
                    invalidateTerrainAfterBuild(project, builtRegionId);
                }
                statusSink.accept(PlotI18n.tr(
                    "plugin.earthwork.build_cancelled", result.success(), result.total()));
                previewManager.clearPreview();
                return;
            }
            host.commands().pushExecuted(command);
            if (result != null && result.success() > 0) {
                invalidateTerrainAfterBuild(project, builtRegionId);
            }
            applyBuildResultStatus(result);
            previewManager.clearPreview();
        });
    }

    private void invalidateTerrainAfterBuild(EarthworkProject project, String builtRegionId) {
        if (builtRegionId != null && !builtRegionId.isBlank()) {
            terrainCache.invalidateRegion(builtRegionId);
        }
        if (project != null) {
            terrainCache.invalidateSite(project.getActiveSiteId());
        }
    }

    private void applyBuildResultStatus(EarthworkGenerateCommand.ExecutionResult result) {
        if (result == null || result.total() == 0) {
            statusSink.accept(PlotI18n.tr("plugin.earthwork.build_no_blocks"));
            return;
        }
        if (result.cancelled()) {
            statusSink.accept(PlotI18n.tr("plugin.earthwork.build_cancelled", result.success(), result.total()));
            return;
        }
        if (result.isFullSuccess()) {
            statusSink.accept(PlotI18n.tr("plugin.earthwork.build_success", result.success()));
            return;
        }
        if (result.isTotalFailure()) {
            statusSink.accept(PlotI18n.tr("plugin.earthwork.build_failed", result.total()));
            return;
        }
        statusSink.accept(PlotI18n.tr(
            "plugin.earthwork.build_partial",
            result.success(),
            result.total(),
            result.failed()));
    }
}
