package com.plot.plugin.powerline.design.parametric;

import com.plot.plugin.powerline.style.TowerFamilyRoleParametricCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerStabilityParametersTest {

    @Test
    void tallSmallLatticeRoleGainsWiderBaseWhenHeightScales() {
        TowerParameterSet role = TowerParameterSet.smallLatticeDefaults();
        TowerParameterSet familyDefault = TowerParameterSet.classicDefaults();
        TowerParameterSet tuned = new TowerParameterSet(72.0, 14.6, 24.0, 1.0, 1.0, null, StructureDensity.MEDIUM);

        TowerParameterSet merged = TowerFamilyRoleParametricCatalog.mergeTunedParameters(
            role, familyDefault, tuned);

        assertTrue(merged.baseWidth() > role.baseWidth() + 2.0);
        assertTrue(merged.height() / merged.baseWidth() <= TowerStabilityParameters.MAX_HEIGHT_TO_BASE_WIDTH + 0.05);
    }

    @Test
    void enforceStableBaseWidthScalesWithHeightOnlyChanges() {
        TowerParameterSet baseline = TowerParameterSet.smallLatticeDefaults();
        TowerParameterSet taller = new TowerParameterSet(
            48.0,
            baseline.baseWidth(),
            baseline.armSpan(),
            baseline.depthScale(),
            baseline.waistRatio(),
            baseline.armLevelScales(),
            baseline.density());

        TowerParameterSet stable = TowerStabilityParameters.enforceStableBaseWidth(taller, baseline);

        double expected = Math.max(
            48.0 / TowerStabilityParameters.MAX_HEIGHT_TO_BASE_WIDTH,
            baseline.baseWidth() * (48.0 / baseline.height()));
        assertEquals(expected, stable.baseWidth(), 0.2);
    }
}
