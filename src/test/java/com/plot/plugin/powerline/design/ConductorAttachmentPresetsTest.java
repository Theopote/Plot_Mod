package com.plot.plugin.powerline.design;

import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConductorAttachmentPresetsTest {

    @Test
    void twinTopWiresCreatesTwoTopWireRoles() {
        var wires = ConductorAttachmentPresets.twinTopWires(20, 2);
        assertEquals(2, wires.size());
        assertEquals(AttachmentRole.TOP_WIRE, wires.get(0).getRole());
        assertEquals(ConductorAttachmentPresets.TOP_WIRE_L_ID, wires.get(0).getId());
        assertEquals(ConductorAttachmentPresets.TOP_WIRE_R_ID, wires.get(1).getId());
    }

    @Test
    void bundledThreePhaseCreatesSubconductorsPerPhase() {
        var attachments = ConductorAttachmentPresets.bundledThreePhaseHorizontal(18, -9, 0, 9, 2, 0.8);
        assertEquals(6, attachments.size());
        assertTrue(attachments.stream().anyMatch(a -> "phase_a_1".equals(a.getId())));
        assertTrue(attachments.stream().anyMatch(a -> "phase_c_2".equals(a.getId())));
    }

    @Test
    void heavyTransmissionDesignHasBundledPhasesAndTwinTopWires() {
        var design = TowerFamilyDesignPresets.hvTransmissionSuspension();
        assertEquals(8, design.getAttachments().size());
        long topWires = design.getAttachments().stream()
            .filter(a -> a.getRole() == AttachmentRole.TOP_WIRE)
            .count();
        assertEquals(2, topWires);
    }

    @Test
    void heavyLatticePresetExpectsSixPhaseConductors() {
        PowerLineStylePreset preset = PowerLineStylePresetCatalog.heavyLattice();
        assertEquals(TowerFamily.HEAVY_TRANSMISSION_ID, preset.getTowerFamilyId());
        assertEquals(6, preset.expectedConductorCount());
    }

    @Test
    void classicLatticeSuspensionHasBundledPhasesAndTwinTopWires() {
        var design = TowerFamilyDesignPresets.latticeSuspension();
        assertEquals(8, design.getAttachments().size());
        long topWires = design.getAttachments().stream()
            .filter(a -> a.getRole() == AttachmentRole.TOP_WIRE)
            .count();
        assertEquals(2, topWires);
    }

    @Test
    void doubleCircuitHorizontalCreatesEightAttachments() {
        var attachments = ConductorAttachmentPresets.doubleCircuitHorizontal(40, -14, -11, -8, 8, 11, 14);
        assertEquals(8, attachments.size());
    }

    @Test
    void megaLatticePresetExpectsSixPhaseConductors() {
        PowerLineStylePreset preset = PowerLineStylePresetCatalog.megaLattice();
        assertEquals(6, preset.expectedConductorCount());
    }

    @Test
    void monsterPylonPresetExpectsTwelvePhaseConductors() {
        PowerLineStylePreset preset = PowerLineStylePresetCatalog.monsterPylon();
        assertEquals(12, preset.expectedConductorCount());
    }

    @Test
    void classicLatticePresetExpectsSixPhaseConductors() {
        PowerLineStylePreset preset = PowerLineStylePresetCatalog.classicLattice();
        assertEquals(TowerFamily.STANDARD_LATTICE_3_PHASE_ID, preset.getTowerFamilyId());
        assertEquals(6, preset.expectedConductorCount());
    }
}
