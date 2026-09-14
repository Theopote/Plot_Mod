package com.plot.plugin.powerline.preview;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import com.plot.plugin.powerline.style.PowerLineStylePreviewBinding;
import com.plot.plugin.powerline.style.PreviewOverlay;
import com.plot.plugin.powerline.style.PreviewRepresentation;
import com.plot.plugin.powerline.style.StyleCardPreviewBinding;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PL-PRESET-S6 P2：钢杆组画廊辨识度。 */
class SteelPoleGroupPreviewTest {

    private static final List<PowerLineStylePreset> STEEL_GROUP = List.of(
        PowerLineStylePresetCatalog.simpleSteel(),
        PowerLineStylePresetCatalog.urbanConcrete(),
        PowerLineStylePresetCatalog.modernUtility(),
        PowerLineStylePresetCatalog.compactLattice(),
        PowerLineStylePresetCatalog.taperedTower());

    @Test
    void utilitySteelPolesUseVoxelPreviewWithDecorativeConductors() {
        for (PowerLineStylePreset preset : List.of(
            PowerLineStylePresetCatalog.simpleSteel(),
            PowerLineStylePresetCatalog.urbanConcrete(),
            PowerLineStylePresetCatalog.modernUtility())) {
            assertEquals(PreviewRepresentation.VOXEL_FRONT, PowerLineStylePreviewBinding.previewRepresentation(preset), preset.getId());
            assertEquals(PreviewOverlay.DECORATIVE_CONDUCTORS, PowerLineStylePreviewBinding.previewOverlay(preset), preset.getId());
        }
    }

    @Test
    void latticeSteelPresetsUseStructuralPreviewWithAttachments() {
        for (PowerLineStylePreset preset : List.of(
            PowerLineStylePresetCatalog.compactLattice(),
            PowerLineStylePresetCatalog.taperedTower())) {
            assertEquals(PreviewRepresentation.STRUCTURAL_FRONT, PowerLineStylePreviewBinding.previewRepresentation(preset), preset.getId());
            assertEquals(PreviewOverlay.ATTACHMENTS, PowerLineStylePreviewBinding.previewOverlay(preset), preset.getId());
            assertTrue(PowerLineStylePreviewBinding.previewDesign(preset).hasTowerStructure(), preset.getId());
        }
    }

    @Test
    void steelGroupGalleryBindingsArePairwiseDistinct() {
        Set<String> signatures = new HashSet<>();
        for (PowerLineStylePreset preset : STEEL_GROUP) {
            StyleCardPreviewBinding binding = PowerLineStylePreviewBinding.cardPreviewBinding(preset);
            String signature = binding.representation().name()
                + "|"
                + binding.overlay().name()
                + "|"
                + previewShapeSignature(binding.design());
            assertTrue(signatures.add(signature), "duplicate gallery signature for " + preset.getId() + ": " + signature);
        }
    }

    @Test
    void simpleSteelUsesBarsShaftWithBlockCollarAndGlowCap() {
        PoleDesign design = PoleDesignCatalog.modernSteelPole();
        assertEquals("minecraft:iron_bars", design.getLayers().get(0).getMaterial().getPrimaryMaterial());
        assertEquals(PoleLayer.Shape.COLUMN, design.getLayers().get(1).getShape());
        assertEquals("minecraft:iron_block", design.getLayers().get(1).getMaterial().getPrimaryMaterial());
        assertEquals(PoleLayer.Shape.CAP, design.getLayers().get(2).getShape());
        assertEquals("minecraft:glowstone", design.getLayers().get(2).getMaterial().getPrimaryMaterial());
    }

    @Test
    void compactAndTaperedStructuralSilhouettesDiffer() {
        PoleDesign compact = PowerLineStylePreviewBinding.previewDesign(PowerLineStylePresetCatalog.compactLattice());
        PoleDesign tapered = PowerLineStylePreviewBinding.previewDesign(PowerLineStylePresetCatalog.taperedTower());
        assertNotEquals(structuralAspectRatio(compact), structuralAspectRatio(tapered), 0.02);
        assertTrue(
            structuralHeight(tapered) > structuralHeight(compact),
            "tapered tower should read taller than compact lattice");
    }

    @Test
    void taperedPresetUsesTallerWaistedDefaults() {
        PowerLineStylePreset tapered = PowerLineStylePresetCatalog.taperedTower();
        TowerParameterSet parameters = tapered.getDefinition().getParametricConfig().parameters();
        TowerParameterSet compact = PowerLineStylePresetCatalog.compactLattice()
            .getDefinition()
            .getParametricConfig()
            .parameters();
        assertTrue(parameters.height() > compact.height());
        assertTrue(parameters.waistRatio() < compact.waistRatio());
    }

    @Test
    void steelGroupSelectedPreviewMatchesGallery() {
        for (PowerLineStylePreset preset : STEEL_GROUP) {
            StyleCardPreviewBinding gallery = PowerLineStylePreviewBinding.cardPreviewBinding(preset);
            StyleCardPreviewBinding selected = PowerLineStylePreviewBinding.bindingForDesign(gallery.design(), preset);
            assertEquals(gallery.representation(), selected.representation(), preset.getId());
            assertEquals(gallery.overlay(), selected.overlay(), preset.getId());
        }
    }

    private static String previewShapeSignature(PoleDesign design) {
        if (design == null) {
            return "null";
        }
        if (design.hasTowerStructure()) {
            TowerStructureDesign structure = design.getTowerStructure();
            double maxReach = structure.getArms().stream()
                .mapToDouble(TowerArm::getLateralReach)
                .max()
                .orElse(0.0);
            return "struct|h=" + structure.maxHeight() + "|w=" + structure.maxHalfWidth() + "|r=" + maxReach;
        }
        return "voxel|h=" + design.totalHeight() + "|arms=" + design.getLayers().stream()
            .filter(layer -> layer.getShape() == PoleLayer.Shape.CROSSARM)
            .count();
    }

    private static double structuralAspectRatio(PoleDesign design) {
        TowerStructureDesign structure = design.getTowerStructure();
        double maxWidth = structure.maxHalfWidth();
        for (TowerArm arm : structure.getArms()) {
            maxWidth = Math.max(maxWidth, arm.getLateralReach());
        }
        return maxWidth * 2.0 / structure.maxHeight();
    }

    private static double structuralHeight(PoleDesign design) {
        return design.getTowerStructure().maxHeight();
    }
}
