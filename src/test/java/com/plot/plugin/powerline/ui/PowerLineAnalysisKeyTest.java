package com.plot.plugin.powerline.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineAnalysisKeyTest {

    @Test
    void matchesCurrentPreview() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        PowerLineDesignProject designs = new PowerLineDesignProject();
        PowerLinePreviewKey previewKey = PowerLinePreviewKey.capture(line, designs);
        PowerLineAnalysisKey analysisKey = PowerLineAnalysisKey.capture(previewKey);

        assertTrue(analysisKey.matches(line, designs, previewKey));
    }

    @Test
    void mismatchesWhenPreviewKeyStale() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        PowerLineDesignProject designs = new PowerLineDesignProject();
        PowerLineAnalysisKey analysisKey = PowerLineAnalysisKey.capture(
            PowerLinePreviewKey.capture(line, designs));

        line.setSagRatio(0.25);
        PowerLinePreviewKey currentPreviewKey = PowerLinePreviewKey.capture(line, designs);

        assertFalse(analysisKey.matches(line, designs, currentPreviewKey));
    }

    @Test
    void mismatchesWhenFootprintIdChanges() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        PowerLineDesignProject designs = new PowerLineDesignProject();
        PowerLinePreviewKey previewKey = PowerLinePreviewKey.capture(line, designs);
        PowerLineAnalysisKey analysisKey = PowerLineAnalysisKey.capture(previewKey);

        PowerLineFootprint other = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        PowerLinePreviewKey otherPreviewKey = PowerLinePreviewKey.capture(other, designs);

        assertFalse(analysisKey.matches(other, designs, otherPreviewKey));
    }
}
