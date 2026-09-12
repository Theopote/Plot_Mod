package com.plot.plugin.powerline.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.api.world.WorldViewBounds;
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
        PowerLineAnalysisKey analysisKey = PowerLineAnalysisKey.capture(previewKey, line);

        assertTrue(analysisKey.matches(line, designs, previewKey));
    }

    @Test
    void matchesPreviewWithProjectionFingerprint() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        PowerLineDesignProject designs = new PowerLineDesignProject();
        ICoordinateService nearView = projectionService(100f, 1f);
        PowerLinePreviewKey previewKey = PowerLinePreviewKey.capture(line, designs, nearView);
        PowerLineAnalysisKey analysisKey = PowerLineAnalysisKey.capture(previewKey, line);

        assertTrue(analysisKey.matches(line, designs, previewKey));
    }

    @Test
    void mismatchesWhenPreviewKeyStale() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        PowerLineDesignProject designs = new PowerLineDesignProject();
        PowerLineAnalysisKey analysisKey = PowerLineAnalysisKey.capture(
            PowerLinePreviewKey.capture(line, designs),
            line);

        line.setSagRatio(0.25);
        PowerLinePreviewKey currentPreviewKey = PowerLinePreviewKey.capture(line, designs);

        assertFalse(analysisKey.matches(line, designs, currentPreviewKey));
    }

    @Test
    void mismatchesWhenAnalysisToggleChanges() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        PowerLineDesignProject designs = new PowerLineDesignProject();
        PowerLinePreviewKey previewKey = PowerLinePreviewKey.capture(line, designs);
        PowerLineAnalysisKey analysisKey = PowerLineAnalysisKey.capture(previewKey, line);

        line.setLineChecksEnabled(false);
        assertFalse(analysisKey.matches(line, designs, previewKey));
    }

    @Test
    void mismatchesWhenFootprintIdChanges() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        PowerLineDesignProject designs = new PowerLineDesignProject();
        PowerLinePreviewKey previewKey = PowerLinePreviewKey.capture(line, designs);
        PowerLineAnalysisKey analysisKey = PowerLineAnalysisKey.capture(previewKey, line);

        PowerLineFootprint other = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        PowerLinePreviewKey otherPreviewKey = PowerLinePreviewKey.capture(other, designs);

        assertFalse(analysisKey.matches(other, designs, otherPreviewKey));
    }

    private static ICoordinateService projectionService(float viewDistance, float viewScale) {
        return new ICoordinateService() {
            @Override
            public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
                return canvasPos != null ? canvasPos.copy() : new Vec2d(0, 0);
            }

            @Override
            public WorldViewBounds getMinecraftWorldViewBounds() {
                return new WorldViewBounds(0, viewDistance, 0, viewDistance);
            }

            @Override
            public WorldProjectionSnapshot captureProjection() {
                return new WorldProjectionSnapshot(
                    getMinecraftWorldViewBounds(),
                    viewDistance,
                    viewScale,
                    800f,
                    600f);
            }
        };
    }
}
