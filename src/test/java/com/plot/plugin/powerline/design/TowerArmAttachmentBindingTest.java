package com.plot.plugin.powerline.design;

import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.design.structure.TowerStructurePresets;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerArmAttachmentBindingTest {

    @Test
    void createThreePhaseDeckBindsToArmHeightAndReach() {
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.addArm(new TowerArm("arm_test", 40, 10));
        TowerArm arm = structure.getArms().getFirst();
        List<ConductorAttachment> deck = TowerArmAttachmentBinding.createThreePhaseDeck(arm);
        assertEquals(3, deck.size());
        for (ConductorAttachment attachment : deck) {
            assertEquals("arm_test", attachment.getArmId());
            assertTrue(attachment.isBound());
        }
        TowerArmAttachmentBinding.ResolvedLocalOffsets left =
            TowerArmAttachmentBinding.resolveLocalOffsets(deck.get(0), structure);
        TowerArmAttachmentBinding.ResolvedLocalOffsets right =
            TowerArmAttachmentBinding.resolveLocalOffsets(deck.get(2), structure);
        assertEquals(40.0, left.vertical(), 0.01);
        assertEquals(-9.0, left.lateral(), 0.01);
        assertEquals(9.0, right.lateral(), 0.01);
    }

    @Test
    void bindToArmPreservesFreeAttachmentPosition() {
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.addArm(new TowerArm("arm_main", 40, 10));
        TowerArm arm = structure.getArms().getFirst();

        ConductorAttachment attachment = new ConductorAttachment("free_phase", "A");
        attachment.setBindingMode(AttachmentBindingMode.FREE);
        attachment.setLateralOffset(6.0);
        attachment.setVerticalOffset(32.0);

        TowerArmAttachmentBinding.bindToArm(arm, attachment, structure);

        TowerArmAttachmentBinding.ResolvedLocalOffsets resolved =
            TowerArmAttachmentBinding.resolveLocalOffsets(attachment, structure);
        assertTrue(attachment.isBound());
        assertEquals(6.0, resolved.lateral(), 0.01);
        assertEquals(32.0, resolved.vertical(), 0.01);
    }

    @Test
    void releaseAttachmentsFromArmBakesCurrentBoundPosition() {
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.addArm(new TowerArm("arm_main", 40, 10));
        TowerArm arm = structure.getArms().getFirst();
        ConductorAttachment attachment = TowerArmAttachmentBinding.createThreePhaseDeck(arm).get(0);
        arm.setLateralReach(12.0);

        TowerArmAttachmentBinding.releaseAttachmentsFromArm(structure, List.of(attachment), arm.getId());

        assertFalse(attachment.isBound());
        assertEquals(-10.0, attachment.getLateralOffset(), 0.01);
        assertEquals(40.0, attachment.getVerticalOffset(), 0.01);
    }

    @Test
    void syncBoundVerticalOffsetsFollowsArmHeightChanges() {
        TowerArm arm = new TowerArm("arm_main", 30, 8);
        ConductorAttachment attachment = new ConductorAttachment("phase_a", "A");
        attachment.setBindingMode(AttachmentBindingMode.FREE);
        attachment.setArmId("arm_main");
        attachment.setVerticalOffset(30);
        arm.setBaseHeight(48);
        TowerArmAttachmentBinding.syncBoundVerticalOffsets(arm, List.of(attachment));
        assertEquals(48.0, attachment.getVerticalOffset());
    }

    @Test
    void inferArmBindingsGroupsMonsterPylonAttachments() {
        PoleDesign design = TowerFamilyDesignPresets.monsterPylonSuspension().copy();
        design.getAttachments().forEach(attachment -> attachment.setArmId(null));
        TowerArmAttachmentBinding.inferArmBindings(design);
        Map<String, List<ConductorAttachment>> grouped = TowerArmAttachmentBinding.groupByArm(design);
        long bound = design.getAttachments().stream()
            .filter(attachment -> attachment.getArmId() != null)
            .count();
        assertTrue(bound >= 10, "most monster pylon conductors should bind to an arm deck");
        assertNotNull(grouped);
    }

    @Test
    void releaseBoundAttachmentsForLegacyLayersBakesOffsetsAndUnbinds() {
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.addArm(new TowerArm("arm_main", 40, 10));
        TowerArm arm = structure.getArms().getFirst();
        ConductorAttachment attachment = TowerArmAttachmentBinding.createThreePhaseDeck(arm).get(0);

        PoleDesign design = new PoleDesign("legacy", "Legacy");
        design.setTowerStructure(structure);
        design.setAttachments(List.of(attachment));
        ConductorAttachment stored = design.getAttachments().getFirst();

        TowerArmAttachmentBinding.releaseBoundAttachmentsForLegacyLayers(design);

        assertFalse(stored.isBound());
        assertEquals(40.0, stored.getVerticalOffset(), 0.01);
        assertEquals(-9.0, stored.getLateralOffset(), 0.01);
    }

    @Test
    void syncAttachmentsForArmPreservesBoundRelativeVerticalWhenArmHeightChanges() {
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.addArm(new TowerArm("arm_main", 40, 10));
        TowerArm arm = structure.getArms().getFirst();
        ConductorAttachment attachment = TowerArmAttachmentBinding.createThreePhaseDeck(arm).get(0);
        attachment.setVerticalAnchorOffset(-2.0);

        arm.setBaseHeight(52.0);
        TowerArmAttachmentBinding.syncAttachmentsForArm(arm, List.of(attachment), structure);

        assertEquals(-2.0, attachment.getVerticalAnchorOffset(), 0.01);
        TowerArmAttachmentBinding.ResolvedLocalOffsets local =
            TowerArmAttachmentBinding.resolveLocalOffsets(attachment, structure);
        assertEquals(50.0, local.vertical(), 0.01);
        assertEquals(50.0, attachment.getVerticalOffset(), 0.01);
    }

    @Test
    void syncAttachmentsForArmRefreshesBoundLateralWhenReachChanges() {
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.addArm(new TowerArm("arm_main", 40, 10));
        TowerArm arm = structure.getArms().getFirst();
        ConductorAttachment attachment = TowerArmAttachmentBinding.createThreePhaseDeck(arm).get(0);

        arm.setLateralReach(12.0);
        TowerArmAttachmentBinding.syncAttachmentsForArm(arm, List.of(attachment), structure);

        assertEquals(-1.0, attachment.getNormalizedPosition(), 0.01);
        assertEquals(-10.0, attachment.getLateralOffset(), 0.01);
        TowerArmAttachmentBinding.ResolvedLocalOffsets local =
            TowerArmAttachmentBinding.resolveLocalOffsets(attachment, structure);
        assertEquals(-10.0, local.lateral(), 0.01);
    }

    @Test
    void syncAfterStructureChangeKeepsBoundRelativeVerticalWhenArmHeightChanges() {
        TowerStructureDesign structure = TowerStructurePresets.classicDoubleArmTower();
        PoleDesign design = new PoleDesign("sync", "Sync");
        design.setTowerStructure(structure);

        ConductorAttachment attachment = new ConductorAttachment("phase_a", "A");
        attachment.setArmId("arm_lower");
        attachment.setLateralOffset(-10.0);
        attachment.setVerticalOffset(26.0);
        design.addAttachment(attachment);

        TowerArmAttachmentBinding.syncAfterStructureChange(design);
        ConductorAttachment stored = design.getAttachments().getFirst();
        assertTrue(stored.isBound());
        assertEquals(0.0, stored.getVerticalAnchorOffset(), 0.01);

        TowerArm lowerArm = design.getTowerStructure().getArms().stream()
            .filter(arm -> "arm_lower".equals(arm.getId()))
            .findFirst()
            .orElseThrow();
        lowerArm.setBaseHeight(lowerArm.getBaseHeight() + 6.0);

        TowerArmAttachmentBinding.syncAfterStructureChange(design);

        assertEquals(0.0, stored.getVerticalAnchorOffset(), 0.01);
        TowerArmAttachmentBinding.ResolvedLocalOffsets local =
            TowerArmAttachmentBinding.resolveLocalOffsets(stored, design.getTowerStructure());
        assertEquals(lowerArm.getBaseHeight(), local.vertical(), 0.01);
    }

    @Test
    void shiftFreeAttachmentHeightsFollowsLegacyCrossarmMove() {
        PoleDesign design = new PoleDesign("legacy", "Legacy");
        design.addLayer(new PoleLayer(PoleLayer.Shape.COLUMN, 6, null));
        design.addLayer(new PoleLayer(PoleLayer.Shape.CROSSARM, 1, null));
        design.setAttachments(ConductorAttachmentPresets.threePhaseHorizontal(
            TowerArmAttachmentBinding.legacyCrossarmHangHeight(design)));

        PoleLayer column = design.getLayers().getFirst();
        double hangBefore = TowerArmAttachmentBinding.legacyCrossarmHangHeight(design);
        double phaseBefore = design.getAttachments().getFirst().getVerticalOffset();

        column.setHeight(10);
        TowerArmAttachmentBinding.shiftFreeAttachmentHeights(
            design,
            TowerArmAttachmentBinding.legacyCrossarmHangHeight(design) - hangBefore);

        double hangAfter = TowerArmAttachmentBinding.legacyCrossarmHangHeight(design);
        assertEquals(hangBefore + 4, hangAfter, 0.01);
        assertEquals(phaseBefore + 4, design.getAttachments().getFirst().getVerticalOffset(), 0.01);
    }

    @Test
    void megaLatticeStructureHasMultipleArms() {
        PoleDesign design = new PoleDesign("test", "test");
        design.setTowerStructure(TowerStructurePresets.megaLatticeTower());
        assertTrue(TowerArmAttachmentBinding.sortedArms(design.getTowerStructure()).size() >= 3);
    }
}
