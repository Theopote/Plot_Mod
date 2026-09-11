package com.plot.plugin.powerline.preview;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerArmSide;
import com.plot.plugin.powerline.design.structure.TowerDecorationKind;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.design.structure.TowerStructurePresets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerStructuralElevationRendererTest {

    @Test
    void lateralSpanBothCoversFullReach() {
        TowerArm arm = armWithSide(TowerArmSide.BOTH, 12.0);
        TowerStructuralElevationRenderer.ArmLateralSpan span = TowerStructuralElevationRenderer.lateralSpan(arm);
        assertEquals(-12.0, span.start(), 0.01);
        assertEquals(12.0, span.end(), 0.01);
        assertTrue(span.includesNegativeSide());
        assertTrue(span.includesPositiveSide());
    }

    @Test
    void lateralSpanLeftOnlyCoversNegativeSide() {
        TowerArm arm = armWithSide(TowerArmSide.LEFT, 10.0);
        TowerStructuralElevationRenderer.ArmLateralSpan span = TowerStructuralElevationRenderer.lateralSpan(arm);
        assertEquals(-10.0, span.start(), 0.01);
        assertEquals(0.0, span.end(), 0.01);
        assertTrue(span.includesNegativeSide());
        assertFalse(span.includesPositiveSide());
    }

    @Test
    void lateralSpanRightOnlyCoversPositiveSide() {
        TowerArm arm = armWithSide(TowerArmSide.RIGHT, 10.0);
        TowerStructuralElevationRenderer.ArmLateralSpan span = TowerStructuralElevationRenderer.lateralSpan(arm);
        assertEquals(0.0, span.start(), 0.01);
        assertEquals(10.0, span.end(), 0.01);
        assertFalse(span.includesNegativeSide());
        assertTrue(span.includesPositiveSide());
    }

    @Test
    void knownDecorationKindsAreRenderable() {
        assertTrue(TowerStructuralElevationRenderer.isRenderableDecorationKind(TowerDecorationKind.ANTENNA));
        assertTrue(TowerStructuralElevationRenderer.isRenderableDecorationKind(TowerDecorationKind.BEACON));
        assertTrue(TowerStructuralElevationRenderer.isRenderableDecorationKind(TowerDecorationKind.WARNING_LIGHT));
        assertTrue(TowerStructuralElevationRenderer.isRenderableDecorationKind(TowerDecorationKind.PLATFORM));
    }

    @Test
    void nullDecorationKindIsNotRenderable() {
        assertFalse(TowerStructuralElevationRenderer.isRenderableDecorationKind(null));
    }

    @Test
    void previewColorResolvesKnownBlockMaterial() {
        int copper = BlockPreviewColors.previewColor(MaterialMix.single("minecraft:copper_block"), 0xFF808080);
        int iron = BlockPreviewColors.previewColor(MaterialMix.single("minecraft:iron_block"), 0xFF808080);
        assertEquals(BlockPreviewColors.colorFor("minecraft:copper_block"), copper);
        assertNotEquals(copper, iron);
    }

    @Test
    void structuralPaletteUsesPrimaryAndBraceMaterials() {
        TowerStructureDesign structure = TowerStructurePresets.classicDoubleArmTower().copy();
        structure.setPrimaryMaterial(MaterialMix.single("minecraft:iron_block"));
        structure.setBraceMaterial(MaterialMix.single("minecraft:gold_block"));
        TowerStructuralElevationRenderer.StructuralPalette palette =
            TowerStructuralElevationRenderer.StructuralPalette.from(structure);
        assertEquals(BlockPreviewColors.colorFor("minecraft:iron_block"), palette.leg());
        assertEquals(BlockPreviewColors.colorFor("minecraft:gold_block"), palette.brace());
        assertEquals(BlockPreviewColors.colorFor("minecraft:gold_block"), palette.defaultArm());
    }

    @Test
    void armColorPrefersArmMaterialOverBraceDefault() {
        TowerStructureDesign structure = TowerStructurePresets.classicDoubleArmTower().copy();
        structure.setBraceMaterial(MaterialMix.single("minecraft:iron_bars"));
        TowerArm arm = structure.getArms().get(0);
        arm.setMaterial(MaterialMix.single("minecraft:copper_block"));
        TowerStructuralElevationRenderer.StructuralPalette palette =
            TowerStructuralElevationRenderer.StructuralPalette.from(structure);
        assertEquals(BlockPreviewColors.colorFor("minecraft:copper_block"), palette.armColor(arm));
    }

    @Test
    void sideLayoutUsesTowerDepthInsteadOfFrontWidth() {
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.addStation(new TowerStation("base", 0.0, 20.0, 4.0));
        structure.addStation(new TowerStation("top", 40.0, 10.0, 2.0));

        TowerStructuralElevationRenderer.StructuralLayout front = TowerStructuralElevationRenderer.computeLayout(
            structure, TowerStructuralElevationRenderer.StructuralView.FRONT, 0f, 0f, 100f, 240f);
        TowerStructuralElevationRenderer.StructuralLayout side = TowerStructuralElevationRenderer.computeLayout(
            structure, TowerStructuralElevationRenderer.StructuralView.SIDE, 0f, 0f, 100f, 240f);

        assertTrue(side.scale() > front.scale());
    }

    private static TowerArm armWithSide(TowerArmSide side, double reach) {
        TowerArm arm = new TowerArm("arm_test", 20.0, reach);
        arm.setSide(side);
        return arm;
    }
}
