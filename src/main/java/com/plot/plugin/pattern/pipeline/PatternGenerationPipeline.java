package com.plot.plugin.pattern.pipeline;

import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.pattern.PatternGenerationIssue;
import com.plot.plugin.pattern.PatternGenerationResult;
import com.plot.plugin.pattern.model.PatternBorderConfig;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.space.PatternSample;
import com.plot.plugin.pattern.space.PatternSampling;
import com.plot.plugin.pattern.space.PatternSpace;
import net.minecraft.world.World;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 图案生成管线：
 * <ol>
 *   <li>Pattern Space 采样</li>
 *   <li>材质解析</li>
 *   <li>地形投影</li>
 *   <li>放置记录</li>
 * </ol>
 */
public final class PatternGenerationPipeline {
    private final ICoordinateService coordinates;
    private final IBlockProjectionService projection;

    public PatternGenerationPipeline(
            ICoordinateService coordinates,
            IBlockProjectionService projection) {
        this.coordinates = Objects.requireNonNull(coordinates, "coordinates");
        this.projection = Objects.requireNonNull(projection, "projection");
    }

    public PatternGenerationResult generate(
            PatternFootprint footprint,
            PatternMaterialResolver materialResolver,
            World world) {
        PatternGenerationSession session = beginSession(footprint, materialResolver, world);
        if (session.isFailed()) {
            return session.result();
        }
        while (!session.isComplete()) {
            processSamples(session, Integer.MAX_VALUE, null);
        }
        finalizeSession(session);
        return session.result();
    }

    public PatternGenerationSession beginSession(
            PatternFootprint footprint,
            PatternMaterialResolver materialResolver,
            World world) {
        if (footprint == null || materialResolver == null) {
            return PatternGenerationSession.failed(footprint, PatternGenerationIssue.NO_SAMPLE_POINTS);
        }
        if (footprint.getOuterPoints().size() < 3) {
            return PatternGenerationSession.failed(footprint, PatternGenerationIssue.REGION_TOO_SMALL);
        }

        PatternSpace space = PatternSpace.fromFootprint(footprint);
        List<PatternSample> samples = PatternSampling.collectFootprintSamples(
            footprint.getOuterPoints(),
            footprint.getHoles());
        if (samples.isEmpty()) {
            return PatternGenerationSession.failed(footprint, PatternGenerationIssue.NO_SAMPLE_POINTS);
        }

        PatternGenerationResult result = new PatternGenerationResult();
        TerrainSurfaceProjector projector = TerrainSurfaceProjector.of(world, coordinates);
        PatternPlacementRecorder recorder = new PatternPlacementRecorder(projection, result);
        PatternBorderConfig borderConfig = footprint.getBorderConfig();
        String borderMaterial = borderConfig.isEnabled() ? borderConfig.getPrimaryBorderMaterial() : null;

        return PatternGenerationSession.ready(
            footprint,
            materialResolver,
            result,
            space,
            samples,
            projector,
            recorder,
            borderConfig,
            borderMaterial);
    }

    /**
     * @return 本批次处理的采样点数
     */
    public int processSamples(
            PatternGenerationSession session,
            int maxSamples,
            Consumer<PatternGenerationSamplePhase> phaseListener) {
        if (session == null || session.isFailed() || session.isComplete() || maxSamples <= 0) {
            return 0;
        }

        List<PatternSample> samples = session.samples();
        int start = session.nextSampleIndex();
        int end = Math.min(start + maxSamples, samples.size());
        int processed = 0;

        for (int i = start; i < end; i++) {
            PatternSample sample = samples.get(i);
            String blockId;
            if (session.borderMaterial() != null
                && PatternBorderSampler.shouldUseBorderMaterial(
                    session.footprint(),
                    session.borderConfig(),
                    sample)) {
                blockId = session.borderMaterial();
            } else {
                if (phaseListener != null) {
                    phaseListener.accept(PatternGenerationSamplePhase.RESOLVING);
                }
                blockId = session.resolver().resolveMaterial(session.space(), sample);
            }
            if (blockId == null) {
                session.recordSkippedTransparent();
                processed++;
                continue;
            }
            if (phaseListener != null) {
                phaseListener.accept(PatternGenerationSamplePhase.PROJECTING);
            }
            TerrainSurfaceProjector.SurfaceProjection surface = session.projector().projectSurface(sample);
            if (surface.usedFallbackElevation()) {
                session.recordFallbackElevation();
            }
            session.recorder().record(surface.pos(), blockId);
            processed++;
        }

        session.advanceSampleIndex(processed);
        return processed;
    }

    public void finalizeSession(PatternGenerationSession session) {
        if (session == null || session.isFailed()) {
            return;
        }
        session.applyFinalize();
    }
}
