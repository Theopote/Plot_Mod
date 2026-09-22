package com.plot.plugin.powerline.design;

import com.plot.core.material.MaterialMix;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PoleDesignSerializationTest {

    @Test
    void attachmentsRoundTrip() {
        PoleDesign design = new PoleDesign("with-attachments", "With Attachments");
        design.addLayer(new PoleLayer(
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
    void designWithoutAttachmentsStaysEmptyOnLoad() {
        PoleDesign source = new PoleDesign("wood-pole", "Wood Pole");
        source.addLayer(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            8,
            MaterialMix.single("minecraft:oak_fence")));

        String json = source.toJson().replace(",\"attachments\":[]", "");

        PoleDesign restored = PoleDesign.fromJson(json);
        assertNotNull(restored);
        assertEquals(0, restored.getAttachments().size());
        assertEquals(1, restored.getLayers().size());
    }
}
