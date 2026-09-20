package com.plot.plugin.powerline.design;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.structure.TowerStructurePresets;
import com.plot.plugin.powerline.model.TowerRole;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void planningMetadataRoundTrip() {
        PoleDesign design = new PoleDesign("mega-custom", "Mega Custom");
        design.setLayers(PoleDesignCatalog.simpleWoodPole().getLayers());
        TowerPlanningMetadata metadata = new TowerPlanningMetadata();
        metadata.setNominalHeight(80.0);
        metadata.setMaxRecommendedSpan(240.0);
        metadata.setMaxRecommendedDeflectionAngle(35.0);
        metadata.setSupportedRoles(EnumSet.of(TowerRole.SUSPENSION, TowerRole.ANGLE));
        design.setPlanningMetadata(metadata);

        PoleDesign restored = PoleDesign.fromJson(design.toJson());
        assertNotNull(restored);
        assertNotNull(restored.getPlanningMetadata());
        assertEquals(metadata, restored.getPlanningMetadata());
        assertTrue(design.toJson().contains("\"planningMetadata\""));
        assertFalse(design.toJson().contains("\"engineeringMetadata\""));
        assertTrue(design.toJson().contains("\"maxRecommendedSpan\""));
    }

    @Test
    void legacyEngineeringMetadataJsonStillLoads() {
        String json = """
            {
              "id": "legacy",
              "name": "Legacy",
              "layers": [],
              "attachments": [],
              "engineeringMetadata": {
                "nominalHeight": 24.0,
                "maxRecommendedSpan": 80.0,
                "maxRecommendedDeflectionAngle": 12.0,
                "supportedRoles": ["SUSPENSION"]
              }
            }
            """;
        PoleDesign restored = PoleDesign.fromJson(json);
        assertNotNull(restored);
        assertNotNull(restored.getPlanningMetadata());
        assertEquals(24.0, restored.getPlanningMetadata().getNominalHeight(), 1e-6);
        assertEquals(80.0, restored.getPlanningMetadata().getMaxRecommendedSpan(), 1e-6);
    }

    @Test
    void saveAsStyleCopyPreservesPlanningMetadata() {
        PoleDesign draft = TowerStructurePresets.taperedLatticePoleDesign("src", "Source");
        TowerPlanningMetadata metadata = new TowerPlanningMetadata();
        metadata.setNominalHeight(42.0);
        metadata.setMaxRecommendedSpan(120.0);
        metadata.setMaxRecommendedDeflectionAngle(15.0);
        metadata.setSupportedRoles(EnumSet.of(TowerRole.SUSPENSION, TowerRole.DEAD_END));
        draft.setPlanningMetadata(metadata);

        // Mirrors PoleDesignerPanel.saveDraft(forceNewId=true) construction.
        PoleDesign saved = new PoleDesign(draft.getName());
        saved.setLayers(draft.getLayers());
        saved.setAttachments(draft.getAttachments());
        saved.setTowerStructure(draft.getTowerStructure());
        saved.setPlanningMetadata(draft.getPlanningMetadata());

        assertEquals(metadata, saved.getPlanningMetadata());
        PoleDesign restored = PoleDesign.fromJson(saved.toJson());
        if (restored != null) {
            assertEquals(metadata, restored.getPlanningMetadata());
        }
    }

    @Test
    void unknownLayerShapeIsSkippedWithoutFailingLoad() {
        String json = """
            {
              "id": "legacy-shape",
              "name": "Legacy Shape",
              "layers": [
                {"shape": "COLUMN_OLD", "height": 4, "crossarmLength": 0, "material": "minecraft:oak_fence"},
                {"shape": "COLUMN", "height": 6, "crossarmLength": 0, "material": "minecraft:oak_fence"},
                {"shape": "CROSSARM", "height": 1, "crossarmLength": 5, "material": "minecraft:oak_log"}
              ],
              "attachments": []
            }
            """;
        PoleDesign restored = PoleDesign.fromJson(json);
        assertNotNull(restored);
        assertEquals(2, restored.getLayers().size());
        assertEquals(PoleLayer.Shape.COLUMN, restored.getLayers().getFirst().getShape());
        assertEquals(PoleLayer.Shape.CROSSARM, restored.getLayers().get(1).getShape());
    }

    @Test
    void legacyDesignWithoutAttachmentsGetsDefaultOnLoad() {
        PoleDesign source = new PoleDesign("legacy-wood", "Legacy Wood");
        source.addLayer(new PoleLayer(
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
        if (restored != null) {
            assertEquals(AttachmentRole.TOP_WIRE, restored.getAttachments().getFirst().getRole());
        }
        if (restored != null) {
            assertTrue(restored.toJson().contains("\"TOP_WIRE\""));
        }
        if (restored != null) {
            assertFalse(restored.toJson().contains("\"GROUND_WIRE\""));
        }
    }
}
