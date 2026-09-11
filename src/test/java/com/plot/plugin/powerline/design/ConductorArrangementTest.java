package com.plot.plugin.powerline.design;

import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import com.plot.plugin.powerline.design.structure.TowerStructurePresets;
import com.plot.plugin.powerline.equipment.InsulatorType;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
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
    void uhvThreeDeckHasTwelvePhaseChannels() {
        ConductorArrangement arrangement = ConductorArrangement.uhvThreeDeck();
        assertEquals(12, arrangement.phaseConductorCount());
        assertEquals(14, arrangement.getChannels().size());
    }

    @Test
    void megaLatticeDesignMatchesThreeDeckArrangement() {
        var design = TowerFamilyDesignPresets.megaLatticeSuspension();
        assertEquals(8, design.getAttachments().size());
        assertEquals(6, ConductorArrangement.megaThreeDeck().phaseConductorCount());
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
    void tripleArmDoubleCircuitAlignsWithArmHeights() {
        List<ConductorAttachment> attachments = ConductorArrangement.doubleCircuitThreeDeck()
            .toAttachments(36, InsulatorType.SUSPENSION, 3);
        Set<Double> phaseHeights = phaseHeights(attachments);
        assertEquals(Set.of(36.0, 42.0, 48.0), phaseHeights);
        assertEquals(6, attachments.stream()
            .filter(a -> a.getRole() != AttachmentRole.TOP_WIRE)
            .count());
    }

    @Test
    void tripleArmFamilyAttachmentsAlignWithArms() {
        var design = TowerFamilyDesignPresets.tripleArmSuspension();
        Set<Double> phaseHeights = phaseHeights(design.getAttachments());
        assertEquals(Set.of(36.0, 42.0, 48.0), phaseHeights);
        List<Double> armHeights = TowerStructurePresets.tripleArmTower().getArms().stream()
            .sorted(Comparator.comparingDouble(com.plot.plugin.powerline.design.structure.TowerArm::getBaseHeight))
            .map(com.plot.plugin.powerline.design.structure.TowerArm::getBaseHeight)
            .toList();
        assertEquals(List.of(36.0, 42.0, 48.0), armHeights);
    }

    @Test
    void megaThreeDeckAlignsWithArmHeights() {
        List<ConductorAttachment> attachments = ConductorArrangement.megaThreeDeck()
            .toAttachments(38, InsulatorType.SUSPENSION, 4);
        assertEquals(Set.of(38.0, 47.0, 56.0), phaseHeights(attachments));
        assertTrue(attachments.stream()
            .filter(a -> a.getRole() != AttachmentRole.TOP_WIRE)
            .allMatch(a -> a.getBundleVisual() == BundleVisual.TWIN));
    }

    @Test
    void megaLatticeFamilyAttachmentsAlignWithArms() {
        var design = TowerFamilyDesignPresets.megaLatticeSuspension();
        assertEquals(Set.of(38.0, 47.0, 56.0), phaseHeights(design.getAttachments()));
    }

    @Test
    void uhvThreeDeckAlignsWithArmHeights() {
        List<ConductorAttachment> attachments = ConductorArrangement.uhvThreeDeck()
            .toAttachments(56, InsulatorType.SUSPENSION, 5);
        Set<Double> phaseHeights = phaseHeights(attachments);
        assertEquals(Set.of(56.0, 66.0, 74.0), phaseHeights);
        long topWires = attachments.stream()
            .filter(a -> a.getRole() == AttachmentRole.TOP_WIRE)
            .count();
        assertEquals(2, topWires);
        assertTrue(attachments.stream()
            .filter(a -> a.getRole() == AttachmentRole.TOP_WIRE)
            .allMatch(a -> a.getVerticalOffset() >= 78.0 && a.getVerticalOffset() <= 82.0));
    }

    @Test
    void monsterPylonDesignHasQuadCircuitAttachments() {
        var design = TowerFamilyDesignPresets.monsterPylonSuspension();
        assertEquals(14, design.getAttachments().size());
        assertEquals(Set.of(56.0, 66.0, 74.0), phaseHeights(design.getAttachments()));
    }

    private static Set<Double> phaseHeights(List<ConductorAttachment> attachments) {
        return attachments.stream()
            .filter(a -> a.getRole() != AttachmentRole.TOP_WIRE)
            .map(ConductorAttachment::getVerticalOffset)
            .collect(Collectors.toSet());
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
