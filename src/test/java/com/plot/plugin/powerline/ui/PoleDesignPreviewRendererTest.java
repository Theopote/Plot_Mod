package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.plugin.powerline.design.structure.TowerStructurePresets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PoleDesignPreviewRendererTest {

    @Test
    void designerPreviewCentersVertically() {
        PoleDesign design = PoleDesignCatalog.simpleWoodPole();
        float width = 320f;
        float height = 200f;
        float padding = 14f;

        PoleDesignPreviewRenderer.PreviewLayout layout = PoleDesignPreviewRenderer.computeLayout(
            design, width, height, padding, false, true);

        float innerHeight = height - padding * 2f;
        float expectedOffset = (innerHeight - layout.contentHeight()) * 0.5f + padding;
        assertTrue(Math.abs(layout.baseYOffset() - expectedOffset) < 0.01f);
    }

    @Test
    void thumbnailPreviewAnchorsToBottom() {
        PoleDesign design = PoleDesignCatalog.simpleWoodPole();
        float width = 120f;
        float height = 80f;
        float padding = 5f;

        PoleDesignPreviewRenderer.PreviewLayout layout = PoleDesignPreviewRenderer.computeLayout(
            design, width, height, padding, false, false);

        float innerHeight = height - padding * 2f;
        float expectedOffset = innerHeight - layout.contentHeight() + padding;
        assertTrue(Math.abs(layout.baseYOffset() - expectedOffset) < 0.01f);
    }

    @Test
    void wideCrossarmReducesScaleToFitWidth() {
        PoleDesign design = PoleDesignCatalog.simpleWoodPole().copy();
        for (PoleLayer layer : design.getLayers()) {
            if (layer.getShape() == PoleLayer.Shape.CROSSARM) {
                layer.setCrossarmLength(12);
            }
        }

        float width = 80f;
        float height = 200f;
        float padding = 14f;
        PoleDesignPreviewRenderer.PreviewLayout layout = PoleDesignPreviewRenderer.computeLayout(
            design, width, height, padding, false, true);

        float halfWidth = PoleDesignPreviewRenderer.estimateHalfWidthPx(design, layout.scale(), false);
        assertTrue(halfWidth * 2f <= width - padding * 2f + 0.01f);
    }

    @Test
    void towerDesignFitsWithinCanvas() {
        PoleDesign design = TowerStructurePresets.taperedLatticePoleDesign("tower", "Tower");
        float width = 200f;
        float height = 200f;
        float padding = 14f;

        PoleDesignPreviewRenderer.PreviewLayout layout = PoleDesignPreviewRenderer.computeLayout(
            design, width, height, padding, true, true);

        float halfWidth = PoleDesignPreviewRenderer.estimateHalfWidthPx(design, layout.scale(), true);
        assertTrue(halfWidth * 2f <= width - padding * 2f + 0.01f);
        assertTrue(layout.contentHeight() <= height - padding * 2f + 0.01f);
    }
}
