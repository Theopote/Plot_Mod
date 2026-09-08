package com.plot.plugin.powerline.design;

import com.plot.core.material.MaterialMix;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoleDesignSerializationTest {

    @Test
    void attachmentsRoundTrip() {
        PoleDesign design = new PoleDesign("with-attachments", "With Attachments");
        design.getLayers().add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            6,
            MaterialMix.single("minecraft:oak_fence")));
        design.setAttachments(ConductorAttachmentPresets.threePhaseHorizontal(12.0));
        design.getAttachments().getFirst().setInsulatorLength(2);

        PoleDesign restored = PoleDesign.fromJson(design.toJson());
        assertNotNull(restored);
        assertEquals(3, restored.getAttachments().size());
        assertEquals("phase_b", restored.getAttachments().get(1).getId());
        assertEquals(12.0, restored.getAttachments().get(1).getVerticalOffset(), 1e-6);
        assertEquals(2, restored.getAttachments().getFirst().getInsulatorLength());
    }

    @Test
    void legacyDesignWithoutAttachmentsStillLoads() {
        PoleDesign source = new PoleDesign("legacy-wood", "Legacy Wood");
        source.getLayers().add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            8,
            MaterialMix.single("minecraft:oak_fence")));

        String json = source.toJson().replace(",\"attachments\":[]", "");

        PoleDesign restored = PoleDesign.fromJson(json);
        assertNotNull(restored);
        assertTrue(restored.getAttachments().isEmpty());
        assertFalse(restored.getLayers().isEmpty());
    }
}
