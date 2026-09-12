package com.plot.plugin.powerline.design.parametric;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.structure.TowerSilhouette;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorMode;
import com.plot.plugin.powerline.design.structure.TowerStructurePresets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerParametricEditorTest {

    @Test
    void enableParametricClassicCompilesStructureAndConfig() {
        PoleDesign design = new PoleDesign("test", "Test");
        TowerParametricEditor.enableParametricClassic(design, TowerParameterSet.classicDefaults());

        assertTrue(design.isParametricMode());
        assertNotNull(design.getTowerStructure());
        assertEquals(6, design.getTowerStructure().sortedStations().size());
        assertEquals(2, design.getTowerStructure().getArms().size());
        assertFalse(design.getAttachments().isEmpty());
    }

    @Test
    void recompileUpdatesGeometryWhenHeightChanges() {
        PoleDesign design = new PoleDesign("test", "Test");
        TowerParametricEditor.enableParametricClassic(design, TowerParameterSet.classicDefaults());
        double originalTop = design.getTowerStructure().maxHeight();

        design.setGeneratorConfig(design.getGeneratorConfig().withParameters(
            new TowerParameterSet(48.0, 13.0, 24.0, 1.0, 1.0, null, StructureDensity.MEDIUM)));
        TowerParametricEditor.recompile(design, null);

        assertTrue(design.getTowerStructure().maxHeight() > originalTop);
        assertEquals(48.0, design.getGeneratorConfig().parameters().height(), 0.01);
    }

    @Test
    void generatorConfigRoundTripsThroughPoleDesignJson() {
        PoleDesign design = new PoleDesign("roundtrip", "Roundtrip");
        design.setTowerStructure(TowerStructurePresets.classicDoubleArmTower());
        design.setGeneratorConfig(TowerGeneratorConfig.parametricClassic(
            new TowerParameterSet(40.0, 14.0, 26.0, 1.1, 1.0, null, StructureDensity.HIGH)));

        PoleDesign restored = PoleDesign.fromJson(design.toJson());
        assertNotNull(restored.getGeneratorConfig());
        assertEquals(TowerParameterProfiles.CLASSIC_DOUBLE_ARM_ID, restored.getGeneratorConfig().profileId());
        assertEquals(40.0, restored.getGeneratorConfig().parameters().height(), 0.01);
        assertEquals(StructureDensity.HIGH, restored.getGeneratorConfig().parameters().density());
    }

    @Test
    void convertToManualPreservesStructureAndMarksManualLegacy() {
        PoleDesign design = new PoleDesign("manual", "Manual");
        TowerParametricEditor.enableParametricClassic(design, TowerParameterSet.classicDefaults());
        TowerParametricEditor.convertToManual(design);
        assertFalse(design.isParametricMode());
        assertTrue(design.isManualLegacyMode());
        assertEquals(TowerGeneratorMode.MANUAL_LEGACY, design.getGeneratorConfig().mode());
        assertNotNull(design.getTowerStructure());
    }

    @Test
    void restoreParametricReEnablesGeneratorMode() {
        PoleDesign design = new PoleDesign("restore", "Restore");
        TowerParametricEditor.enableParametricClassic(design, TowerParameterSet.classicDefaults());
        TowerParametricEditor.convertToManual(design);
        assertTrue(TowerParametricEditor.restoreParametric(design, null));
        assertTrue(design.isParametricMode());
        assertFalse(design.isManualLegacyMode());
    }

    @Test
    void enableParametricSmallLatticeCompilesStructure() {
        PoleDesign design = new PoleDesign("small", "Small");
        TowerParametricEditor.enableParametricSmallLattice(design, TowerParameterSet.smallLatticeDefaults());

        assertTrue(design.isParametricMode());
        assertEquals(TowerParameterProfiles.SMALL_LATTICE_ID, design.getGeneratorConfig().profileId());
        assertEquals(TowerSilhouette.TAPERED_LATTICE, design.getTowerStructure().getSilhouette());
    }

    @Test
    void heightLimitsRespectWorldEnvelope() {
        TowerBuildEnvelope envelope = new TowerBuildEnvelope(-64, 320, 280.0, 4);
        TowerParametricHeightLimits.EffectiveHeightRange range = TowerParametricHeightLimits.heightRange(
            TowerParameterProfiles.classicDoubleArm(),
            TowerParameterSet.classicDefaults(),
            envelope);
        assertTrue(range.worldLimitedMax() != null);
        assertTrue(range.max() < TowerParameterProfiles.classicDoubleArm().heightRange().max());
    }
}
