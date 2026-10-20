package com.plot.plugin.earthwork.manager;

import com.plot.api.world.PlacementReadiness;
import com.plot.core.command.BlockRecord;
import com.plot.core.command.commands.EarthworkGenerateCommand;
import com.plot.core.context.PluginContext;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.terrain.TerrainSnapshotCache;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.utils.PlotI18n;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final AtomicBoolean buildInFlight = new AtomicBoolean(false);

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

    /**
     * @return 拒绝原因（已本地化文案）；可施工时返回 null
     */
    public String validateBuildEligibility(EarthworkProject project, String builtRegionId) {
        if (!previewManager.hasValidPreview()) {
            return PlotI18n.tr("plugin.earthwork.build_preview_not_valid");
        }
        EarthworkGenerationResult result = previewManager.getLastGenerationResult();
        if (result == null || result.placementRecords.isEmpty()) {
            return PlotI18n.tr("plugin.earthwork.build_no_blocks");
        }
        World world = clientWorld();
        if (!EarthworkPreviewIdentity.matchesCurrent(result, project, builtRegionId, world)) {
            return PlotI18n.tr("plugin.earthwork.build_preview_mismatch");
        }
        PlacementReadiness readiness = host.projection().checkWorldModificationReadiness();
        if (!readiness.ready()) {
            return readiness.message();
        }
        if (host.placement().isBusy() || buildInFlight.get()) {
            return PlotI18n.tr("plugin.earthwork.build_in_progress_wait");
        }
        return null;
    }

    public void buildInWorld(EarthworkProject project, String builtRegionId) {
        String rejection = validateBuildEligibility(project, builtRegionId);
        if (rejection != null) {
            statusSink.accept(rejection);
            return;
        }

        EarthworkGenerationResult resultSnapshot = previewManager.getLastGenerationResult();
        if (!buildInFlight.compareAndSet(false, true)) {
            statusSink.accept(PlotI18n.tr("plugin.earthwork.build_in_progress_wait"));
            return;
        }

        List<BlockRecord> records = new ArrayList<>(resultSnapshot.placementRecords.values());
        EarthworkGenerateCommand command = new EarthworkGenerateCommand(
            records, host.projection(), host.placement());
        statusSink.accept(PlotI18n.tr("plugin.earthwork.build_in_progress", records.size()));
        command.executeScheduled(() -> {
            try {
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
            } finally {
                buildInFlight.set(false);
            }
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

    private static World clientWorld() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null ? client.world : null;
    }
}
