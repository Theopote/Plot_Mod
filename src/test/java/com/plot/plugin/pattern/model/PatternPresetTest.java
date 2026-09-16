package com.plot.plugin.pattern.model;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternPresetTest {

    @Test
    void applyToFootprintIncludesBorderConfig() {
        PatternFootprint footprint = new PatternFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(4, 0),
            new Vec2d(4, 4),
            new Vec2d(0, 4)
        ));
        ProceduralPatternConfig pattern = new ProceduralPatternConfig();
        pattern.setType(ProceduralPatternConfig.PatternType.CHECKERBOARD);
        footprint.setPattern(pattern);

        PatternBorderConfig border = new PatternBorderConfig();
        border.setEnabled(true);
        border.setBorderWidth(2.0);
        border.setBorderMaterial("minecraft:stone_bricks");
        footprint.setBorderConfig(border);

        PatternPreset preset = PatternPreset.fromFootprint(footprint, "Test preset");
        PatternFootprint target = new PatternFootprint(footprint.getOuterPoints());
        preset.applyToFootprint(target);

        assertTrue(target.getBorderConfig().isEnabled());
        assertEquals(2.0, target.getBorderConfig().getBorderWidth(), 1e-6);
        assertEquals("minecraft:stone_bricks", target.getBorderConfig().getBorderMaterial());
    }

    @Test
    void fromFootprintCapturesBorderConfig() {
        PatternFootprint footprint = new PatternFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(2, 0),
            new Vec2d(2, 2),
            new Vec2d(0, 2)
        ));
        PatternBorderConfig border = new PatternBorderConfig();
        border.setEnabled(true);
        border.setOuterBorder(false);
        footprint.setBorderConfig(border);

        PatternPreset preset = PatternPreset.fromFootprint(footprint, "Border preset");
        assertTrue(preset.getBorderConfig().isEnabled());
        assertEquals(false, preset.getBorderConfig().isOuterBorder());
    }

    @Test
    void builtInPresetUsesNameKey() {
        PatternPreset preset = BuiltInPatternPresets.all().getFirst();
        assertTrue(preset.isBuiltIn());
        assertEquals("builtin:checkerboard_classic", preset.getId());
        assertEquals("plugin.pattern.preset.builtin.checkerboard_classic.name", preset.getNameKey());
    }
}
