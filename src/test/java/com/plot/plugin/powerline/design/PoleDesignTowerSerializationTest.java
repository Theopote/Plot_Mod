package com.plot.plugin.powerline.design;

import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.design.structure.TowerStructurePresets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoleDesignTowerSerializationTest {

    @Test
    void generation1LayersOnlyStillLoads() {
        String json = PoleDesignCatalog.simpleWoodPole().toJson();
        assertFalseContains(json, "towerStructure");
        PoleDesign design = PoleDesign.fromJson(json);
        assertNotNull(design);
        assertNull(design.getTowerStructure());
        assertFalse(design.hasTowerStructure());
    }

    @Test
    void generation2LayersAndAttachmentsStillLoads() {
        PoleDesign design = new PoleDesign("test");
        design.setLayers(PoleDesignCatalog.simpleWoodPole().getLayers());
        design.setAttachments(ConductorAttachmentPresets.threePhaseHorizontal(12));
        String json = design.toJson();
        PoleDesign restored = PoleDesign.fromJson(json);
        assertNotNull(restored);
        assertTrue(restored.hasEnabledAttachments());
        assertNull(restored.getTowerStructure());
    }

    @Test
    void generation3TowerStructureAndAttachmentsRoundTrip() {
        PoleDesign design = TowerStructurePresets.taperedLatticePoleDesign("tower", "Tower");
        String json = design.toJson();
        PoleDesign restored = PoleDesign.fromJson(json);
        assertNotNull(restored);
        assertTrue(restored.hasTowerStructure());
        assertTrue(restored.hasEnabledAttachments());
        TowerStructureDesign structure = restored.getTowerStructure();
        assertNotNull(structure);
        assertTrue(structure.sortedStations().size() >= 4);
    }

    private static void assertFalseContains(String json, String token) {
        org.junit.jupiter.api.Assertions.assertFalse(json.contains(token));
    }
}
