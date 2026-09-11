package com.plot.plugin.powerline.design.parametric;

import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.TowerArmAttachmentBinding;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerBay;
import com.plot.plugin.powerline.design.structure.TowerSilhouette;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.design.structure.TowerStructurePresets;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerParametricSmallLatticeTest {
    private static final double EPS = 0.05;

    @Test
    void defaultSmallLatticeParametersCompileCloseToCurrentPreset() {
        PoleDesign compiled = TowerParametricDesignFactory.compileSmallLattice(TowerParameterSet.smallLatticeDefaults());
        TowerStructureDesign preset = TowerStructurePresets.smallLatticeTower();

        assertEquals(TowerSilhouette.TAPERED_LATTICE, compiled.getTowerStructure().getSilhouette());
        assertStationsClose(preset, compiled.getTowerStructure());
        assertArmsClose(preset, compiled.getTowerStructure());
        assertEquals(preset.getBays().size(), compiled.getTowerStructure().getBays().size());
        assertEquals(baySignature(preset), baySignature(compiled.getTowerStructure()));
    }

    @Test
    void heightDependencyScalesVerticalGeometryOnly() {
        TowerParameterSet defaults = TowerParameterSet.smallLatticeDefaults();
        PoleDesign baseline = TowerParametricDesignFactory.compileSmallLattice(defaults);
        PoleDesign taller = TowerParametricDesignFactory.compileSmallLattice(
            new TowerParameterSet(30.0, defaults.baseWidth(), defaults.armSpan(), defaults.depthScale(), defaults.waistRatio(), defaults.armLevelScales(), defaults.density()));

        double ratio = 30.0 / 24.0;
        List<TowerStation> baseStations = baseline.getTowerStructure().sortedStations();
        List<TowerStation> tallStations = taller.getTowerStructure().sortedStations();
        for (int i = 0; i < baseStations.size(); i++) {
            assertClose(baseStations.get(i).getHeight() * ratio, tallStations.get(i).getHeight());
            assertClose(baseStations.get(i).getHalfWidth(), tallStations.get(i).getHalfWidth());
            assertClose(baseStations.get(i).getHalfDepth(), tallStations.get(i).getHalfDepth());
        }

        TowerArm baseArm = baseline.getTowerStructure().getArms().get(0);
        TowerArm tallArm = taller.getTowerStructure().getArms().get(0);
        assertClose(baseArm.getBaseHeight() * ratio, tallArm.getBaseHeight());
        assertClose(baseArm.getLateralReach(), tallArm.getLateralReach());
    }

    @Test
    void baseWidthDependencyScalesBodyOnly() {
        TowerParameterSet defaults = TowerParameterSet.smallLatticeDefaults();
        PoleDesign baseline = TowerParametricDesignFactory.compileSmallLattice(defaults);
        PoleDesign wider = TowerParametricDesignFactory.compileSmallLattice(
            new TowerParameterSet(defaults.height(), 11.0, defaults.armSpan(), defaults.depthScale(), defaults.waistRatio(), defaults.armLevelScales(), defaults.density()));

        double widthRatio = 11.0 / 9.0;
        List<TowerStation> baseStations = baseline.getTowerStructure().sortedStations();
        List<TowerStation> wideStations = wider.getTowerStructure().sortedStations();
        for (int i = 0; i < baseStations.size(); i++) {
            assertClose(baseStations.get(i).getHeight(), wideStations.get(i).getHeight());
            assertClose(baseStations.get(i).getHalfWidth() * widthRatio, wideStations.get(i).getHalfWidth());
            assertClose(baseStations.get(i).getHalfDepth() * widthRatio, wideStations.get(i).getHalfDepth());
        }
        assertClose(
            baseline.getTowerStructure().getArms().get(0).getLateralReach(),
            wider.getTowerStructure().getArms().get(0).getLateralReach());
    }

    @Test
    void armSpanDependencyScalesReachAndBoundAttachments() {
        TowerParameterSet defaults = TowerParameterSet.smallLatticeDefaults();
        PoleDesign baseline = TowerParametricDesignFactory.compileSmallLattice(defaults);
        PoleDesign widerSpan = TowerParametricDesignFactory.compileSmallLattice(
            new TowerParameterSet(defaults.height(), defaults.baseWidth(), 18.0, defaults.depthScale(), defaults.waistRatio(), defaults.armLevelScales(), defaults.density()));

        TowerArm baseArm = baseline.getTowerStructure().getArms().get(0);
        TowerArm wideArm = widerSpan.getTowerStructure().getArms().get(0);
        assertClose(8.0, baseArm.getLateralReach());
        assertClose(9.0, wideArm.getLateralReach());

        double baseSpread = maxPhaseLateral(baseline, "arm_main");
        double wideSpread = maxPhaseLateral(widerSpan, "arm_main");
        assertTrue(wideSpread > baseSpread);
        assertClose(baseArm.getLateralReach() * 0.85, baseSpread, 0.2);
    }

    @Test
    void densityPreservesSilhouetteButChangesBracing() {
        TowerParameterSet defaults = TowerParameterSet.smallLatticeDefaults();
        PoleDesign low = TowerParametricDesignFactory.compileSmallLattice(
            new TowerParameterSet(defaults.height(), defaults.baseWidth(), defaults.armSpan(), defaults.depthScale(), defaults.waistRatio(), defaults.armLevelScales(), StructureDensity.LOW));
        PoleDesign medium = TowerParametricDesignFactory.compileSmallLattice(defaults);
        PoleDesign high = TowerParametricDesignFactory.compileSmallLattice(
            new TowerParameterSet(defaults.height(), defaults.baseWidth(), defaults.armSpan(), defaults.depthScale(), defaults.waistRatio(), defaults.armLevelScales(), StructureDensity.HIGH));

        assertStationsClose(low.getTowerStructure(), medium.getTowerStructure());
        assertStationsClose(high.getTowerStructure(), medium.getTowerStructure());
        assertArmsClose(low.getTowerStructure(), medium.getTowerStructure());
        assertNotEquals(baySignature(low.getTowerStructure()), baySignature(medium.getTowerStructure()));
        assertNotEquals(baySignature(high.getTowerStructure()), baySignature(medium.getTowerStructure()));
    }

    @Test
    void profileClampRecordsAdjustments() {
        TowerParameterSet requested = new TowerParameterSet(80.0, 20.0, 40.0, 2.0, 2.0, null, StructureDensity.MEDIUM);
        TowerConstraintResult result = TowerParametricDesignFactory.resolveSmallLattice(requested);
        assertClose(36.0, result.resolved().height());
        assertClose(12.0, result.resolved().baseWidth());
        assertClose(20.0, result.resolved().armSpan());
        assertTrue(result.adjustments().stream().anyMatch(a -> a.kind() == ConstraintAdjustmentKind.HEIGHT_CLAMPED_TO_PROFILE));
    }

    @Test
    void editorEnableSmallLatticeProducesConfig() {
        PoleDesign design = new PoleDesign("small", "Small");
        TowerParametricEditor.enableParametricSmallLattice(design, TowerParameterSet.smallLatticeDefaults());
        assertTrue(design.isParametricMode());
        assertEquals(TowerParameterProfiles.SMALL_LATTICE_ID, design.getGeneratorConfig().profileId());
        assertEquals(4, design.getTowerStructure().sortedStations().size());
        assertEquals(1, design.getTowerStructure().getArms().size());
        assertFalse(design.getAttachments().isEmpty());
    }

    @Test
    void existingSmallLatticePresetStillWorks() {
        TowerStructureDesign preset = TowerStructurePresets.smallLatticeTower();
        assertEquals(4, preset.sortedStations().size());
        assertEquals(1, preset.getArms().size());
        assertEquals(TowerSilhouette.TAPERED_LATTICE, preset.getSilhouette());
    }

    private static void assertStationsClose(TowerStructureDesign expected, TowerStructureDesign actual) {
        List<TowerStation> expectedStations = expected.sortedStations();
        List<TowerStation> actualStations = actual.sortedStations();
        assertEquals(expectedStations.size(), actualStations.size());
        for (int i = 0; i < expectedStations.size(); i++) {
            assertClose(expectedStations.get(i).getHeight(), actualStations.get(i).getHeight());
            assertClose(expectedStations.get(i).getHalfWidth(), actualStations.get(i).getHalfWidth());
            assertClose(expectedStations.get(i).getHalfDepth(), actualStations.get(i).getHalfDepth());
        }
    }

    private static void assertArmsClose(TowerStructureDesign expected, TowerStructureDesign actual) {
        List<TowerArm> expectedArms = sortedArms(expected);
        List<TowerArm> actualArms = sortedArms(actual);
        assertEquals(expectedArms.size(), actualArms.size());
        for (int i = 0; i < expectedArms.size(); i++) {
            assertClose(expectedArms.get(i).getBaseHeight(), actualArms.get(i).getBaseHeight());
            assertClose(expectedArms.get(i).getLateralReach(), actualArms.get(i).getLateralReach());
            assertClose(expectedArms.get(i).getVerticalDrop(), actualArms.get(i).getVerticalDrop());
            assertClose(expectedArms.get(i).getLongitudinalHalfWidth(), actualArms.get(i).getLongitudinalHalfWidth());
            assertEquals(expectedArms.get(i).getShape(), actualArms.get(i).getShape());
        }
    }

    private static String baySignature(TowerStructureDesign structure) {
        return structure.getBays().stream()
            .sorted(Comparator.comparing(TowerBay::getLowerStationId).thenComparing(TowerBay::getUpperStationId))
            .map(bay -> bay.getFrontBackBracing()
                + ":" + bay.isHorizontalRing()
                + ":" + bay.isPlanDiagonalBracing())
            .reduce((a, b) -> a + "|" + b)
            .orElse("");
    }

    private static List<TowerArm> sortedArms(TowerStructureDesign structure) {
        return structure.getArms().stream()
            .sorted(Comparator.comparingDouble(TowerArm::getBaseHeight))
            .toList();
    }

    private static double maxPhaseLateral(PoleDesign design, String armId) {
        return design.getAttachments().stream()
            .filter(attachment -> armId.equals(attachment.getArmId()))
            .mapToDouble(attachment -> Math.abs(
                TowerArmAttachmentBinding.resolveLocalOffsets(attachment, design.getTowerStructure()).lateral()))
            .max()
            .orElse(0.0);
    }

    private static void assertClose(double expected, double actual) {
        assertClose(expected, actual, EPS);
    }

    private static void assertClose(double expected, double actual, double tolerance) {
        assertEquals(expected, actual, tolerance, "expected " + expected + " but was " + actual);
    }
}
