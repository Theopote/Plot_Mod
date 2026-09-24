package com.plot.plugin.road.manager;

import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.RoadNetworkPreviewSession;
import com.plot.plugin.road.model.RoadNetwork;
import net.minecraft.world.World;

/**
 * 路网预览分帧 job：每帧在主线程推进若干边/路口生成，避免 UI 长时间阻塞。
 * <p>
 * World 采样必须在主线程，故不使用后台线程；由 {@link RoadUIManager} 每帧 {@link #tick()}。
 */
public final class RoadNetworkPreviewJob {

    public enum Phase {
        PENDING,
        PREPARING,
        EDGES,
        JUNCTIONS,
        AGGREGATING,
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
                case JUNCTIONS -> tickJunctions();
                case AGGREGATING -> tickAggregate();
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
        networkGenerator.tickPreviewSessionEdges(session, RoadNetworkGenerator.PREVIEW_EDGES_PER_TICK);
        manager.updatePreviewJobProgress(this);
        if (!session.hasMoreEdges()) {
            phase = Phase.JUNCTIONS;
        }
    }

    private void tickJunctions() {
        networkGenerator.tickPreviewSessionJunctions(session, RoadNetworkGenerator.PREVIEW_JUNCTIONS_PER_TICK);
        manager.updatePreviewJobProgress(this);
        if (!session.hasMoreJunctions()) {
            phase = Phase.AGGREGATING;
        }
    }

    private void tickAggregate() {
        RoadNetworkGenerator.PreviewResult previewResult = networkGenerator.completePreviewSession(session);
        phase = Phase.COMPLETED;
        manager.completePreviewJob(this, previewResult, autoProjectGhosts);
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
            return phase == Phase.PENDING ? 0 : 0;
        }
        return session.processedWorkUnits();
    }

    public int totalWorkUnits() {
        return session != null ? session.totalWorkUnits() : 0;
    }

    public float progressFraction() {
        if (totalWorkUnits() <= 0) {
            return phase == Phase.PREPARING ? 0f : 0f;
        }
        return Math.min(1f, (float) processedWorkUnits() / totalWorkUnits());
    }

    public int progressPercent() {
        return Math.round(progressFraction() * 100f);
    }

    public String phaseTranslationKey() {
        return phaseTranslationKey(phase);
    }

    public static String phaseTranslationKey(Phase phase) {
        return switch (phase) {
            case PENDING -> "plugin.road.preview_phase_pending";
            case PREPARING -> "plugin.road.preview_phase_preparing";
            case EDGES -> "plugin.road.preview_phase_edges";
            case JUNCTIONS -> "plugin.road.preview_phase_junctions";
            case AGGREGATING -> "plugin.road.preview_phase_aggregating";
            default -> "plugin.road.preview_phase_edges";
        };
    }

    boolean autoProjectGhosts() {
        return autoProjectGhosts;
    }

    RoadNetwork sourceNetwork() {
        return sourceNetwork;
    }
}
