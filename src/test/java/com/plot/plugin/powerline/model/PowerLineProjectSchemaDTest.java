package com.plot.plugin.powerline.model;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.family.TowerFamily;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineProjectSchemaDTest {

    @Test
    void jsonRoundTripPreservesTowerFamilyFields() {
        PowerLineProject project = new PowerLineProject();
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        line.setTowerFamilyId(TowerFamily.STANDARD_LATTICE_3_PHASE_ID);
        line.setTopWireMaterial(MaterialMix.single("minecraft:chain"));
        PoleOverride override = new PoleOverride(20.0);
        override.setRoleOverride(TowerRole.DEAD_END);
        line.addPoleOverride(override);
        project.addLine(line);

        PowerLineProject restored = PowerLineProject.fromJson(project.toJson());
        PowerLineFootprint restoredLine = restored.getLine(line.getId());
        assertNotNull(restoredLine);
        assertEquals(TowerFamily.STANDARD_LATTICE_3_PHASE_ID, restoredLine.getTowerFamilyId());
        assertEquals("minecraft:chain", restoredLine.getTopWireMaterial().getPrimaryMaterial());
        assertTrue(project.toJson().contains("\"topWireMaterial\""), "new saves should use topWireMaterial JSON key");
        assertEquals(1, restoredLine.getPoleOverrides().size());
        assertEquals(TowerRole.DEAD_END, restoredLine.getPoleOverrides().getFirst().getRoleOverride());
    }

    @Test
    void lineChecksEnabledJsonRoundTrip() {
        PowerLineProject project = new PowerLineProject();
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        line.setLineChecksEnabled(true);
        line.setTerrainAvoidanceEnabled(true);
        project.addLine(line);

        PowerLineProject restored = PowerLineProject.fromJson(project.toJson());
        PowerLineFootprint restoredLine = restored.getLine(line.getId());
        assertNotNull(restoredLine);
        assertTrue(restoredLine.isLineChecksEnabled());
        assertTrue(restoredLine.isTerrainAvoidanceEnabled());
        assertTrue(project.toJson().contains("\"lineChecksEnabled\""));
    }

    @Test
    void legacyEngineeringAnalysisEnabledJsonStillLoads() {
        String legacyJson = """
            {
              "lines": [{
                "id": "line-legacy-checks",
                "pathPoints": [{"x": 0, "y": 0}, {"x": 40, "y": 0}],
                "engineeringAnalysisEnabled": true
              }]
            }
            """;
        PowerLineProject restored = PowerLineProject.fromJson(legacyJson);
        PowerLineFootprint line = restored.getLine("line-legacy-checks");
        assertNotNull(line);
        assertTrue(line.isLineChecksEnabled());
    }

    @Test
    void legacyGroundWireMaterialJsonStillLoads() {
        String legacyJson = """
            {
              "lines": [{
                "id": "line-legacy",
                "pathPoints": [{"x": 0, "y": 0}, {"x": 40, "y": 0}],
                "groundWireMaterial": "minecraft:chain"
              }]
            }
            """;
        PowerLineProject restored = PowerLineProject.fromJson(legacyJson);
        PowerLineFootprint line = restored.getLine("line-legacy");
        assertNotNull(line);
        assertEquals("minecraft:chain", line.getTopWireMaterial().getPrimaryMaterial());
    }

    @Test
    void stylePresetIdJsonRoundTrip() {
        PowerLineProject project = new PowerLineProject();
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        line.setStylePresetId("pack/rustic_wood");
        project.addLine(line);

        PowerLineProject restored = PowerLineProject.fromJson(project.toJson());
        PowerLineFootprint restoredLine = restored.getLine(line.getId());
        assertNotNull(restoredLine);
        assertEquals("pack/rustic_wood", restoredLine.getStylePresetId());
        assertTrue(project.toJson().contains("\"stylePresetId\""));
        assertTrue(!project.toJson().contains("\"stylePackId\""));
    }

    @Test
    void legacyStylePackIdJsonStillLoads() {
        String legacyJson = """
            {
              "lines": [{
                "id": "line-legacy-style",
                "pathPoints": [{"x": 0, "y": 0}, {"x": 40, "y": 0}],
                "stylePackId": "pack/japanese_street"
              }]
            }
            """;
        PowerLineProject restored = PowerLineProject.fromJson(legacyJson);
        PowerLineFootprint line = restored.getLine("line-legacy-style");
        assertNotNull(line);
        assertEquals("pack/japanese_street", line.getStylePresetId());
    }
}
