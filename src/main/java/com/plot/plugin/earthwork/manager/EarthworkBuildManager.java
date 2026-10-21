package com.plot.plugin.earthwork.manager;

import com.plot.api.world.PlacementReadiness;
import com.plot.core.command.BlockRecord;
import com.plot.core.command.commands.EarthworkGenerateCommand;
import com.plot.core.context.PluginContext;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.plugin.earthwork.terrain.TerrainSnapshotCache;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.utils.PlotI18n;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 土方落地、取消与撤销入栈（对标道路 {@link com.plot.plugin.road.manager.RoadPreviewManager#buildRoadInWorld} 的构建职责）。
 */
public final class EarthworkBuildManager {
    private final PluginContext host;
    private final TerrainSnapshotCache terrainCache;
    private final EarthworkPreviewManager previewManager;
    private final Consumer<String> statusSink;
    private final AtomicBoolean buildInFlight = new AtomicBoolean(false);
    private volatile BuildExecutionSummary lastExecutionSummary;

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
        return validateBuildEligibility(project, builtRegionId, clientWorld(), true, null);
    }

    /**
     * 单测入口：可注入世界要求与地形对比结果。
     *
     * @param requireWorld {@code true} 时无客户端世界即拒绝（正式路径）
     * @param terrainComparisonOverride 非 null 时跳过实时采样，直接使用该对比结果
     */
    String validateBuildEligibility(
            EarthworkProject project,
            String builtRegionId,
            World world,
            boolean requireWorld,
            TerrainSnapshot.ComparisonResult terrainComparisonOverride) {
        if (!previewManager.hasValidPreview()) {
            return PlotI18n.tr("plugin.earthwork.build_preview_not_valid");
        }
        EarthworkGenerationResult result = previewManager.getLastGenerationResult();
        if (result == null || result.placementRecords.isEmpty()) {
            return PlotI18n.tr("plugin.earthwork.build_no_blocks");
        }
        if (requireWorld && world == null) {
            return PlotI18n.tr("plugin.earthwork.generate_world_unavailable");
        }
        if (world != null) {
            if (!EarthworkPreviewIdentity.matchesCurrent(result, project, builtRegionId, world)) {
                return PlotI18n.tr("plugin.earthwork.build_preview_mismatch");
            }
        } else if (!EarthworkPreviewIdentity.matchesDesign(result, project, builtRegionId)) {
            return PlotI18n.tr("plugin.earthwork.build_preview_mismatch");
        }

        TerrainSnapshot.ComparisonResult terrainComparison = terrainComparisonOverride;
        if (terrainComparison == null && world != null) {
            terrainComparison = previewManager.comparePreviewTerrainWithWorld(world);
        }
        String terrainRejection = rejectIfTerrainUnsafe(world != null, terrainComparison);
        if (terrainRejection != null) {
            return terrainRejection;
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
        buildInWorld(project, builtRegionId, true);
    }

    /**
     * 单测入口：无 Minecraft 客户端世界时允许 {@code requireWorld=false}。
     */
    void buildInWorld(EarthworkProject project, String builtRegionId, boolean requireWorld) {
        String rejection = validateBuildEligibility(
            project, builtRegionId, clientWorld(), requireWorld, null);
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
                BuildOutcome outcome = BuildOutcome.from(result);
                lastExecutionSummary = BuildExecutionSummary.from(outcome, result);
                // 仅当实际写入过方块时才入 Undo 栈，避免空失败命令污染历史
                if (command.hasAppliedRecords()) {
                    host.commands().pushExecuted(command);
                }
                if (result != null && result.success() > 0) {
                    invalidateTerrainAfterBuild(project, builtRegionId);
                }
                applyBuildResultStatus(outcome, result);
                previewManager.clearPreview();
            } finally {
                buildInFlight.set(false);
            }
        });
    }

    public BuildExecutionSummary lastExecutionSummary() {
        return lastExecutionSummary;
    }

    /**
     * 施工结果分类：完成 / 部分成功 / 失败 / 取消。
     */
    public enum BuildOutcome {
        COMPLETED,
        PARTIAL,
        FAILED,
        CANCELLED,
        NO_WORK;

        public static BuildOutcome from(EarthworkGenerateCommand.ExecutionResult result) {
            if (result == null || result.total() == 0) {
                return NO_WORK;
            }
            if (result.cancelled()) {
                return CANCELLED;
            }
            if (result.isFullSuccess()) {
                return COMPLETED;
            }
            if (result.isTotalFailure()) {
                return FAILED;
            }
            return PARTIAL;
        }
    }

    /**
     * 最近一次施工摘要。预览方量报告始终是预期值；部分成功/取消时
     * {@link #previewReportIsAsBuilt()} 为 false，需重新采样世界后再算竣工差。
     */
    public record BuildExecutionSummary(
            BuildOutcome outcome,
            int success,
            int failed,
            int total,
            boolean cancelled) {
        public static BuildExecutionSummary from(
                BuildOutcome outcome,
                EarthworkGenerateCommand.ExecutionResult result) {
            BuildOutcome safe = outcome != null ? outcome : BuildOutcome.NO_WORK;
            if (result == null) {
                return new BuildExecutionSummary(safe, 0, 0, 0, false);
            }
            return new BuildExecutionSummary(
                safe, result.success(), result.failed(), result.total(), result.cancelled());
        }

        /** 预览方量是否仍可视为竣工统计（仅完全成功时为 true）。 */
        public boolean previewReportIsAsBuilt() {
            return outcome == BuildOutcome.COMPLETED;
        }
    }

    boolean isBuildInFlight() {
        return buildInFlight.get();
    }

    /** 单测：标记施工占用状态。 */
    void setBuildInFlightForTest(boolean inFlight) {
        buildInFlight.set(inFlight);
    }

    private void invalidateTerrainAfterBuild(EarthworkProject project, String builtRegionId) {
        if (builtRegionId != null && !builtRegionId.isBlank()) {
            terrainCache.invalidateRegion(builtRegionId);
        }
        if (project != null) {
            terrainCache.invalidateSite(project.getActiveSiteId());
        }
    }

    private void applyBuildResultStatus(
            BuildOutcome outcome,
            EarthworkGenerateCommand.ExecutionResult result) {
        switch (outcome) {
            case NO_WORK -> statusSink.accept(PlotI18n.tr("plugin.earthwork.build_no_blocks"));
            case CANCELLED -> statusSink.accept(PlotI18n.tr(
                "plugin.earthwork.build_cancelled",
                result != null ? result.success() : 0,
                result != null ? result.total() : 0));
            case COMPLETED -> statusSink.accept(PlotI18n.tr(
                "plugin.earthwork.build_success",
                result != null ? result.success() : 0));
            case FAILED -> statusSink.accept(PlotI18n.tr(
                "plugin.earthwork.build_failed",
                result != null ? result.total() : 0));
            case PARTIAL -> statusSink.accept(PlotI18n.tr(
                "plugin.earthwork.build_partial",
                result != null ? result.success() : 0,
                result != null ? result.total() : 0,
                result != null ? result.failed() : 0));
        }
    }

    /**
     * 地形门禁：
     * <ul>
     *   <li>有世界且对比结果为 null → 无法校验，拒绝</li>
     *   <li>对比结果明确显示已变化 → 拒绝（含单测注入的 override）</li>
     *   <li>无世界且无对比结果 → 跳过（离线/单测路径）</li>
     * </ul>
     */
    static String rejectIfTerrainUnsafe(
            boolean worldPresent,
            TerrainSnapshot.ComparisonResult terrainComparison) {
        if (worldPresent && terrainComparison == null) {
            return PlotI18n.tr("plugin.earthwork.build_terrain_check_unavailable");
        }
        if (terrainComparison != null && terrainComparison.terrainChanged()) {
            return PlotI18n.tr(
                "plugin.earthwork.terrain_changed_since_preview",
                terrainComparison.changedColumns(),
                terrainComparison.totalColumns());
        }
        return null;
    }

    private static World clientWorld() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null ? client.world : null;
    }
}
