package com.plot.plugin.powerline.design;

import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerStructurePresets;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerArmAttachmentBindingTest {

    @Test
    void createThreePhaseDeckBindsToArmHeightAndReach() {
        TowerArm arm = new TowerArm("arm_test", 40, 10);
        List<ConductorAttachment> deck = TowerArmAttachmentBinding.createThreePhaseDeck(arm);
        assertEquals(3, deck.size());
        for (ConductorAttachment attachment : deck) {
            assertEquals("arm_test", attachment.getArmId());
            assertEquals(40.0, attachment.getVerticalOffset());
        }
        assertEquals(-8.5, deck.get(0).getLateralOffset(), 0.01);
        assertEquals(8.5, deck.get(2).getLateralOffset(), 0.01);
    }

    @Test
    void syncBoundVerticalOffsetsFollowsArmHeightChanges() {
        TowerArm arm = new TowerArm("arm_main", 30, 8);
        ConductorAttachment attachment = new ConductorAttachment("phase_a", "A");
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
    void megaLatticeStructureHasMultipleArms() {
        PoleDesign design = new PoleDesign("test", "test");
        design.setTowerStructure(TowerStructurePresets.megaLatticeTower());
        assertTrue(TowerArmAttachmentBinding.sortedArms(design.getTowerStructure()).size() >= 3);
    }
}
