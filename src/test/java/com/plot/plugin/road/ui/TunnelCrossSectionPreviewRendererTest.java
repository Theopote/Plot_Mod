package com.plot.plugin.road.ui;

import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import com.plot.plugin.road.tunnel.TunnelCrossSectionMask;
import com.plot.plugin.road.tunnel.TunnelShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TunnelCrossSectionPreviewRendererTest {

    @Test
    void shapeLabelKeysAreDefinedForEveryShape() {
        for (TunnelShape shape : TunnelShape.values()) {
            assertTrue(TunnelCrossSectionPreviewRenderer.shapeLabelKey(shape).startsWith("plugin.road.tunnel.shape."));
        }
    }

    @Test
    void previewMaskUsesRepresentativeDefaultsForEachShape() {
        ResolvedTunnelStyle defaults = ResolvedTunnelStyle.defaults();
        for (TunnelShape shape : TunnelShape.values()) {
            TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
                shape,
                TunnelCrossSectionPreviewRenderer.PREVIEW_ENVELOPE_WIDTH,
                defaults.sideClearance(),
                defaults.clearHeight(),
                defaults.liningThickness(),
                TunnelCrossSectionPreviewRenderer.PREVIEW_ROAD_Y,
                false);
            int[] visible = {0};
            mask.forEach((lateral, y, kind) -> {
                if (kind != TunnelCrossSectionMask.CellKind.OUTSIDE) {
                    visible[0]++;
                }
            });
            assertTrue(visible[0] > 0, shape.name());
        }
    }

    @Test
    void shapesProduceDistinctPreviewMasks() {
        ResolvedTunnelStyle defaults = ResolvedTunnelStyle.defaults();
        TunnelShape[] shapes = TunnelShape.values();
        for (int i = 0; i < shapes.length; i++) {
            TunnelCrossSectionMask left = previewMask(shapes[i], defaults);
            for (int j = i + 1; j < shapes.length; j++) {
                TunnelCrossSectionMask right = previewMask(shapes[j], defaults);
                assertTrue(masksDiffer(left, right), shapes[i] + " vs " + shapes[j]);
            }
        }
    }

    private static boolean masksDiffer(TunnelCrossSectionMask left, TunnelCrossSectionMask right) {
        int[] differences = {0};
        left.forEach((lateral, y, kind) -> {
            if (kind != right.kindAt(lateral, y)) {
                differences[0]++;
            }
        });
        return differences[0] > 0;
    }

    private static TunnelCrossSectionMask previewMask(TunnelShape shape, ResolvedTunnelStyle defaults) {
        return TunnelCrossSectionMask.build(
            shape,
            TunnelCrossSectionPreviewRenderer.PREVIEW_ENVELOPE_WIDTH,
            defaults.sideClearance(),
            defaults.clearHeight(),
            defaults.liningThickness(),
            TunnelCrossSectionPreviewRenderer.PREVIEW_ROAD_Y,
            false);
    }
}
