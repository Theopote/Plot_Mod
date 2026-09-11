package com.plot.plugin.powerline.design.parametric;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerParametricAdvancedParametersTest {

    @Test
    void depthScaleAffectsStationDepthWithoutChangingArmReach() {
        TowerParameterSet defaults = TowerParameterSet.classicDefaults();
        PoleDesign baseline = TowerParametricDesignFactory.compileClassicDoubleArm(defaults);
        PoleDesign deeper = TowerParametricDesignFactory.compileClassicDoubleArm(
            new TowerParameterSet(
                defaults.height(),
                defaults.baseWidth(),
                defaults.armSpan(),
                1.25,
                defaults.waistRatio(),
                defaults.armLevelScales(),
                defaults.density()));

        TowerStation base = baseline.getTowerStructure().sortedStations().get(0);
        TowerStation deepBase = deeper.getTowerStructure().sortedStations().get(0);
        assertTrue(deepBase.getHalfDepth() > base.getHalfDepth());
        assertEquals(
            baseline.getTowerStructure().getArms().get(0).getLateralReach(),
            deeper.getTowerStructure().getArms().get(0).getLateralReach(),
            0.01);
    }

    @Test
    void waistRatioScalesWaistAndShoulderWithoutMovingArms() {
        TowerParameterSet defaults = TowerParameterSet.classicDefaults();
        PoleDesign baseline = TowerParametricDesignFactory.compileClassicDoubleArm(defaults);
        PoleDesign widerWaist = TowerParametricDesignFactory.compileClassicDoubleArm(
            new TowerParameterSet(
                defaults.height(),
                defaults.baseWidth(),
                defaults.armSpan(),
                defaults.depthScale(),
                1.2,
                defaults.armLevelScales(),
                defaults.density()));

        TowerStructureDesign baselineStructure = baseline.getTowerStructure();
        TowerStructureDesign widerStructure = widerWaist.getTowerStructure();
        TowerStation baselineWaist = findStation(baselineStructure, "s3");
        TowerStation widerWaistStation = findStation(widerStructure, "s3");
        TowerStation baselineShoulder = findStation(baselineStructure, "s4");
        TowerStation widerShoulder = findStation(widerStructure, "s4");
        TowerStation baselineBase = findStation(baselineStructure, "s0");
        TowerStation widerBase = findStation(widerStructure, "s0");

        assertTrue(widerWaistStation.getHalfWidth() > baselineWaist.getHalfWidth());
        assertTrue(widerShoulder.getHalfWidth() > baselineShoulder.getHalfWidth());
        assertEquals(baselineBase.getHalfWidth(), widerBase.getHalfWidth(), 0.01);
        assertEquals(
            baselineStructure.getArms().get(0).getLateralReach(),
            widerStructure.getArms().get(0).getLateralReach(),
            0.01);
    }

    @Test
    void smallLatticeProfileDoesNotExposeWaistControl() {
        assertFalse(TowerParameterProfiles.smallLattice().hasWaistControl());
        assertTrue(TowerParameterProfiles.classicDoubleArm().hasWaistControl());
    }

    @Test
    void armLevelScalesMoveArmsWithoutChangingReach() {
        TowerParameterSet defaults = TowerParameterSet.classicDefaults();
        PoleDesign baseline = TowerParametricDesignFactory.compileClassicDoubleArm(defaults);
        PoleDesign adjusted = TowerParametricDesignFactory.compileClassicDoubleArm(
            defaults.withArmLevelScales(List.of(1.05, 1.0)));

        TowerArm baselineLower = findArm(baseline.getTowerStructure(), "arm_lower");
        TowerArm adjustedLower = findArm(adjusted.getTowerStructure(), "arm_lower");
        TowerArm baselineUpper = findArm(baseline.getTowerStructure(), "arm_upper");
        TowerArm adjustedUpper = findArm(adjusted.getTowerStructure(), "arm_upper");

        assertTrue(adjustedLower.getBaseHeight() > baselineLower.getBaseHeight());
        assertEquals(baselineUpper.getBaseHeight(), adjustedUpper.getBaseHeight(), 0.01);
        assertEquals(baselineLower.getLateralReach(), adjustedLower.getLateralReach(), 0.01);
        assertEquals(baselineUpper.getLateralReach(), adjustedUpper.getLateralReach(), 0.01);
    }

    @Test
    void armLevelScalesRoundTripThroughGeneratorConfigJson() {
        PoleDesign design = new PoleDesign("arm-levels", "Arm Levels");
        design.setGeneratorConfig(TowerGeneratorConfig.parametricClassic(
            new TowerParameterSet(36.0, 13.0, 24.0, 1.0, 1.0, List.of(1.05, 0.95), StructureDensity.MEDIUM)));

        PoleDesign restored = PoleDesign.fromJson(design.toJson());
        List<Double> scales = restored.getGeneratorConfig().parameters().armLevelScales();
        assertEquals(2, scales.size());
        assertEquals(1.05, scales.get(0), 0.01);
        assertEquals(0.95, scales.get(1), 0.01);
    }

    @Test
    void waistRatioRoundTripsThroughGeneratorConfigJson() {
        PoleDesign design = new PoleDesign("advanced", "Advanced");
        design.setGeneratorConfig(TowerGeneratorConfig.parametricClassic(
            new TowerParameterSet(36.0, 13.0, 24.0, 1.1, 0.9, List.of(1.05, 0.95), StructureDensity.MEDIUM)));

        PoleDesign restored = PoleDesign.fromJson(design.toJson());
        TowerParameterSet parameters = restored.getGeneratorConfig().parameters();
        assertEquals(1.1, parameters.depthScale(), 0.01);
        assertEquals(0.9, parameters.waistRatio(), 0.01);
        assertEquals(1.05, parameters.armLevelScales().get(0), 0.01);
    }

    private static TowerArm findArm(TowerStructureDesign structure, String id) {
        return structure.getArms().stream()
            .filter(arm -> id.equals(arm.getId()))
            .findFirst()
            .orElseThrow();
    }

    private static TowerStation findStation(TowerStructureDesign structure, String id) {
        return structure.getStations().stream()
            .filter(station -> id.equals(station.getId()))
            .findFirst()
            .orElseThrow();
    }
}
