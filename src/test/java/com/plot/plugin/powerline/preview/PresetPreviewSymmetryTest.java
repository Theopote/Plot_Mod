package com.plot.plugin.powerline.preview;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.design.parametric.TowerParametricDesignFactory;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.plugin.powerline.design.structure.BracingPattern;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerBay;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.style.PowerLineStyleParametricCatalog;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** PL-PRESET-S6：应对称 preset 的结构对称性（斜撑/横担，非体素离散）。 */
class PresetPreviewSymmetryTest {

    @Test
    void steampunkParametricUsesSymmetricBracingOnly() {
        PoleDesign compiled = TowerParametricDesignFactory.compileSteampunk(TowerParameterSet.steampunkDefaults());
        assertSymmetricBracing(compiled.getTowerStructure());
    }

    @Test
    void steampunkUpperArmUsesSymmetricXBracing() {
        PoleDesign compiled = TowerParametricDesignFactory.compileSteampunk(TowerParameterSet.steampunkDefaults());
        TowerArm upper = compiled.getTowerStructure().getArms().stream()
            .filter(arm -> "arm_rod".equals(arm.getId()))
            .findFirst()
            .orElseThrow();
        assertEquals(BracingPattern.X, upper.getBracing());
    }

    @Test
    void modernHvGlassParametricUsesSymmetricBracingOnly() {
        PoleDesign compiled = PowerLineStyleParametricCatalog.compileRepresentative(
            TowerGeneratorConfig.parametricModernHvGlass(TowerParameterSet.modernHvGlassDefaults()));
        assertSymmetricBracing(compiled.getTowerStructure());
    }

    private static void assertSymmetricBracing(TowerStructureDesign structure) {
        for (TowerArm arm : structure.getArms()) {
            assertNotEquals(
                BracingPattern.SINGLE_DIAGONAL,
                arm.getBracing(),
                "arm " + arm.getId() + " must not use single diagonal bracing");
            assertEquals(
                BracingPattern.X,
                arm.getBracing(),
                "arm " + arm.getId() + " should use symmetric X bracing");
        }
        for (TowerBay bay : structure.getBays()) {
            assertNotEquals(
                BracingPattern.SINGLE_DIAGONAL,
                bay.getFrontBackBracing(),
                "bay " + bay.getLowerStationId() + "->" + bay.getUpperStationId()
                    + " front bracing must be symmetric");
            assertNotEquals(
                BracingPattern.SINGLE_DIAGONAL,
                bay.getSideBracing(),
                "bay " + bay.getLowerStationId() + "->" + bay.getUpperStationId()
                    + " side bracing must be symmetric");
        }
    }
}
