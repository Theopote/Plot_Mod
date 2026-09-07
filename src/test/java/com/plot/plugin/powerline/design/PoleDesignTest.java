package com.plot.plugin.powerline.design;

import com.plot.core.material.MaterialMix;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PoleDesignTest {

    @Test
    void totalHeightSumsLayerHeights() {
        PoleDesign design = new PoleDesign("test");
        design.getLayers().add(new PoleLayer(PoleLayer.Shape.COLUMN, 4, MaterialMix.single("minecraft:oak_fence")));
        design.getLayers().add(new PoleLayer(PoleLayer.Shape.CROSSARM, 1, MaterialMix.single("minecraft:oak_slab")));
        design.getLayers().add(new PoleLayer(PoleLayer.Shape.CAP, 1, MaterialMix.single("minecraft:lantern")));

        assertEquals(6, design.totalHeight());
    }

    @Test
    void jsonRoundTripPreservesLayers() {
        PoleDesign design = PoleDesignCatalog.latticeSteelTower();
        design.setName("Custom Lattice");

        PoleDesign restored = PoleDesign.fromJson(design.toJson());
        assertNotNull(restored);
        assertEquals(design.getId(), restored.getId());
        assertEquals("Custom Lattice", restored.getName());
        assertEquals(design.totalHeight(), restored.totalHeight());
        assertEquals(design.getLayers().size(), restored.getLayers().size());
        assertEquals(
            design.getLayers().getFirst().getShape(),
            restored.getLayers().getFirst().getShape());
        assertEquals(
            design.getLayers().get(2).getCrossarmLength(),
            restored.getLayers().get(2).getCrossarmLength());
    }

    @Test
    void capLayerHeightIsAlwaysOne() {
        PoleLayer layer = new PoleLayer(PoleLayer.Shape.CAP, 1, MaterialMix.single("minecraft:stone"));
        layer.setHeight(5);
        assertEquals(1, layer.getHeight());
    }

    @Test
    void crossarmLengthIsOdd() {
        PoleLayer layer = new PoleLayer();
        layer.setShape(PoleLayer.Shape.CROSSARM);
        layer.setCrossarmLength(4);
        assertEquals(5, layer.getCrossarmLength());
    }

    @Test
    void topmostCrossarmIsConductorAttachmentLayer() {
        PoleDesign design = new PoleDesign("multi-crossarm");
        design.getLayers().add(new PoleLayer(PoleLayer.Shape.COLUMN, 4, MaterialMix.single("minecraft:oak_fence")));
        design.getLayers().add(new PoleLayer(PoleLayer.Shape.CROSSARM, 1, MaterialMix.single("minecraft:oak_slab")));
        design.getLayers().add(new PoleLayer(PoleLayer.Shape.COLUMN, 3, MaterialMix.single("minecraft:oak_fence")));
        design.getLayers().add(new PoleLayer(PoleLayer.Shape.CROSSARM, 1, MaterialMix.single("minecraft:oak_slab")));

        assertEquals(3, design.conductorCrossarmLayerIndex());
        assertEquals(73, design.wireHangHeightFromGround(64));
    }
}
