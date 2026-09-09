package com.plot.plugin.powerline.design;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.equipment.InsulatorAssemblyCatalog;
import com.plot.plugin.powerline.equipment.InsulatorType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void jsonRoundTripPreservesAttachmentInsulatorAndBindingFields() {
        PoleDesign design = new PoleDesign("insulator-roundtrip");
        ConductorAttachment attachment = new ConductorAttachment("phase_a", "A");
        attachment.setRole(AttachmentRole.PHASE_A);
        attachment.setLateralOffset(-4.5);
        attachment.setVerticalOffset(42.0);
        attachment.setLongitudinalOffset(1.0);
        attachment.setInsulatorMaterial(MaterialMix.single("minecraft:chain"));
        attachment.setInsulatorLength(5);
        attachment.setInsulatorType(InsulatorType.STRAIN);
        attachment.setInsulatorAssemblyId(InsulatorAssemblyCatalog.LONG_STRAIN_ID);
        attachment.setBundleVisual(BundleVisual.TWIN);
        attachment.setArmId("arm_upper");
        attachment.setEnabled(true);
        design.setAttachments(List.of(attachment));

        PoleDesign restored = PoleDesign.fromJson(design.toJson());
        assertNotNull(restored);
        assertEquals(1, restored.getAttachments().size());
        ConductorAttachment restoredAttachment = restored.getAttachments().getFirst();

        assertEquals("phase_a", restoredAttachment.getId());
        assertEquals(AttachmentRole.PHASE_A, restoredAttachment.getRole());
        assertEquals(-4.5, restoredAttachment.getLateralOffset());
        assertEquals(42.0, restoredAttachment.getVerticalOffset());
        assertEquals(1.0, restoredAttachment.getLongitudinalOffset());
        assertEquals("minecraft:chain", restoredAttachment.getInsulatorMaterial().getPrimaryMaterial());
        assertEquals(5, restoredAttachment.getInsulatorLength());
        assertEquals(InsulatorType.STRAIN, restoredAttachment.getInsulatorType());
        assertEquals(InsulatorAssemblyCatalog.LONG_STRAIN_ID, restoredAttachment.getInsulatorAssemblyId());
        assertEquals(BundleVisual.TWIN, restoredAttachment.getBundleVisual());
        assertEquals("arm_upper", restoredAttachment.getArmId());
        assertTrue(restoredAttachment.isEnabled());
    }

    @Test
    void jsonRoundTripPreservesDisabledAttachmentState() {
        PoleDesign design = new PoleDesign("disabled-attachment");
        ConductorAttachment active = new ConductorAttachment("phase_a", "A");
        ConductorAttachment disabled = new ConductorAttachment("phase_b", "B");
        disabled.setEnabled(false);
        disabled.setInsulatorType(InsulatorType.VERTICAL);
        design.setAttachments(List.of(active, disabled));

        PoleDesign restored = PoleDesign.fromJson(design.toJson());
        ConductorAttachment restoredDisabled = restored.findAttachment("phase_b");
        assertNotNull(restoredDisabled);
        assertFalse(restoredDisabled.isEnabled());
        assertEquals(InsulatorType.VERTICAL, restoredDisabled.getInsulatorType());
    }

    @Test
    void jsonRoundTripPreservesAllDisabledAttachmentsWithoutInjectingDefault() {
        ConductorAttachment disabledA = new ConductorAttachment("phase_a", "A");
        disabledA.setEnabled(false);
        disabledA.setInsulatorType(InsulatorType.STRAIN);
        ConductorAttachment disabledB = new ConductorAttachment("phase_b", "B");
        disabledB.setEnabled(false);
        disabledB.setInsulatorType(InsulatorType.VERTICAL);

        PoleDesign design = new PoleDesign("all-disabled");
        design.setAttachments(List.of(disabledA, disabledB));

        PoleDesign restored = PoleDesign.fromJson(design.toJson());
        assertEquals(2, restored.getAttachments().size());
        assertFalse(restored.hasEnabledAttachments());
        assertEquals(InsulatorType.STRAIN, restored.findAttachment("phase_a").getInsulatorType());
        assertEquals(InsulatorType.VERTICAL, restored.findAttachment("phase_b").getInsulatorType());
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
