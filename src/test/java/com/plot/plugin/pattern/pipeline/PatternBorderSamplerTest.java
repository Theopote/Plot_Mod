package com.plot.plugin.pattern.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.pattern.model.PatternBorderConfig;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.space.PatternSample;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternBorderSamplerTest {

    @Test
    void detectsOuterBorderNearEdge() {
        PatternFootprint footprint = new PatternFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)));
        PatternBorderConfig border = new PatternBorderConfig();
        border.setEnabled(true);
        border.setOuterBorder(true);
        border.setBorderWidth(1.5);

        assertTrue(PatternBorderSampler.shouldUseBorderMaterial(
            footprint, border, new PatternSample(0.5, 5.0)));
        assertFalse(PatternBorderSampler.shouldUseBorderMaterial(
            footprint, border, new PatternSample(5.0, 5.0)));
    }
}
