package com.plot.plugin.pattern.pipeline;

import com.plot.plugin.pattern.PatternGenerationIssue;
import com.plot.plugin.pattern.PatternGenerationResult;
import com.plot.plugin.pattern.model.PatternBorderConfig;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.space.PatternSample;
import com.plot.plugin.pattern.space.PatternSpace;

import java.util.List;

/** 单块 footprint 的分步生成会话，供 {@link PatternPreviewJob} 按预算推进。 */
public final class PatternGenerationSession {
    private final PatternFootprint footprint;
    private final PatternMaterialResolver resolver;
    private final PatternGenerationResult result;
    private final PatternSpace space;
    private final List<PatternSample> samples;
    private final TerrainSurfaceProjector projector;
    private final PatternPlacementRecorder recorder;
    private final PatternBorderConfig borderConfig;
    private final String borderMaterial;
    private final boolean failed;

    private int nextSampleIndex;
    private int skippedTransparent;
    private int fallbackElevation;

    private PatternGenerationSession(
            PatternFootprint footprint,
            PatternMaterialResolver resolver,
            PatternGenerationResult result,
            PatternSpace space,
            List<PatternSample> samples,
            TerrainSurfaceProjector projector,
            PatternPlacementRecorder recorder,
            PatternBorderConfig borderConfig,
            String borderMaterial,
            boolean failed) {
        this.footprint = footprint;
        this.resolver = resolver;
        this.result = result;
        this.space = space;
        this.samples = samples;
        this.projector = projector;
        this.recorder = recorder;
        this.borderConfig = borderConfig;
        this.borderMaterial = borderMaterial;
        this.failed = failed;
    }

    public static PatternGenerationSession failed(
            PatternFootprint footprint,
            PatternGenerationIssue issue) {
        PatternGenerationResult result = new PatternGenerationResult();
        result.setIssue(issue);
        return new PatternGenerationSession(
            footprint,
            null,
            result,
            null,
            List.of(),
            null,
            null,
            null,
            null,
            true);
    }

    static PatternGenerationSession ready(
            PatternFootprint footprint,
            PatternMaterialResolver resolver,
            PatternGenerationResult result,
            PatternSpace space,
            List<PatternSample> samples,
            TerrainSurfaceProjector projector,
            PatternPlacementRecorder recorder,
            PatternBorderConfig borderConfig,
            String borderMaterial) {
        return new PatternGenerationSession(
            footprint,
            resolver,
            result,
            space,
            samples,
            projector,
            recorder,
            borderConfig,
            borderMaterial,
            false);
    }

    public PatternFootprint footprint() {
        return footprint;
    }

    public PatternGenerationResult result() {
        return result;
    }

    public boolean isFailed() {
        return failed;
    }

    public boolean isComplete() {
        return failed || nextSampleIndex >= samples.size();
    }

    public int sampleCount() {
        return samples.size();
    }

    public int processedSampleCount() {
        return nextSampleIndex;
    }

    List<PatternSample> samples() {
        return samples;
    }

    PatternSpace space() {
        return space;
    }

    PatternMaterialResolver resolver() {
        return resolver;
    }

    TerrainSurfaceProjector projector() {
        return projector;
    }

    PatternPlacementRecorder recorder() {
        return recorder;
    }

    PatternBorderConfig borderConfig() {
        return borderConfig;
    }

    String borderMaterial() {
        return borderMaterial;
    }

    int nextSampleIndex() {
        return nextSampleIndex;
    }

    void advanceSampleIndex(int processed) {
        nextSampleIndex += processed;
    }

    void recordSkippedTransparent() {
        skippedTransparent++;
    }

    void recordFallbackElevation() {
        fallbackElevation++;
    }

    int skippedTransparent() {
        return skippedTransparent;
    }

    int fallbackElevation() {
        return fallbackElevation;
    }

    void applyFinalize() {
        if (failed) {
            return;
        }
        result.setSampleCount(samples.size());
        result.setSkippedTransparentCount(skippedTransparent);
        result.setFallbackElevationCount(fallbackElevation);
        if (!result.hasPlacements()) {
            if (skippedTransparent >= samples.size()) {
                result.setIssue(PatternGenerationIssue.ALL_PIXELS_TRANSPARENT);
            } else {
                result.setIssue(PatternGenerationIssue.NO_SAMPLE_POINTS);
            }
        }
    }
}
