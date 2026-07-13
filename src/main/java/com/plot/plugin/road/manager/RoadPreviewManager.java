package com.plot.plugin.road.manager;

import com.plot.core.command.BlockRecord;
import com.plot.core.command.CommandManager;
import com.plot.core.command.commands.GenerateRoadCommand;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import com.plot.infrastructure.event.block.BlockPlacementScheduler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.infrastructure.event.block.GhostBlockManager;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.RoadPlacementVisibility;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.utils.PlotI18n;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 道路预览、虚影投影与世界落地。
 */
public final class RoadPreviewManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadPreview");

    private final RoadProjectStatus status;
    private RoadNetworkGenerator networkGenerator;
    private RoadGenerationResult lastGenerationResult;
    private Map<String, RoadGenerationResult> lastEdgeResults = Collections.emptyMap();

    public RoadPreviewManager(RoadProjectStatus status) {
        this.status = status;
    }

    public void setNetworkGenerator(RoadNetworkGenerator networkGenerator) {
        this.networkGenerator = networkGenerator;
    }

    public RoadGenerationResult getLastGenerationResult() {
        return lastGenerationResult;
    }

    public Map<String, RoadGenerationResult> getLastEdgeResults() {
        return lastEdgeResults;
    }

    public RoadGenerationResult getLastEdgeResult(String edgeId) {
        if (edgeId == null || lastEdgeResults.isEmpty()) {
            return null;
        }
        return lastEdgeResults.get(edgeId);
    }

    public boolean calculateNetworkPreview(RoadNetwork network) {
        if (network.getEdges().isEmpty()) {
            status.set(PlotI18n.tr("plugin.road.no_edges"));
            return false;
        }

        World world = RoadNetworkGenerator.getClientWorld();
        if (world == null || networkGenerator == null) {
            LOGGER.warn("世界或生成器未就绪");
            status.set(PlotI18n.tr("plugin.road.generate_world_unavailable"));
            return false;
        }

        GhostBlockManager ghostBlockManager = GhostBlockManager.getInstance();
        if (ghostBlockManager != null) {
            ghostBlockManager.clearAllGhostBlocks();
        }
        RoadNetworkGenerator.PreviewResult previewResult = networkGenerator.generatePreview(network, world);
        lastGenerationResult = previewResult.aggregate();
        lastEdgeResults = new LinkedHashMap<>(previewResult.edgeResults());

        if (lastGenerationResult == null || lastGenerationResult.placementRecords.isEmpty()) {
            status.set(PlotI18n.tr("plugin.road.generate_empty_result"));
            LOGGER.warn("路网预览未产生可投影方块");
            return false;
        }

        LOGGER.info("路网预览: 挖{} 填{} 路灯{}",
            lastGenerationResult.cutVolume, lastGenerationResult.fillVolume,
            lastGenerationResult.streetlightCount);
        applyPreviewReadyStatus();
        return true;
    }

    private void applyPreviewReadyStatus() {
        RoadPlacementVisibility.Analysis visibility = analyzeRoadVisibility();
        if (visibility != null && visibility.requiresWarning()) {
            status.set(PlotI18n.tr("plugin.road.generate_preview_ready")
                + " — "
                + RoadPlacementVisibility.formatWarningMessage(visibility));
            return;
        }
        status.set(PlotI18n.tr("plugin.road.generate_preview_ready"));
    }

    public RoadPlacementVisibility.Analysis analyzeRoadVisibility() {
        if (lastGenerationResult == null) {
            return null;
        }
        return RoadPlacementVisibility.analyze(lastGenerationResult, CoordinateTransformer.getInstance());
    }

    public String formatVisibilityWarning() {
        return RoadPlacementVisibility.formatWarningMessage(analyzeRoadVisibility());
    }

    public void projectRoadPreview() {
        if (lastGenerationResult == null) {
            return;
        }
        GhostBlockManager ghostBlockManager = GhostBlockManager.getInstance();
        if (ghostBlockManager == null) {
            return;
        }

        ghostBlockManager.clearAllGhostBlocks();
        for (BlockRecord record : lastGenerationResult.placementRecords.values()) {
            ghostBlockManager.addGhostBlock(record.pos, record.newBlockId);
        }
    }

    public void clearPreview() {
        GhostBlockManager ghostBlockManager = GhostBlockManager.getInstance();
        if (ghostBlockManager != null) {
            ghostBlockManager.clearAllGhostBlocks();
        }
        lastEdgeResults = Collections.emptyMap();
        lastGenerationResult = null;
    }

    /**
     * 检查预览是否有效（非空且未过期）
     */
    public boolean hasValidPreview() {
        return lastGenerationResult != null && !lastGenerationResult.placementRecords.isEmpty();
    }

    /**
     * 使预览失效（在网络变更时调用）
     */
    public void invalidatePreview() {
        if (lastGenerationResult != null || !lastEdgeResults.isEmpty()) {
            LOGGER.debug("网络已变更，预览结果失效");
            lastEdgeResults = Collections.emptyMap();
            lastGenerationResult = null;
            // 不清除虚影，让用户知道需要重新计算预览
        }
    }

    public void buildRoadInWorld() {
        if (lastGenerationResult == null || lastGenerationResult.placementRecords.isEmpty()) {
            status.set(PlotI18n.tr("plugin.road.build_no_blocks"));
            return;
        }

        BlockProjectionHandler.PlacementReadiness readiness =
            BlockProjectionHandler.getInstance().checkWorldModificationReadiness();
        if (!readiness.ready()) {
            status.set(readiness.message());
            return;
        }

        if (BlockPlacementScheduler.getInstance().isBusy()) {
            status.set(PlotI18n.tr("plugin.road.build_in_progress_wait"));
            return;
        }

        RoadPlacementVisibility.Analysis visibility = analyzeRoadVisibility();
        if (visibility != null && visibility.requiresWarning()) {
            LOGGER.warn("道路落地范围超出当前摄像机视野: {}", visibility.status());
        }

        List<BlockRecord> records =
            new ArrayList<>(lastGenerationResult.placementRecords.values());
        GenerateRoadCommand command = new GenerateRoadCommand(records);
        status.set(PlotI18n.tr("plugin.road.build_in_progress", records.size()));
        command.executeScheduled(() -> {
            GenerateRoadCommand.ExecutionResult result = command.getLastExecutionResult();
            if (result != null && result.cancelled()) {
                status.set(PlotI18n.tr("plugin.road.build_cancelled", result.success(), result.total()));
                return;
            }
            CommandManager.getInstance().pushExecuted(command);
            applyBuildResultStatus(result);
            clearPreview();
        });
    }

    private void applyBuildResultStatus(GenerateRoadCommand.ExecutionResult result) {
        if (result == null || result.total() == 0) {
            status.set(PlotI18n.tr("plugin.road.build_no_blocks"));
            return;
        }
        if (result.cancelled()) {
            status.set(PlotI18n.tr("plugin.road.build_cancelled", result.success(), result.total()));
            return;
        }
        if (result.isFullSuccess()) {
            status.set(PlotI18n.tr("plugin.road.build_success", result.success()));
            return;
        }
        if (result.isTotalFailure()) {
            status.set(PlotI18n.tr("plugin.road.build_failed", result.total()));
            return;
        }
        status.set(PlotI18n.tr(
            "plugin.road.build_partial",
            result.success(),
            result.total(),
            result.failed()));
    }
}
