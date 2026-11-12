package com.plot.plugin.road.manager;

import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.RoadNetworkPreviewSession;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.ui.PluginJobProgressUi;
import net.minecraft.world.World;

/**
 * 路网预览分帧 job：每帧在主线程推进若干边/路口生成，避免 UI 长时间阻塞。
 * <p>
 * World 采样必须在主线程，故不使用后台线程；由 {@link RoadUIManager} 每帧 {@link #tick()}。
 * <p>
 * 进度覆盖准备、边/路口、隧道连通、汇总子步进与幽灵投影，避免「条已满仍卡住」。
 */
public final class RoadNetworkPreviewJob {

    /** 准备阶段计入进度的虚拟工作量。 */
    static final int PREPARE_WORK_UNITS = 1;
    /** 边完成后的隧道连通计入进度的虚拟工作量。 */
    static final int CONNECTIVITY_WORK_UNITS = 1;

    public enum Phase {
        PENDING,
        PREPARING,
        EDGES,
        CONNECTIVITY,
        JUNCTIONS,
        AGGREGATING,
        PROJECTING,
        COMPLETED,
        CANCELLED,
        FAILED
    }

    private final RoadNetwork sourceNetwork;
    private final boolean autoProjectGhosts;
    private final RoadPreviewManager manager;
    private final RoadNetworkGenerator networkGenerator;

    private Phase phase = Phase.PENDING;
    private RoadNetworkPreviewSession session;
    private volatile boolean cancelled;
    private int ghostProcessed;
    private int ghostTotal;

    RoadNetworkPreviewJob(
            RoadNetwork sourceNetwork,
            boolean autoProjectGhosts,
            RoadNetworkGenerator networkGenerator,
            RoadPreviewManager manager) {
        this.sourceNetwork = sourceNetwork;
        this.autoProjectGhosts = autoProjectGhosts;
        this.networkGenerator = networkGenerator;
        this.manager = manager;
    }

    public void tick() {
        if (phase == Phase.COMPLETED || phase == Phase.CANCELLED || phase == Phase.FAILED) {
            return;
        }
        if (cancelled) {
            phase = Phase.CANCELLED;
            manager.finishCancelledPreviewJob(this);
            return;
        }

        World world = RoadNetworkGenerator.getClientWorld();
        if (world == null || networkGenerator == null) {
            phase = Phase.FAILED;
            manager.failPreviewJob(this);
            return;
        }

        try {
            switch (phase) {
                case PENDING -> beginSession(world);
                case PREPARING -> tickPrepare();
                case EDGES -> tickEdges();
                case CONNECTIVITY -> tickConnectivity();
                case JUNCTIONS -> tickJunctions();
                case AGGREGATING -> tickAggregate();
                case PROJECTING -> tickProjecting();
                default -> {
                }
            }
        } catch (RuntimeException e) {
            phase = Phase.FAILED;
            manager.failPreviewJob(this, e);
        }
    }

    private void beginSession(World world) {
        session = networkGenerator.beginPreviewSession(sourceNetwork, world);
        if (!session.isValid()) {
            phase = Phase.FAILED;
            manager.failPreviewJob(this);
            return;
        }
        phase = Phase.PREPARING;
        manager.updatePreviewJobProgress(this);
    }

    private void tickPrepare() {
        networkGenerator.preparePreviewSession(session);
        phase = Phase.EDGES;
        manager.updatePreviewJobProgress(this);
    }

    private void tickEdges() {
        networkGenerator.tickPreviewSessionEdges(session, RoadNetworkGenerator.PREVIEW_EDGES_PER_TICK, 8_000_000L);
        manager.updatePreviewJobProgress(this);
        if (!session.hasMoreEdges()) {
            phase = Phase.CONNECTIVITY;
        }
    }

    private void tickConnectivity() {
        networkGenerator.applyPreviewSessionTunnelConnectivity(session);
        phase = Phase.JUNCTIONS;
        manager.updatePreviewJobProgress(this);
    }

    private void tickJunctions() {
        networkGenerator.tickPreviewSessionJunctions(session, RoadNetworkGenerator.PREVIEW_JUNCTIONS_PER_TICK, 8_000_000L);
        manager.updatePreviewJobProgress(this);
        if (!session.hasMoreJunctions()) {
            phase = Phase.AGGREGATING;
        }
    }

    private void tickAggregate() {
        boolean done = networkGenerator.tickPreviewSessionAggregation(session);
        manager.updatePreviewJobProgress(this);
        if (!done) {
            return;
        }
        RoadNetworkGenerator.PreviewResult previewResult = session.completedPreview();
        if (previewResult == null) {
            phase = Phase.FAILED;
            manager.failPreviewJob(this);
            return;
        }
        manager.completePreviewJob(this, previewResult, autoProjectGhosts);
        if (phase == Phase.FAILED || phase == Phase.CANCELLED) {
            return;
        }
        if (autoProjectGhosts && manager.hasPendingGhostProjection()) {
            ghostTotal = Math.max(1, manager.ghostProjectionTotal());
            ghostProcessed = manager.ghostProjectionProcessed();
            phase = Phase.PROJECTING;
            manager.updatePreviewJobProgress(this);
        } else {
            phase = Phase.COMPLETED;
        }
    }

    private void tickProjecting() {
        boolean stillUploading = manager.tickGhostProjection();
        ghostProcessed = manager.ghostProjectionProcessed();
        ghostTotal = Math.max(ghostTotal, manager.ghostProjectionTotal());
        manager.updatePreviewJobProgress(this);
        if (!stillUploading) {
            phase = Phase.COMPLETED;
            manager.finishPreviewJobProjection(this);
        }
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean isRunning() {
        return !cancelled
            && phase != Phase.COMPLETED
            && phase != Phase.CANCELLED
            && phase != Phase.FAILED;
    }

    public Phase phase() {
        return phase;
    }

    public int processedWorkUnits() {
        if (session == null) {
            return 0;
        }
        int processed = 0;
        if (phase.ordinal() > Phase.PREPARING.ordinal()) {
            processed += PREPARE_WORK_UNITS;
        }
        processed += session.processedWorkUnits();
        if (phase.ordinal() > Phase.CONNECTIVITY.ordinal()) {
            processed += CONNECTIVITY_WORK_UNITS;
        }
        if (phase == Phase.AGGREGATING
            || phase == Phase.PROJECTING
            || phase == Phase.COMPLETED) {
            processed += session.processedAggregationWorkUnits();
        }
        if (phase == Phase.PROJECTING || phase == Phase.COMPLETED) {
            processed += Math.min(ghostTotal, Math.max(0, ghostProcessed));
        }
        return Math.min(totalWorkUnits(), processed);
    }

    public int totalWorkUnits() {
        int base = session != null ? session.totalWorkUnits() : 0;
        int total = PREPARE_WORK_UNITS + base + CONNECTIVITY_WORK_UNITS;
        if (session != null) {
            total += session.aggregationWorkUnits();
        } else {
            total += 3;
        }
        if (phase == Phase.PROJECTING || phase == Phase.COMPLETED) {
            total += Math.max(0, ghostTotal);
        } else if (autoProjectGhosts) {
            // Reserve at least one unit so the bar cannot sit at 100% before projection.
            total += 1;
        }
        return Math.max(1, total);
    }

    public float progressFraction() {
        return PluginJobProgressUi.fraction(processedWorkUnits(), totalWorkUnits());
    }

    public int progressPercent() {
        return PluginJobProgressUi.percent(processedWorkUnits(), totalWorkUnits());
    }

    public String phaseTranslationKey() {
        return phaseTranslationKey(phase);
    }

    public static String phaseTranslationKey(Phase phase) {
        return switch (phase) {
            case PENDING -> "plugin.road.preview_phase_pending";
            case PREPARING -> "plugin.road.preview_phase_preparing";
            case EDGES -> "plugin.road.preview_phase_edges";
            case CONNECTIVITY -> "plugin.road.preview_phase_connectivity";
            case JUNCTIONS -> "plugin.road.preview_phase_junctions";
            case AGGREGATING -> "plugin.road.preview_phase_aggregating";
            case PROJECTING -> "plugin.road.preview_phase_projecting";
            default -> "plugin.road.preview_phase_edges";
        };
    }

    boolean autoProjectGhosts() {
        return autoProjectGhosts;
    }

    RoadNetwork sourceNetwork() {
        return sourceNetwork;
    }

    void markFailed() {
        phase = Phase.FAILED;
    }

    void markCancelled() {
        phase = Phase.CANCELLED;
    }
}
