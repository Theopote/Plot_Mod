package com.plot.plugin.road.ui;

import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import com.plot.plugin.road.tunnel.TunnelLightingMode;
import com.plot.plugin.road.tunnel.TunnelSectionDisplayModel;
import com.plot.plugin.road.tunnel.TunnelShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunnelCrossSectionPreviewRendererTest {

    @Test
    void shapeLabelKeysAreDefinedForEveryShape() {
        for (TunnelShape shape : TunnelShape.values()) {
            assertTrue(TunnelCrossSectionPreviewRenderer.shapeLabelKey(shape).startsWith("plugin.road.tunnel.shape."));
        }
    }

    @Test
    void previewDisplayModelUsesRepresentativeDefaultsForEachShape() {
        ResolvedTunnelStyle defaults = ResolvedTunnelStyle.defaults();
        for (TunnelShape shape : TunnelShape.values()) {
            TunnelSectionDisplayModel model = previewModel(shape, defaults, TunnelLightingMode.NONE, false);
            assertTrue(model.cells().size() > 0, shape.name());
            assertTrue(model.displayMinY() >= TunnelCrossSectionPreviewRenderer.PREVIEW_ROAD_Y, shape.name());
        }
    }

    @Test
    void shapesProduceDistinctPreviewDisplayModels() {
        ResolvedTunnelStyle defaults = ResolvedTunnelStyle.defaults();
        TunnelShape[] shapes = TunnelShape.values();
        for (int i = 0; i < shapes.length; i++) {
            TunnelSectionDisplayModel left = previewModel(shapes[i], defaults, TunnelLightingMode.NONE, false);
            for (int j = i + 1; j < shapes.length; j++) {
                TunnelSectionDisplayModel right = previewModel(shapes[j], defaults, TunnelLightingMode.NONE, false);
                assertTrue(modelsDiffer(left, right), shapes[i] + " vs " + shapes[j]);
            }
        }
    }

    @Test
    void lightingModeChangesDisplayOverlay() {
        ResolvedTunnelStyle defaults = ResolvedTunnelStyle.defaults();
        TunnelSectionDisplayModel none = previewModel(
            TunnelShape.RECTANGULAR, defaults, TunnelLightingMode.NONE, false);
        TunnelSectionDisplayModel lit = previewModel(
            TunnelShape.RECTANGULAR, defaults, TunnelLightingMode.WALL_AND_CEILING, false);
        assertNotEquals(countLights(none), countLights(lit));
    }

    private static long countLights(TunnelSectionDisplayModel model) {
        return model.cells().stream()
            .filter(c -> c.kind() == TunnelSectionDisplayModel.DisplayKind.WALL_LIGHT
                || c.kind() == TunnelSectionDisplayModel.DisplayKind.CEILING_LIGHT)
            .count();
    }

    private static boolean modelsDiffer(TunnelSectionDisplayModel left, TunnelSectionDisplayModel right) {
        if (left.cells().size() != right.cells().size()) {
            return true;
        }
        return !left.cells().equals(right.cells());
    }

    private static TunnelSectionDisplayModel previewModel(
            TunnelShape shape,
            ResolvedTunnelStyle defaults,
            TunnelLightingMode lightingMode,
            boolean accentRings) {
        return TunnelSectionDisplayModel.build(
            shape,
            TunnelCrossSectionPreviewRenderer.PREVIEW_ENVELOPE_WIDTH,
            defaults.sideClearance(),
            defaults.clearHeight(),
            defaults.liningThickness(),
            TunnelCrossSectionPreviewRenderer.PREVIEW_ROAD_Y,
            lightingMode,
            accentRings);
    }
}
