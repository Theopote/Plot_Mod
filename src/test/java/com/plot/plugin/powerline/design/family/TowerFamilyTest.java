package com.plot.plugin.powerline.design.family;

import com.plot.plugin.powerline.model.TowerRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TowerFamilyTest {

    @Test
    void roleResolvesCorrectDesign() {
        TowerFamily family = TowerFamilyCatalog.standardLattice3Phase();
        assertEquals(
            TowerFamilyDesignPresets.LATTICE_SUSPENSION_ID,
            family.getDesignId(TowerRole.SUSPENSION));
        assertEquals(
            TowerFamilyDesignPresets.LATTICE_ANGLE_ID,
            family.getDesignId(TowerRole.ANGLE));
        assertEquals(
            TowerFamilyDesignPresets.LATTICE_DEAD_END_ID,
            family.getDesignId(TowerRole.DEAD_END));
        assertEquals(
            TowerFamilyDesignPresets.LATTICE_TERMINAL_ID,
            family.getDesignId(TowerRole.TERMINAL));
    }

    @Test
    void missingRoleUsesFallback() {
        TowerFamily family = new TowerFamily("family/test", "Test");
        family.setDesignId(TowerRole.SUSPENSION, TowerFamilyDesignPresets.LATTICE_SUSPENSION_ID);
        assertEquals(null, family.getDesignId(TowerRole.ANGLE));
        assertNotNull(family.getDesignId(TowerRole.SUSPENSION));
    }

    @Test
    void familyCopyIsDeep() {
        TowerFamily original = TowerFamilyCatalog.standardLattice3Phase();
        TowerFamily copy = original.copy();
        copy.setDesignId(TowerRole.ANGLE, "custom/angle");
        assertNotEquals(copy.getDesignId(TowerRole.ANGLE), original.getDesignId(TowerRole.ANGLE));
        assertEquals(original.getDesignId(TowerRole.SUSPENSION), copy.getDesignId(TowerRole.SUSPENSION));
    }

    @Test
    void familySerializationRoundTrip() {
        TowerFamily family = TowerFamilyCatalog.standardLattice3Phase();
        TowerFamily copy = family.copy();
        copy.setName("Renamed");
        assertEquals(family.getId(), copy.getId());
        assertEquals(family.getDesignByRole(), copy.getDesignByRole());
    }
}
