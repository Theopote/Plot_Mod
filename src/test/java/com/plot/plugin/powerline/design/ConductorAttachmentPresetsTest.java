package com.plot.plugin.powerline.design;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleLayer;
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
    void bundledThreePhaseCreatesLogicalPhaseAttachmentsWithTwinVisual() {
        var attachments = ConductorAttachmentPresets.bundledThreePhaseHorizontal(18, -9, 0, 9, 2, 0.8);
        assertEquals(3, attachments.size());
        assertTrue(attachments.stream().anyMatch(a -> ConductorAttachmentPresets.PHASE_A_ID.equals(a.getId())));
        assertTrue(attachments.stream().anyMatch(a -> ConductorAttachmentPresets.PHASE_C_ID.equals(a.getId())));
        assertTrue(attachments.stream()
            .allMatch(a -> a.getBundleVisual() == BundleVisual.TWIN));
    }

    @Test
    void heavyTransmissionDesignHasBundledPhasesAndTwinTopWires() {
        var design = TowerFamilyDesignPresets.hvTransmissionSuspension();
        assertEquals(5, design.getAttachments().size());
        long topWires = design.getAttachments().stream()
            .filter(a -> a.getRole() == AttachmentRole.TOP_WIRE)
            .count();
        assertEquals(2, topWires);
    }

    @Test
    void heavyLatticePresetExpectsSixPhaseConductors() {
        PowerLineStylePreset preset = PowerLineStylePresetCatalog.heavyLattice();
        assertEquals(TowerFamily.HEAVY_TRANSMISSION_ID, preset.getTowerFamilyId());
        assertEquals(3, preset.expectedConductorCount());
    }

    @Test
    void classicLatticeSuspensionHasBundledPhasesAndTwinTopWires() {
        var design = TowerFamilyDesignPresets.latticeSuspension();
        assertEquals(5, design.getAttachments().size());
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
        assertEquals(3, preset.expectedConductorCount());
    }

    @Test
    void createNextLayerModeAttachmentAssignsSequentialPhaseLetters() {
        PoleDesign design = new PoleDesign("test");
        design.setAttachments(ConductorAttachmentPresets.threePhaseHorizontal(12.0));

        ConductorAttachment next = ConductorAttachmentPresets.createNextLayerModeAttachment(design);
        assertEquals("D", next.getName());
        assertEquals("phase_d", next.getId());
        assertEquals(AttachmentRole.AUXILIARY, next.getRole());
    }

    @Test
    void createNextLayerModeAttachmentStartsAtAOnEmptyDesign() {
        PoleDesign design = new PoleDesign("empty");
        design.addLayer(new PoleLayer(PoleLayer.Shape.COLUMN, 8, null));

        ConductorAttachment first = ConductorAttachmentPresets.createNextLayerModeAttachment(design);
        assertEquals("A", first.getName());
        assertEquals("phase_a", first.getId());
        assertEquals(AttachmentRole.PHASE_A, first.getRole());
    }

    @Test
    void createNextLayerModeAttachmentSkipsDefaultAttachmentPlaceholderName() {
        PoleDesign design = new PoleDesign("test");
        design.addAttachment(new ConductorAttachment());

        ConductorAttachment next = ConductorAttachmentPresets.createNextLayerModeAttachment(design);
        assertEquals("A", next.getName());
    }
}
