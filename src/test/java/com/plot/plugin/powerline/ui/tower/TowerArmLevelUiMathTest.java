package com.plot.plugin.powerline.ui.tower;

import com.plot.plugin.powerline.design.parametric.ParameterRange;
import com.plot.plugin.powerline.design.parametric.TowerParameterProfiles;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TowerArmLevelUiMathTest {

    @Test
    void scaleRoundTripsThroughBlockHeight() {
        var profile = TowerParameterProfiles.classicDoubleArm();
        var template = profile.armTemplates().getFirst();
        double towerHeight = TowerParameterSet.classicDefaults().height();
        double scale = 1.05;

        double armHeight = TowerArmLevelUiMath.resolvedArmHeight(towerHeight, template, scale);
        double roundTrip = TowerArmLevelUiMath.scaleFromArmHeight(armHeight, towerHeight, template);

        assertEquals(scale, roundTrip, 0.001);
    }

    @Test
    void requestedHeightClampsToArmLevelRange() {
        var profile = TowerParameterProfiles.classicDoubleArm();
        var template = profile.armTemplates().getFirst();
        double towerHeight = TowerParameterSet.classicDefaults().height();
        double extreme = towerHeight * template.heightRatio() * 5.0;

        double scale = TowerArmLevelUiMath.scaleFromArmHeight(extreme, towerHeight, template);

        assertEquals(ParameterRange.ARM_LEVEL.max(), scale, 0.001);
    }

    @Test
    void displayHeightUsesCurrentTowerHeight() {
        var profile = TowerParameterProfiles.classicDoubleArm();
        var template = profile.armTemplates().get(1);
        TowerParameterSet parameters = new TowerParameterSet(
            48.0,
            13.0,
            24.0,
            1.0,
            1.0,
            java.util.List.of(1.0, 0.95),
            com.plot.plugin.powerline.design.parametric.StructureDensity.MEDIUM);

        double display = TowerArmLevelUiMath.displayArmHeight(profile, parameters, template, 1);
        double expected = 48.0 * template.heightRatio() * 0.95;

        assertEquals(expected, display, 0.01);
    }
}
