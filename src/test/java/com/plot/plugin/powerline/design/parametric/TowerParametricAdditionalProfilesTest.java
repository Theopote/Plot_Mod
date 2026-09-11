package com.plot.plugin.powerline.design.parametric;

import com.plot.plugin.powerline.design.PoleDesign;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerParametricAdditionalProfilesTest {
    private static final double EPS = 0.05;

    @Test
    void defaultTripleArmParametersCompileCloseToCurrentPreset() {
        PoleDesign compiled = TowerParametricDesignFactory.compileTripleArm(TowerParameterSet.tripleArmDefaults());
        TowerStructureDesign preset = TowerStructurePresets.tripleArmTower();

        assertEquals(TowerSilhouette.TRIPLE_ARM, compiled.getTowerStructure().getSilhouette());
        assertStationsClose(preset, compiled.getTowerStructure());
        assertArmsClose(preset, compiled.getTowerStructure());
        assertEquals(preset.getBays().size(), compiled.getTowerStructure().getBays().size());
        assertEquals(baySignature(preset), baySignature(compiled.getTowerStructure()));
        assertEquals(3, compiled.getTowerStructure().getArms().size());
    }

    @Test
    void defaultCupParametersCompileCloseToCurrentPreset() {
        PoleDesign compiled = TowerParametricDesignFactory.compileCup(TowerParameterSet.cupDefaults());
        TowerStructureDesign preset = TowerStructurePresets.cupTower();

        assertEquals(TowerSilhouette.CUP, compiled.getTowerStructure().getSilhouette());
        assertStationsClose(preset, compiled.getTowerStructure());
        assertArmsClose(preset, compiled.getTowerStructure());
        assertEquals(preset.getBays().size(), compiled.getTowerStructure().getBays().size());
        assertEquals(baySignature(preset), baySignature(compiled.getTowerStructure()));
        assertEquals(1, compiled.getTowerStructure().getArms().size());
    }

    @Test
    void defaultHeavyParametersCompileCloseToCurrentPreset() {
        PoleDesign compiled = TowerParametricDesignFactory.compileHeavy(TowerParameterSet.heavyDefaults());
        TowerStructureDesign preset = TowerStructurePresets.heavyTransmissionTower();

        assertEquals(TowerSilhouette.DOUBLE_ARM, compiled.getTowerStructure().getSilhouette());
        assertStationsClose(preset, compiled.getTowerStructure());
        assertArmsClose(preset, compiled.getTowerStructure());
        assertEquals(preset.getBays().size(), compiled.getTowerStructure().getBays().size());
        assertEquals(baySignature(preset), baySignature(compiled.getTowerStructure()));
        assertEquals(2, compiled.getTowerStructure().getArms().size());
    }

    @Test
    void defaultMegaParametersCompileCloseToCurrentPreset() {
        PoleDesign compiled = TowerParametricDesignFactory.compileMega(TowerParameterSet.megaDefaults());
        TowerStructureDesign preset = TowerStructurePresets.megaLatticeTower();

        assertEquals(TowerSilhouette.GIANT, compiled.getTowerStructure().getSilhouette());
        assertStationsClose(preset, compiled.getTowerStructure());
        assertArmsClose(preset, compiled.getTowerStructure());
        assertEquals(preset.getBays().size(), compiled.getTowerStructure().getBays().size());
        assertEquals(baySignature(preset), baySignature(compiled.getTowerStructure()));
        assertEquals(3, compiled.getTowerStructure().getArms().size());
    }

    @Test
    void defaultPortalParametersCompileCloseToCurrentPreset() {
        PoleDesign compiled = TowerParametricDesignFactory.compilePortal(TowerParameterSet.portalDefaults());
        TowerStructureDesign preset = TowerStructurePresets.portalTower();

        assertEquals(TowerSilhouette.PORTAL, compiled.getTowerStructure().getSilhouette());
        assertStationsClose(preset, compiled.getTowerStructure());
        assertArmsClose(preset, compiled.getTowerStructure());
        assertEquals(preset.getBays().size(), compiled.getTowerStructure().getBays().size());
        assertEquals(baySignature(preset), baySignature(compiled.getTowerStructure()));
        assertEquals(3, compiled.getTowerStructure().getArms().size());
    }

    @Test
    void defaultDrumParametersCompileCloseToCurrentPreset() {
        PoleDesign compiled = TowerParametricDesignFactory.compileDrum(TowerParameterSet.drumDefaults());
        TowerStructureDesign preset = TowerStructurePresets.doubleCircuitDrumTower();

        assertEquals(TowerSilhouette.TRIPLE_ARM, compiled.getTowerStructure().getSilhouette());
        assertStationsClose(preset, compiled.getTowerStructure());
        assertArmsClose(preset, compiled.getTowerStructure());
        assertEquals(preset.getBays().size(), compiled.getTowerStructure().getBays().size());
        assertEquals(baySignature(preset), baySignature(compiled.getTowerStructure()));
        assertEquals(3, compiled.getTowerStructure().getArms().size());
    }

    @Test
    void defaultUhvParametersCompileCloseToCurrentPreset() {
        PoleDesign compiled = TowerParametricDesignFactory.compileUhv(TowerParameterSet.uhvDefaults());
        TowerStructureDesign preset = TowerStructurePresets.uhvGiantTower();

        assertEquals(TowerSilhouette.GIANT, compiled.getTowerStructure().getSilhouette());
        assertStationsClose(preset, compiled.getTowerStructure());
        assertArmsClose(preset, compiled.getTowerStructure());
        assertEquals(preset.getBays().size(), compiled.getTowerStructure().getBays().size());
        assertEquals(baySignature(preset), baySignature(compiled.getTowerStructure()));
        assertEquals(3, compiled.getTowerStructure().getArms().size());
    }

    @Test
    void profileFindReturnsAllMigratedProfiles() {
        assertTrue(TowerParameterProfiles.find(TowerParameterProfiles.TRIPLE_ARM_ID).isPresent());
        assertTrue(TowerParameterProfiles.find(TowerParameterProfiles.CUP_ID).isPresent());
        assertTrue(TowerParameterProfiles.find(TowerParameterProfiles.HEAVY_ID).isPresent());
        assertTrue(TowerParameterProfiles.find(TowerParameterProfiles.MEGA_ID).isPresent());
        assertTrue(TowerParameterProfiles.find(TowerParameterProfiles.PORTAL_ID).isPresent());
        assertTrue(TowerParameterProfiles.find(TowerParameterProfiles.DRUM_ID).isPresent());
        assertTrue(TowerParameterProfiles.find(TowerParameterProfiles.UHV_ID).isPresent());
        assertTrue(TowerParametricEditor.supportsProfile(TowerParameterProfiles.UHV_ID));
    }

    @Test
    void editorEnableTripleArmProducesConfig() {
        PoleDesign design = new PoleDesign("triple", "Triple");
        TowerParametricEditor.enableParametricTripleArm(design, TowerParameterSet.tripleArmDefaults());
        assertTrue(design.isParametricMode());
        assertEquals(TowerParameterProfiles.TRIPLE_ARM_ID, design.getGeneratorConfig().profileId());
        assertEquals(6, design.getTowerStructure().sortedStations().size());
        assertEquals(3, design.getTowerStructure().getArms().size());
        assertFalse(design.getAttachments().isEmpty());
    }

    @Test
    void tripleArmProfileClampRecordsAdjustments() {
        TowerParameterSet requested = new TowerParameterSet(100.0, 30.0, 60.0, 2.0, StructureDensity.MEDIUM);
        TowerConstraintResult result = TowerParametricDesignFactory.resolveTripleArm(requested);
        assertClose(70.0, result.resolved().height());
        assertClose(20.0, result.resolved().baseWidth());
        assertClose(38.0, result.resolved().armSpan());
        assertTrue(result.adjustments().stream().anyMatch(a -> a.kind() == ConstraintAdjustmentKind.HEIGHT_CLAMPED_TO_PROFILE));
    }

    @Test
    void portalMiddleArmIsDominantReach() {
        PoleDesign compiled = TowerParametricDesignFactory.compilePortal(TowerParameterSet.portalDefaults());
        List<TowerArm> arms = sortedArms(compiled.getTowerStructure());
        assertClose(13.0, arms.get(0).getLateralReach());
        assertClose(16.0, arms.get(1).getLateralReach());
        assertClose(13.0, arms.get(2).getLateralReach());
    }

    @Test
    void uhvProfileClampRecordsAdjustments() {
        TowerParameterSet requested = new TowerParameterSet(120.0, 40.0, 80.0, 2.0, StructureDensity.MEDIUM);
        TowerConstraintResult result = TowerParametricDesignFactory.resolveUhv(requested);
        assertClose(100.0, result.resolved().height());
        assertClose(34.0, result.resolved().baseWidth());
        assertClose(64.0, result.resolved().armSpan());
        assertTrue(result.adjustments().stream().anyMatch(a -> a.kind() == ConstraintAdjustmentKind.HEIGHT_CLAMPED_TO_PROFILE));
    }

    @Test
    void cupTowerPreservesWideHeadStation() {
        PoleDesign compiled = TowerParametricDesignFactory.compileCup(TowerParameterSet.cupDefaults());
        List<TowerStation> stations = compiled.getTowerStructure().sortedStations();
        TowerStation waist = stations.get(3);
        TowerStation head = stations.get(4);
        assertTrue(head.getHalfWidth() > waist.getHalfWidth());
        assertClose(16.0, compiled.getTowerStructure().getArms().getFirst().getLateralReach());
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

    private static void assertClose(double expected, double actual) {
        assertEquals(expected, actual, EPS, "expected " + expected + " but was " + actual);
    }
}
