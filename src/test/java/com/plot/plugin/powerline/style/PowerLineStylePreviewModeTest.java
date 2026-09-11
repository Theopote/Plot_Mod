package com.plot.plugin.powerline.style;

import com.plot.plugin.powerline.design.AttachmentRole;
import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerArmShape;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.preview.BlockPreviewColors;
import com.plot.plugin.powerline.preview.TowerStructuralElevationRenderer;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Preview v3 混合预览：模式路由、结构比例、叠加层与可区分性。 */
class PowerLineStylePreviewModeTest {

    @Test
    void classicWoodUsesVoxelPreview() {
        assertEquals(
            PreviewRepresentation.VOXEL_FRONT,
            PowerLineStylePreviewBinding.previewRepresentation(PowerLineStylePresetCatalog.classicWood()));
        assertEquals(
            PreviewOverlay.DECORATIVE_CONDUCTORS,
            PowerLineStylePreviewBinding.previewOverlay(PowerLineStylePresetCatalog.classicWood()));
    }

    @Test
    void classicLatticeUsesStructuralPreview() {
        assertEquals(
            PreviewRepresentation.STRUCTURAL_FRONT,
            PowerLineStylePreviewBinding.previewRepresentation(PowerLineStylePresetCatalog.classicLattice()));
    }

    @Test
    void megaUsesStructuralPreview() {
        assertEquals(
            PreviewRepresentation.STRUCTURAL_FRONT,
            PowerLineStylePreviewBinding.previewRepresentation(PowerLineStylePresetCatalog.megaLattice()));
    }

    @Test
    void classicLatticeHasTwoArmLevels() {
        PoleDesign design = PowerLineStylePreviewBinding.previewDesign(PowerLineStylePresetCatalog.classicLattice());
        assertEquals(2, armCount(design));
    }

    @Test
    void tripleArmHasThreeArmLevels() {
        PoleDesign design = PowerLineStylePreviewBinding.previewDesign(PowerLineStylePresetCatalog.tripleArmTower());
        assertEquals(3, armCount(design));
    }

    @Test
    void cupTowerHasOneDominantWideUpsweepArm() {
        PoleDesign design = PowerLineStylePreviewBinding.previewDesign(PowerLineStylePresetCatalog.cupTower());
        List<TowerArm> arms = design.getTowerStructure().getArms();
        assertEquals(1, arms.size());
        TowerArm arm = arms.get(0);
        assertEquals(TowerArmShape.UPSWEEP, arm.getShape());
        assertTrue(arm.getLateralReach() >= 15.0, "cup arm should be visually wide");
    }

    @Test
    void megaMiddleReachLargerThanUpperAndLower() {
        PoleDesign design = PowerLineStylePreviewBinding.previewDesign(PowerLineStylePresetCatalog.megaLattice());
        List<TowerArm> arms = sortedArms(design);
        assertEquals(3, arms.size());
        double lower = arms.get(0).getLateralReach();
        double middle = arms.get(1).getLateralReach();
        double upper = arms.get(2).getLateralReach();
        assertTrue(middle > lower, "middle deck wider than lower");
        assertTrue(middle > upper, "middle deck wider than upper");
    }

    @Test
    void uhvMainReachIsLargestArm() {
        PoleDesign design = PowerLineStylePreviewBinding.previewDesign(PowerLineStylePresetCatalog.monsterPylon());
        List<TowerArm> arms = sortedArms(design);
        assertTrue(arms.size() >= 3);
        double maxReach = arms.stream().mapToDouble(TowerArm::getLateralReach).max().orElse(0);
        TowerArm mainArm = arms.stream()
            .max(Comparator.comparingDouble(TowerArm::getLateralReach))
            .orElseThrow();
        assertEquals(maxReach, mainArm.getLateralReach(), 0.01);
        assertTrue(mainArm.getLateralReach() >= 14.0);
    }

    @Test
    void classicWoodHasSingleConductorAttachmentHint() {
        PoleDesign design = PowerLineStylePreviewBinding.previewDesign(PowerLineStylePresetCatalog.classicWood());
        assertEquals(1, countPhaseAttachments(design));
    }

    @Test
    void doubleWoodHasThreePhaseAttachmentHints() {
        PoleDesign design = PowerLineStylePreviewBinding.previewDesign(PowerLineStylePresetCatalog.doubleWood());
        assertEquals(3, countPhaseAttachments(design));
    }

    @Test
    void heavyDoubleCircuitHasSixPhaseAttachmentHints() {
        PoleDesign design = PowerLineStylePreviewBinding.previewDesign(PowerLineStylePresetCatalog.heavyDoubleCircuit());
        assertEquals(6, countPhaseAttachments(design));
    }

    @Test
    void uhvHasExpectedPhaseAttachmentCount() {
        PoleDesign design = PowerLineStylePreviewBinding.previewDesign(PowerLineStylePresetCatalog.monsterPylon());
        assertEquals(12, countPhaseAttachments(design));
        assertEquals(2, countTopWireAttachments(design));
    }

    @Test
    void builtinPreviewBlocksHaveExplicitColors() {
        List<String> criticalBlocks = List.of(
            "minecraft:oak_slab",
            "minecraft:dark_oak_slab",
            "minecraft:birch_slab",
            "minecraft:spruce_slab",
            "minecraft:mossy_cobblestone_slab",
            "minecraft:gold_block",
            "minecraft:cut_copper",
            "minecraft:oxidized_copper",
            "minecraft:orange_terracotta",
            "minecraft:sea_lantern",
            "minecraft:soul_lantern",
            "minecraft:cobweb",
            "minecraft:quartz_block",
            "minecraft:iron_trapdoor");
        for (String blockId : criticalBlocks) {
            assertTrue(BlockPreviewColors.hasExplicitColor(blockId), blockId);
        }
    }

    @Test
    void cupAndTripleStructuralBoundsDiffer() {
        PoleDesign cup = PowerLineStylePreviewBinding.previewDesign(PowerLineStylePresetCatalog.cupTower());
        PoleDesign triple = PowerLineStylePreviewBinding.previewDesign(PowerLineStylePresetCatalog.tripleArmTower());
        assertNotEquals(structuralAspectRatio(cup), structuralAspectRatio(triple), 0.02);
        assertNotEquals(maxArmReach(cup), maxArmReach(triple), 0.5);
    }

    @Test
    void portalWidthRatioGreaterThanClassic() {
        PoleDesign portal = PowerLineStylePreviewBinding.previewDesign(PowerLineStylePresetCatalog.industrialPortal());
        PoleDesign classic = PowerLineStylePreviewBinding.previewDesign(PowerLineStylePresetCatalog.classicLattice());
        assertTrue(
            structuralWidthHeightRatio(portal) > structuralWidthHeightRatio(classic),
            "portal should read wider relative to height");
    }

    @Test
    void structuralLayoutPreservesFullAspectRatio() {
        PoleDesign design = PowerLineStylePreviewBinding.previewDesign(PowerLineStylePresetCatalog.megaLattice());
        TowerStructureDesign structure = design.getTowerStructure();
        float cardW = 116f;
        float cardH = 92f;
        TowerStructuralElevationRenderer.StructuralLayout layout =
            TowerStructuralElevationRenderer.computeLayout(structure, 0, 0, cardW, cardH);
        assertTrue(layout != null);
        double modelAspect = maxModelHalfWidth(structure) * 2.0 / structure.maxHeight();
        double screenAspect = cardW / cardH;
        double fittedAspect = modelAspect <= screenAspect
            ? modelAspect
            : screenAspect * (modelAspect / (maxModelHalfWidth(structure) * 2.0 / structure.maxHeight()));
        assertTrue(layout.scale() > 0);
        assertEquals(modelAspect, fittedAspect, 0.05, "layout should not crop or stretch tower head");
    }

    @Test
    void smartTowersUseMediumRepresentativeAndAdaptiveMarker() {
        PowerLineStylePreset smart = PowerLineStylePresetCatalog.smartTowers();
        assertTrue(PowerLineStylePreviewBinding.usesAdaptiveHeightMarker(smart));
        assertEquals(
            PreviewRepresentation.STRUCTURAL_FRONT,
            PowerLineStylePreviewBinding.previewRepresentation(smart));
    }

    @Test
    void wastelandWindUsesVoxelWithWindRotorOverlay() {
        PowerLineStylePreset wind = PowerLineStylePresetCatalog.wastelandWind();
        assertEquals(PreviewRepresentation.VOXEL_FRONT, PowerLineStylePreviewBinding.previewRepresentation(wind));
        assertEquals(PreviewOverlay.WIND_ROTOR, PowerLineStylePreviewBinding.previewOverlay(wind));
    }

    @Test
    void bindingForLatticeDesignStaysStructural() {
        PoleDesign design = PowerLineStylePreviewBinding.previewDesign(PowerLineStylePresetCatalog.classicLattice());
        StyleCardPreviewBinding binding = PowerLineStylePreviewBinding.bindingForDesign(
            design, PowerLineStylePresetCatalog.classicLattice());
        assertEquals(PreviewRepresentation.STRUCTURAL_FRONT, binding.representation());
        assertEquals(PreviewOverlay.ATTACHMENTS, binding.overlay());
    }

    @Test
    void bindingForWindDesignKeepsRotorOverlay() {
        PoleDesign design = PowerLineStylePreviewBinding.previewDesign(PowerLineStylePresetCatalog.wastelandWind());
        StyleCardPreviewBinding binding = PowerLineStylePreviewBinding.bindingForDesign(
            design, PowerLineStylePresetCatalog.wastelandWind());
        assertEquals(PreviewRepresentation.VOXEL_FRONT, binding.representation());
        assertEquals(PreviewOverlay.WIND_ROTOR, binding.overlay());
    }

    @Test
    void selectedPreviewBindingMatchesGalleryForLatticeAndWood() {
        assertSelectedMatchesGallery(PowerLineStylePresetCatalog.classicLattice());
        assertSelectedMatchesGallery(PowerLineStylePresetCatalog.classicWood());
        assertSelectedMatchesGallery(PowerLineStylePresetCatalog.wastelandWind());
        assertSelectedMatchesGallery(PowerLineStylePresetCatalog.megaLattice());
    }

    private static void assertSelectedMatchesGallery(PowerLineStylePreset pack) {
        StyleCardPreviewBinding gallery = PowerLineStylePreviewBinding.cardPreviewBinding(pack);
        StyleCardPreviewBinding selected = PowerLineStylePreviewBinding.bindingForDesign(gallery.design(), pack);
        assertEquals(gallery.representation(), selected.representation(), pack.getId());
        assertEquals(gallery.overlay(), selected.overlay(), pack.getId());
    }

    private static int armCount(PoleDesign design) {
        return design.getTowerStructure().getArms().size();
    }

    private static List<TowerArm> sortedArms(PoleDesign design) {
        return design.getTowerStructure().getArms().stream()
            .sorted(Comparator.comparingDouble(TowerArm::getBaseHeight))
            .toList();
    }

    private static int countPhaseAttachments(PoleDesign design) {
        int count = 0;
        for (ConductorAttachment attachment : design.getAttachments()) {
            if (!attachment.isEnabled()) {
                continue;
            }
            AttachmentRole role = attachment.getRole();
            if (role == AttachmentRole.PHASE_A
                    || role == AttachmentRole.PHASE_B
                    || role == AttachmentRole.PHASE_C) {
                count++;
            }
        }
        return count;
    }

    private static int countTopWireAttachments(PoleDesign design) {
        int count = 0;
        for (ConductorAttachment attachment : design.getAttachments()) {
            if (attachment.isEnabled() && attachment.getRole() == AttachmentRole.TOP_WIRE) {
                count++;
            }
        }
        return count;
    }

    private static double structuralAspectRatio(PoleDesign design) {
        TowerStructureDesign structure = design.getTowerStructure();
        return maxModelHalfWidth(structure) * 2.0 / structure.maxHeight();
    }

    private static double structuralWidthHeightRatio(PoleDesign design) {
        return structuralAspectRatio(design);
    }

    private static double maxArmReach(PoleDesign design) {
        return design.getTowerStructure().getArms().stream()
            .mapToDouble(TowerArm::getLateralReach)
            .max()
            .orElse(0);
    }

    private static double maxModelHalfWidth(TowerStructureDesign structure) {
        double max = structure.maxHalfWidth();
        for (TowerArm arm : structure.getArms()) {
            max = Math.max(max, arm.getLateralReach());
        }
        return max;
    }
}
