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
        assertTrue(project.toJson().contains("\"topWireMaterial\""));
        assertTrue(!project.toJson().contains("\"groundWireMaterial\""));
        assertEquals(1, restoredLine.getPoleOverrides().size());
        assertEquals(TowerRole.DEAD_END, restoredLine.getPoleOverrides().getFirst().getRoleOverride());
    }

    @Test
    void schemaVersionIsWritten() {
        PowerLineProject project = new PowerLineProject();
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        project.addLine(line);
        assertTrue(project.toJson().contains("\"schemaVersion\": " + PowerLineProject.SCHEMA_VERSION));
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
    void styleOverridesJsonRoundTrip() {
        PowerLineProject project = new PowerLineProject();
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        com.plot.plugin.powerline.style.PowerLineStyleEditor.selectPreset(
            line,
            com.plot.plugin.powerline.style.PowerLineStylePresetCatalog.classicWood());
        line.setWireMaterial(MaterialMix.single("minecraft:chain"));
        line.setSagRatio(0.35);
        line.setMinPoleSpacing(12.0);
        line.setMaxPoleSpacing(48.0);
        line.setSpacingCustomized(true);
        com.plot.plugin.powerline.style.PowerLineStyleEditor.afterStyleEdit(line);
        com.plot.plugin.powerline.style.PowerLineStyleEditor.afterSpacingEdit(line);
        project.addLine(line);

        String json = project.toJson();
        assertTrue(json.contains("\"styleOverrides\""));

        PowerLineProject restored = PowerLineProject.fromJson(json);
        PowerLineFootprint restoredLine = restored.getLine(line.getId());
        assertNotNull(restoredLine);
        assertEquals("minecraft:chain", restoredLine.getWireMaterial().getPrimaryMaterial());
        assertEquals(0.35, restoredLine.getSagRatio(), 1e-6);
        assertTrue(restoredLine.isSpacingCustomized());
        assertEquals(48.0, restoredLine.getMaxPoleSpacing(), 1e-6);
        assertNotNull(restoredLine.getStyleOverrides().getWireMaterial());
        assertEquals(0.35, restoredLine.getStyleOverrides().getSagRatio(), 1e-6);
        assertNotNull(restoredLine.getStyleOverrides().getPreferredSpacing());
        assertTrue(com.plot.plugin.powerline.style.PowerLineStyleEditor.isModified(restoredLine));
    }

    @Test
    void legacyJsonWithoutStyleOverridesStillLoads() {
        String legacyJson = """
            {
              "lines": [{
                "id": "line-legacy-overrides",
                "pathPoints": [{"x": 0, "y": 0}, {"x": 40, "y": 0}],
                "stylePresetId": "pack/rustic_wood",
                "wireMaterial": "minecraft:chain",
                "sagRatio": 0.35
              }]
            }
            """;
        PowerLineProject restored = PowerLineProject.fromJson(legacyJson);
        PowerLineFootprint line = restored.getLine("line-legacy-overrides");
        assertNotNull(line);
        assertEquals("minecraft:chain", line.getWireMaterial().getPrimaryMaterial());
        assertEquals(0.35, line.getSagRatio(), 1e-6);
        assertNotNull(line.getStyleOverrides().getWireMaterial());
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
