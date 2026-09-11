package com.plot.plugin.powerline.design;

import com.plot.plugin.powerline.PowerLineAttachmentResolver;
import com.plot.plugin.powerline.PoleFrame;
import com.plot.plugin.powerline.design.parametric.TowerParametricDesignFactory;
import com.plot.plugin.powerline.design.parametric.TowerParametricEditor;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.design.structure.TowerStructurePresets;
import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConductorAttachmentBindingV2Test {

    @Test
    void boundAttachmentResolvesLateralFromNormalizedPositionAndReach() {
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.addArm(new TowerArm("arm_lower", 36, 12));
        TowerArm arm = structure.getArms().getFirst();

        ConductorAttachment attachment = new ConductorAttachment("phase_a", "A");
        attachment.setBindingMode(AttachmentBindingMode.BOUND);
        attachment.setArmId("arm_lower");
        attachment.setNormalizedPosition(-1.0);
        attachment.setVerticalAnchorOffset(0.0);

        TowerArmAttachmentBinding.ResolvedLocalOffsets local =
            TowerArmAttachmentBinding.resolveLocalOffsets(attachment, structure);
        assertEquals(-10.2, local.lateral(), 0.01);
        assertEquals(36.0, local.vertical(), 0.01);

        arm.setLateralReach(14.0);
        TowerArmAttachmentBinding.ResolvedLocalOffsets wider =
            TowerArmAttachmentBinding.resolveLocalOffsets(attachment, structure);
        assertEquals(-11.9, wider.lateral(), 0.01);
    }

    @Test
    void createThreePhaseDeckUsesBoundNormalizedPositions() {
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.addArm(new TowerArm("arm_test", 40, 10));
        TowerArm arm = structure.getArms().getFirst();

        for (ConductorAttachment attachment : TowerArmAttachmentBinding.createThreePhaseDeck(arm)) {
            assertTrue(attachment.isBound());
            assertEquals("arm_test", attachment.getArmId());
        }

        TowerArmAttachmentBinding.ResolvedLocalOffsets left =
            TowerArmAttachmentBinding.resolveLocalOffsets(
                TowerArmAttachmentBinding.createThreePhaseDeck(arm).get(0),
                structure);
        assertEquals(-8.5, left.lateral(), 0.01);
        assertEquals(40.0, left.vertical(), 0.01);
    }

    @Test
    void legacyArmIdMigratesToBoundOnEnsureV2Bindings() {
        TowerStructureDesign structure = TowerStructurePresets.classicDoubleArmTower();
        PoleDesign design = new PoleDesign("legacy", "Legacy");
        design.setTowerStructure(structure);

        ConductorAttachment attachment = new ConductorAttachment("phase_a", "A");
        attachment.setArmId("arm_lower");
        attachment.setLateralOffset(-10.2);
        attachment.setVerticalOffset(26.0);
        design.addAttachment(attachment);

        TowerArmAttachmentBinding.ensureV2Bindings(design);
        ConductorAttachment stored = design.getAttachments().getFirst();
        assertTrue(stored.isBound());
        assertEquals(-1.0, stored.getNormalizedPosition(), 0.01);
        assertEquals(0.0, stored.getVerticalAnchorOffset(), 0.01);
    }

    @Test
    void boundAttachmentSerializationRoundTrips() {
        TowerStructureDesign structure = TowerStructurePresets.smallLatticeTower();
        PoleDesign design = new PoleDesign("bound", "Bound");
        design.setTowerStructure(structure);
        design.setAttachments(TowerArmAttachmentBinding.createThreePhaseDeck(structure.getArms().getFirst()));

        PoleDesign restored = PoleDesign.fromJson(design.toJson());
        ConductorAttachment restoredAttachment = restored.getAttachments().getFirst();
        assertTrue(restoredAttachment.isBound());
        assertEquals(-1.0, restoredAttachment.getNormalizedPosition(), 0.01);

        TowerArmAttachmentBinding.ResolvedLocalOffsets before =
            TowerArmAttachmentBinding.resolveLocalOffsets(
                design.getAttachments().getFirst(),
                structure);
        TowerArmAttachmentBinding.ResolvedLocalOffsets after =
            TowerArmAttachmentBinding.resolveLocalOffsets(
                restoredAttachment,
                restored.getTowerStructure());
        assertEquals(before.lateral(), after.lateral(), 0.01);
        assertEquals(before.vertical(), after.vertical(), 0.01);
    }

    @Test
    void powerLineAttachmentResolverUsesBoundGeometry() {
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.addArm(new TowerArm("arm_main", 20, 10));
        TowerArm arm = structure.getArms().getFirst();

        PoleDesign design = new PoleDesign("resolve", "Resolve");
        design.setTowerStructure(structure);
        ConductorAttachment attachment = TowerArmAttachmentBinding.createThreePhaseDeck(arm).get(0);
        design.addAttachment(attachment);

        PowerLineAttachmentResolver resolver = new PowerLineAttachmentResolver(
            com.plot.test.world.IdentityCoordinateService.INSTANCE);
        PoleFrame frame = PoleFrame.fromPole(new Vec2d(0, 0), new Vec2d(1, 0), 64);

        assertEquals(84.0, resolver.resolve(design, frame).getFirst().conductorWorldY(), 0.01);

        arm.setLateralReach(12.0);
        ConductorAttachment stored = design.getAttachments().getFirst();
        TowerArmAttachmentBinding.ResolvedLocalOffsets local =
            TowerArmAttachmentBinding.resolveLocalOffsets(stored, structure);
        assertEquals(-10.2, local.lateral(), 0.01);
        assertEquals(84.0, resolver.resolve(design, frame).getFirst().conductorWorldY(), 0.01);
    }

    @Test
    void parametricRecompilePreservesBoundAttachments() {
        PoleDesign design = new PoleDesign("parametric", "Parametric");
        TowerParametricEditor.enableParametricClassic(design, TowerParameterSet.classicDefaults());
        int attachmentCount = design.getAttachments().size();
        String firstId = design.getAttachments().getFirst().getId();
        double normalized = design.getAttachments().getFirst().getNormalizedPosition();

        design.setGeneratorConfig(design.getGeneratorConfig().withParameters(
            new TowerParameterSet(48.0, 13.0, 24.0, 1.0, design.getGeneratorConfig().parameters().density())));
        TowerParametricEditor.recompile(design, null);

        assertEquals(attachmentCount, design.getAttachments().size());
        assertEquals(firstId, design.getAttachments().getFirst().getId());
        assertEquals(normalized, design.getAttachments().getFirst().getNormalizedPosition(), 0.01);
        assertTrue(design.getAttachments().stream().anyMatch(ConductorAttachment::isBound));
        assertEquals(48.0, design.getTowerStructure().maxHeight(), 0.5);
    }
}
