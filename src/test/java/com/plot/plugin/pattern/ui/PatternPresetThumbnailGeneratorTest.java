package com.plot.plugin.pattern.ui;

import com.plot.core.material.BlockColorRegistry;
import com.plot.plugin.pattern.model.BuiltInPatternPresets;
import com.plot.plugin.pattern.model.PatternPreset;
import com.plot.plugin.pattern.model.ProceduralPatternConfig;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternPresetThumbnailGeneratorTest {

    @Test
    void proceduralBuiltinPresetProducesColoredThumbnail() {
        PatternPreset preset = BuiltInPatternPresets.all().stream()
            .filter(candidate -> "builtin:checkerboard_classic".equals(candidate.getId()))
            .findFirst()
            .orElseThrow();

        PatternPreviewBuckets buckets = PatternPresetThumbnailGenerator.generate(preset, null);
        assertTrue(hasFilledPixels(buckets));

        Set<Integer> colors = new HashSet<>();
        collectColors(buckets, colors);
        assertTrue(colors.size() >= 2, "checkerboard should use at least two colors");
        assertTrue(colors.contains(BlockColorRegistry.colorFor("minecraft:stone")));
        assertTrue(colors.contains(BlockColorRegistry.colorFor("minecraft:stone_bricks")));
    }

    @Test
    void configFingerprintChangesWhenPresetConfigChanges() {
        PatternPreset preset = new PatternPreset("Test", new ProceduralPatternConfig());
        int before = PatternPresetThumbnailGenerator.configFingerprint(preset);
        ProceduralPatternConfig updated = preset.getProceduralConfig();
        updated.setType(ProceduralPatternConfig.PatternType.STRIPES);
        preset.setProceduralConfig(updated);
        int after = PatternPresetThumbnailGenerator.configFingerprint(preset);
        assertTrue(before != after);
    }

    private static boolean hasFilledPixels(PatternPreviewBuckets buckets) {
        for (int py = 0; py < buckets.height(); py++) {
            for (int px = 0; px < buckets.width(); px++) {
                if (buckets.countAt(px, py) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void collectColors(PatternPreviewBuckets buckets, Set<Integer> colors) {
        for (int py = 0; py < buckets.height(); py++) {
            for (int px = 0; px < buckets.width(); px++) {
                if (buckets.countAt(px, py) > 0) {
                    colors.add(buckets.colorAt(px, py));
                }
            }
        }
    }
}
