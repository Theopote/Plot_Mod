package com.plot.plugin.powerline.design;

import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import com.plot.plugin.powerline.equipment.InsulatorType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConductorArrangementTest {

    @Test
    void heavyDoubleCircuitHasSixPhaseChannelsAndTwinTop() {
        ConductorArrangement arrangement = ConductorArrangement.heavyDoubleCircuit();
        assertEquals(ConductorArrangement.CATALOG_DOUBLE_CIRCUIT, arrangement.getCatalogId());
        assertEquals(6, arrangement.phaseConductorCount());
        assertEquals(8, arrangement.getChannels().size());
        assertTrue(arrangement.getChannels().stream().anyMatch(c -> "left_phase_a".equals(c.id())));
        assertTrue(arrangement.getChannels().stream().anyMatch(c -> "top_wire_l".equals(c.id())));
    }

    @Test
    void monsterQuadCircuitHasTwelvePhaseChannels() {
        ConductorArrangement arrangement = ConductorArrangement.monsterQuadCircuit();
        assertEquals(12, arrangement.phaseConductorCount());
        assertEquals(14, arrangement.getChannels().size());
    }

    @Test
    void megaLatticeDesignMatchesArrangementChannelCount() {
        var design = TowerFamilyDesignPresets.megaLatticeSuspension();
        assertEquals(5, design.getAttachments().size());
        assertEquals(3, ConductorArrangement.megaIndustrialBundled().phaseConductorCount());
    }

    @Test
    void doubleCircuitDrumHasSixPhaseConductorsAndTwinTop() {
        ConductorArrangement arrangement = ConductorArrangement.doubleCircuitDrum();
        assertEquals(ConductorArrangement.CATALOG_DOUBLE_CIRCUIT_DRUM, arrangement.getCatalogId());
        assertEquals(6, arrangement.phaseConductorCount());
        assertEquals(8, arrangement.getChannels().size());

        List<ConductorAttachment> attachments = arrangement.toAttachments(44, InsulatorType.SUSPENSION, 4);
        assertEquals(8, attachments.size());

        long topWires = attachments.stream()
            .filter(a -> a.getRole() == AttachmentRole.TOP_WIRE)
            .count();
        assertEquals(2, topWires);

        Set<Double> deckHeights = attachments.stream()
            .filter(a -> a.getRole() != AttachmentRole.TOP_WIRE)
            .map(ConductorAttachment::getVerticalOffset)
            .collect(Collectors.toSet());
        assertEquals(Set.of(36.0, 44.0, 52.0), deckHeights);

        assertAttachmentNear(attachments, "left_c", -11, 36);
        assertAttachmentNear(attachments, "right_c", 11, 36);
        assertAttachmentNear(attachments, "left_b", -14, 44);
        assertAttachmentNear(attachments, "right_b", 14, 44);
        assertAttachmentNear(attachments, "left_a", -11, 52);
        assertAttachmentNear(attachments, "right_a", 11, 52);
    }

    @Test
    void heavyDoubleCircuitDesignUsesDrumArrangement() {
        var design = TowerFamilyDesignPresets.heavyDoubleCircuitSuspension();
        assertEquals(8, design.getAttachments().size());
        assertEquals(6, ConductorArrangement.doubleCircuitDrum().phaseConductorCount());
    }

    @Test
    void monsterPylonDesignHasQuadCircuitAttachments() {
        var design = TowerFamilyDesignPresets.monsterPylonSuspension();
        assertEquals(14, design.getAttachments().size());
    }

    private static void assertAttachmentNear(
            List<ConductorAttachment> attachments,
            String id,
            double lateral,
            double vertical) {
        ConductorAttachment attachment = attachments.stream()
            .filter(a -> id.equals(a.getId()))
            .findFirst()
            .orElseThrow();
        assertEquals(lateral, attachment.getLateralOffset(), 0.01);
        assertEquals(vertical, attachment.getVerticalOffset(), 0.01);
    }
}
