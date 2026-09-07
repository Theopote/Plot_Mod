package com.plot.plugin.powerline.model;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.family.TowerFamily;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PowerLineProjectSchemaDTest {

    @Test
    void jsonRoundTripPreservesTowerFamilyFields() {
        PowerLineProject project = new PowerLineProject();
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        line.setTowerFamilyId(TowerFamily.STANDARD_LATTICE_3_PHASE_ID);
        line.setGroundWireMaterial(MaterialMix.single("minecraft:chain"));
        PoleOverride override = new PoleOverride(20.0);
        override.setRoleOverride(TowerRole.DEAD_END);
        line.addPoleOverride(override);
        project.addLine(line);

        PowerLineProject restored = PowerLineProject.fromJson(project.toJson());
        PowerLineFootprint restoredLine = restored.getLine(line.getId());
        assertNotNull(restoredLine);
        assertEquals(TowerFamily.STANDARD_LATTICE_3_PHASE_ID, restoredLine.getTowerFamilyId());
        assertEquals("minecraft:chain", restoredLine.getGroundWireMaterial().getPrimaryMaterial());
        assertEquals(1, restoredLine.getPoleOverrides().size());
        assertEquals(TowerRole.DEAD_END, restoredLine.getPoleOverrides().getFirst().getRoleOverride());
    }
}
