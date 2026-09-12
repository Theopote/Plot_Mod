package com.plot.plugin.powerline.model;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.family.TowerFamily;
import net.minecraft.util.math.BlockPos;
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
    void jsonRoundTripAlwaysWritesStyleOverrides() {
        PowerLineProject project = new PowerLineProject();
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        project.addLine(line);

        String json = project.toJson();
        assertTrue(json.contains("\"styleOverrides\""));

        PowerLineProject restored = PowerLineProject.fromJson(json);
        assertNotNull(restored.getLine(line.getId()));
        assertTrue(restored.getLine(line.getId()).getStyleOverrides().isEmpty());
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
    void placedSingleTowersJsonRoundTrip() {
        PowerLineProject project = new PowerLineProject();
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        line.setName("Style Source");
        project.addLine(line);

        PlacedSingleTower tower = new PlacedSingleTower(
            new Vec2d(12.5, 3.0),
            2,
            "Lattice A",
            line.getId(),
            List.of(new BlockRecord(new BlockPos(10, 64, 20), "minecraft:air", "minecraft:oak_fence")));
        project.addPlacedSingleTower(tower);

        String json = project.toJson();
        assertTrue(json.contains("\"placedSingleTowers\""));
        assertTrue(json.contains("\"schemaVersion\": " + PowerLineProject.SCHEMA_VERSION));

        PowerLineProject restored = PowerLineProject.fromJson(json);
        assertEquals(1, restored.getPlacedSingleTowers().size());
        PlacedSingleTower restoredTower = restored.getPlacedSingleTowers().getFirst();
        assertEquals(tower.getId(), restoredTower.getId());
        assertEquals(12.5, restoredTower.getPlanPoint().x, 1e-6);
        assertEquals(3.0, restoredTower.getPlanPoint().y, 1e-6);
        assertEquals(2, restoredTower.getRotationQuadrant());
        assertEquals("Lattice A", restoredTower.getDesignLabel());
        assertEquals(line.getId(), restoredTower.getStyleLineId());
        assertEquals(1, restoredTower.getBlockRecords().size());
        assertEquals("minecraft:oak_fence", restoredTower.getBlockRecords().getFirst().newBlockId);
    }
}
