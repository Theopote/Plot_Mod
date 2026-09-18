package com.plot.plugin.pattern.ui;

import com.plot.plugin.pattern.PatternGenerationResult;
import com.plot.plugin.pattern.PatternGenerator;
import com.plot.plugin.pattern.PatternPreviewKey;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.pipeline.PatternGenerationSamplePhase;
import com.plot.plugin.pattern.pipeline.PatternGenerationSession;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 铺装预览分帧 job：每帧在客户端主线程处理若干采样点，避免 UI 长时间阻塞。
 * <p>
 * World 采样必须在主线程，故不使用后台线程；由 {@link PatternUIManager} 每帧 {@link #tick()}。
 */
public final class PatternPreviewJob {
    /** 每帧处理的采样点数（2k–5k 合理区间，默认 3k）。 */
    public static final int SAMPLES_PER_TICK = 3000;

    public enum Phase {
        PENDING,
        SAMPLING,
        RESOLVING,
        PROJECTING,
        COMPLETED,
        CANCELLED,
        FAILED
    }

    private final List<PatternFootprint> footprints;
    private final boolean autoProjectGhosts;
    private final PatternPreviewKey previewKey;
    private final PatternActions actions;

    private final List<PatternGenerationSession> sessions = new ArrayList<>();
    private final PatternGenerationResult merged = new PatternGenerationResult();

    private Phase phase = Phase.PENDING;
    private int samplingIndex;
    private int processingIndex;
    private int totalSamples;
    private int processedSamples;
    private volatile boolean cancelled;

    public PatternPreviewJob(
            List<PatternFootprint> footprints,
            PatternPreviewKey previewKey,
            boolean autoProjectGhosts,
            PatternActions actions) {
        this.footprints = footprints == null ? List.of() : List.copyOf(footprints);
        this.previewKey = previewKey;
        this.autoProjectGhosts = autoProjectGhosts;
        this.actions = actions;
    }

    public void tick() {
        if (phase == Phase.COMPLETED || phase == Phase.CANCELLED || phase == Phase.FAILED) {
            return;
        }
        if (cancelled) {
            phase = Phase.CANCELLED;
            actions.finishCancelledPreviewJob(this);
            return;
        }

        World world = actions.getClientWorld();
        PatternGenerator generator = actions.patternGenerator();
        if (world == null || generator == null) {
            phase = Phase.FAILED;
            actions.failPreviewJob(this);
            return;
        }

        switch (phase) {
            case PENDING -> phase = Phase.SAMPLING;
            case SAMPLING -> tickSampling(generator, world);
            case RESOLVING, PROJECTING -> tickProcessing(generator);
            default -> {
            }
        }
    }

    private void tickSampling(PatternGenerator generator, World world) {
        if (samplingIndex >= footprints.size()) {
            processingIndex = 0;
            phase = totalSamples > 0 ? Phase.RESOLVING : Phase.COMPLETED;
            if (phase == Phase.COMPLETED) {
                actions.completePreviewJob(this, merged, autoProjectGhosts);
            }
            return;
        }

        PatternFootprint footprint = footprints.get(samplingIndex);
        PatternGenerationSession session = generator.beginSession(footprint, world);
        sessions.add(session);
        if (!session.isFailed()) {
            totalSamples += session.sampleCount();
        } else {
            generator.finalizeSession(session);
            merged.mergeFrom(session.result());
        }
        samplingIndex++;
        actions.updatePreviewJobProgress(this);

        if (samplingIndex >= footprints.size()) {
            processingIndex = 0;
            if (totalSamples <= 0) {
                phase = Phase.COMPLETED;
                actions.completePreviewJob(this, merged, autoProjectGhosts);
            } else {
                phase = Phase.RESOLVING;
            }
        }
    }

    private void tickProcessing(PatternGenerator generator) {
        if (processingIndex >= sessions.size()) {
            finishCompleted();
            return;
        }

        PatternGenerationSession session = sessions.get(processingIndex);
        if (session.isFailed()) {
            processingIndex++;
            finishIfDone();
            return;
        }

        if (session.isComplete()) {
            processingIndex++;
            advanceAfterSession(generator, session);
            return;
        }

        phase = Phase.RESOLVING;
        int processed = generator.processSessionSamples(
            session,
            SAMPLES_PER_TICK,
            samplePhase -> {
                if (samplePhase == PatternGenerationSamplePhase.PROJECTING) {
                    phase = Phase.PROJECTING;
                } else {
                    phase = Phase.RESOLVING;
                }
            });
        processedSamples += processed;
        actions.updatePreviewJobProgress(this);

        if (session.isComplete()) {
            processingIndex++;
            advanceAfterSession(generator, session);
        }
    }

    private void advanceAfterSession(PatternGenerator generator, PatternGenerationSession session) {
        generator.finalizeSession(session);
        merged.mergeFrom(session.result());
        finishIfDone();
    }

    private void finishIfDone() {
        if (processingIndex >= sessions.size()) {
            finishCompleted();
        }
    }

    private void finishCompleted() {
        phase = Phase.COMPLETED;
        actions.completePreviewJob(this, merged, autoProjectGhosts);
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

    public PatternPreviewKey previewKey() {
        return previewKey;
    }

    public PatternGenerationResult partialResult() {
        return merged;
    }

    public int processedCount() {
        return processedSamples;
    }

    public int totalCount() {
        return totalSamples;
    }

    public float progressFraction() {
        if (totalSamples <= 0) {
            return 0f;
        }
        return Math.min(1f, (float) processedSamples / totalSamples);
    }

    public int progressPercent() {
        return Math.round(progressFraction() * 100f);
    }

    public String phaseTranslationKey() {
        return phaseTranslationKey(phase);
    }

    public static String phaseTranslationKey(Phase phase) {
        return switch (phase) {
            case PENDING -> "plugin.pattern.preview_phase_pending";
            case SAMPLING -> "plugin.pattern.preview_phase_sampling";
            case RESOLVING -> "plugin.pattern.preview_phase_resolving";
            case PROJECTING -> "plugin.pattern.preview_phase_projecting";
            default -> "plugin.pattern.preview_phase_resolving";
        };
    }

    List<PatternFootprint> footprints() {
        return List.copyOf(footprints);
    }
}
