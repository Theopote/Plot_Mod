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
    void legacyDesignWithoutAttachmentsGetsDefaultOnLoad() {
        PoleDesign source = new PoleDesign("legacy-wood", "Legacy Wood");
        source.getLayers().add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            8,
            MaterialMix.single("minecraft:oak_fence")));

        String json = source.toJson().replace(",\"attachments\":[]", "");

        PoleDesign restored = PoleDesign.fromJson(json);
        assertNotNull(restored);
        assertEquals(1, restored.getAttachments().size());
        assertFalse(restored.getLayers().isEmpty());
    }

    @Test
    void legacyGroundWireRoleMigratesOnPoleDesignLoad() {
        String json = """
            {
              "id": "legacy-top",
              "name": "Legacy Top",
              "layers": [{"shape": "COLUMN", "height": 8, "crossarmLength": 0, "material": "minecraft:oak_fence"}],
              "attachments": [{
                "id": "tw",
                "name": "TW",
                "lateralOffset": 0,
                "verticalOffset": 10,
                "longitudinalOffset": 0,
                "role": "GROUND_WIRE",
                "insulatorLength": 1,
                "enabled": true
              }]
            }
            """;
        PoleDesign restored = PoleDesign.fromJson(json);
        assertEquals(AttachmentRole.TOP_WIRE, restored.getAttachments().getFirst().getRole());
        assertTrue(restored.toJson().contains("\"TOP_WIRE\""));
        assertTrue(!restored.toJson().contains("\"GROUND_WIRE\""));
    }
}
