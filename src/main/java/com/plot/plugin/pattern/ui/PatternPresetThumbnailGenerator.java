package com.plot.plugin.pattern.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.core.material.BlockColorRegistry;
import com.plot.plugin.pattern.PatternPreviewFingerprint;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.model.PatternPreset;
import com.plot.plugin.pattern.pipeline.PatternMaterialResolver;
import com.plot.plugin.pattern.pipeline.PatternMaterialResolverFactory;
import com.plot.plugin.pattern.space.PatternSample;
import com.plot.plugin.pattern.space.PatternSampling;
import com.plot.plugin.pattern.space.PatternSpace;

import java.nio.file.Path;
import java.util.List;

/** 在固定虚拟区域上生成预设缩略图（不依赖世界地形）。 */
final class PatternPresetThumbnailGenerator {
    private static final int MAX_THUMB_CELLS = 24;
    private static final String VIRTUAL_FOOTPRINT_ID = "__preset_thumb__";
    private static final List<Vec2d> VIRTUAL_OUTER = List.of(
        new Vec2d(0, 0),
        new Vec2d(12, 0),
        new Vec2d(12, 12),
        new Vec2d(0, 12));

    private PatternPresetThumbnailGenerator() {
    }

    static PatternPreviewBuckets generate(PatternPreset preset, Path pluginDataDir) {
        if (preset == null) {
            return emptyBuckets(1, 1);
        }

        PatternFootprint footprint = virtualFootprint();
        preset.applyToFootprint(footprint);
        PatternSpace space = PatternSpace.fromFootprint(footprint);
        List<PatternSample> samples = PatternSampling.collectFootprintSamples(footprint.getOuterPoints());
        if (samples.isEmpty()) {
            return emptyBuckets(1, 1);
        }

        PatternMaterialResolverFactory.Outcome outcome =
            PatternMaterialResolverFactory.forFootprint(footprint, pluginDataDir);
        if (!outcome.canGenerate()) {
            return emptyBuckets(1, 1);
        }

        PolygonRegionUtils.RectBounds bounds = space.regionBounds();
        double spanX = Math.max(1e-6, bounds.maxX() - bounds.minX());
        double spanZ = Math.max(1e-6, bounds.maxZ() - bounds.minZ());
        int cellsX = Math.min(MAX_THUMB_CELLS, Math.max(1, (int) Math.ceil(spanX)));
        int cellsZ = Math.min(MAX_THUMB_CELLS, Math.max(1, (int) Math.ceil(spanZ)));

        PatternPreviewBuckets buckets = new PatternPreviewBuckets(cellsX, cellsZ);
        PatternMaterialResolver resolver = outcome.resolver();
        for (PatternSample sample : samples) {
            String blockId = resolver.resolveMaterial(space, sample);
            if (blockId == null || blockId.isBlank()) {
                continue;
            }
            int cellX = toCellIndex(sample.x(), bounds.minX(), spanX, cellsX);
            int cellY = toCellIndex(sample.z(), bounds.minZ(), spanZ, cellsZ);
            buckets.add(cellX, cellY, BlockColorRegistry.colorFor(blockId));
        }
        return buckets;
    }

    static int configFingerprint(PatternPreset preset) {
        if (preset == null) {
            return 0;
        }
        PatternFootprint footprint = virtualFootprint();
        preset.applyToFootprint(footprint);
        return PatternPreviewFingerprint.configFingerprint(List.of(footprint));
    }

    private static PatternFootprint virtualFootprint() {
        return new PatternFootprint(VIRTUAL_FOOTPRINT_ID, VIRTUAL_OUTER);
    }

    private static PatternPreviewBuckets emptyBuckets(int width, int height) {
        return new PatternPreviewBuckets(width, height);
    }

    private static int toCellIndex(double coord, double minCoord, double span, int cellCount) {
        double normalized = (coord - minCoord) / span;
        int index = (int) Math.floor(normalized * cellCount);
        if (index < 0) {
            return 0;
        }
        if (index >= cellCount) {
            return cellCount - 1;
        }
        return index;
    }
}
