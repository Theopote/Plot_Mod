package com.plot.plugin.powerline.design.parametric;

import com.plot.plugin.powerline.design.AttachmentRole;
import com.plot.plugin.powerline.design.ConductorArrangement;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Stabilization v1.0：attachment topology、ERROR contract、profile migration、equivalence matrix。 */
class TowerParametricStabilizationTest {

    @Test
    void classicParametricUsesSingleLatticeDeckNotPerArmAbc() {
        PoleDesign compiled = TowerParametricDesignFactory.compileClassicDoubleArm(TowerParameterSet.classicDefaults());
        PoleDesign reference = TowerFamilyDesignPresets.latticeSuspension();

        assertEquals(phaseAttachmentCount(reference), phaseAttachmentCount(compiled));
        assertEquals(reference.getAttachments().size(), compiled.getAttachments().size());
        assertFalse(compiled.getAttachments().stream().anyMatch(a -> a.getArmId() != null && !a.getArmId().isBlank()));
    }

    @Test
    void tripleParametricMatchesDoubleCircuitThreeDeckTopology() {
        PoleDesign compiled = TowerParametricDesignFactory.compileTripleArm(TowerParameterSet.tripleArmDefaults());
        assertEquals(
            ConductorArrangement.doubleCircuitThreeDeck().phaseConductorCount(),
            phaseAttachmentCount(compiled));
        assertEquals(8, compiled.getAttachments().size());
    }

    @Test
    void drumParametricMatchesSixPhaseDrumTopology() {
        PoleDesign compiled = TowerParametricDesignFactory.compileDrum(TowerParameterSet.drumDefaults());
        assertEquals(
            ConductorArrangement.doubleCircuitDrum().phaseConductorCount(),
            phaseAttachmentCount(compiled));
        assertEquals(8, compiled.getAttachments().size());
    }

    @Test
    void uhvParametricMatchesUhvThreeDeckTopology() {
        PoleDesign compiled = TowerParametricDesignFactory.compileUhv(TowerParameterSet.uhvDefaults());
        assertEquals(
            ConductorArrangement.uhvThreeDeck().phaseConductorCount(),
            phaseAttachmentCount(compiled));
        assertEquals(14, compiled.getAttachments().size());
    }

    @Test
    void recompileRefusesStructureOnConstraintError() {
        PoleDesign design = new PoleDesign("blocked", "Blocked");
        TowerParametricEditor.enableParametricClassic(design, TowerParameterSet.classicDefaults());
        double originalTop = design.getTowerStructure().maxHeight();
        int originalAttachmentCount = design.getAttachments().size();

        TowerBuildEnvelope envelope = new TowerBuildEnvelope(-64, 320, 280.0, 4);
        design.setGeneratorConfig(design.getGeneratorConfig().withParameters(
            new TowerParameterSet(52.0, 13.0, 24.0, 1.0, 1.0, null, StructureDensity.MEDIUM)));
        TowerConstraintResult result = TowerParametricEditor.recompile(design, envelope);

        assertTrue(result.hasErrors());
        assertEquals(originalTop, design.getTowerStructure().maxHeight(), 0.01);
        assertEquals(originalAttachmentCount, design.getAttachments().size());
    }

    @Test
    void profileMigrationRegeneratesAttachmentTopology() {
        PoleDesign design = new PoleDesign("migrate", "Migrate");
        TowerParametricEditor.enableParametricClassic(design, TowerParameterSet.classicDefaults());
        int classicCount = design.getAttachments().size();

        TowerParametricEditor.enableParametricTripleArm(design, TowerParameterSet.tripleArmDefaults());

        assertEquals(TowerParameterProfiles.TRIPLE_ARM_ID, design.getGeneratorConfig().profileId());
        assertNotEquals(classicCount, design.getAttachments().size());
        assertEquals(8, design.getAttachments().size());
    }

    @Test
    void sameProfileRecompilePreservesExistingAttachments() {
        PoleDesign design = new PoleDesign("preserve", "Preserve");
        TowerParametricEditor.enableParametricClassic(design, TowerParameterSet.classicDefaults());
        design.getAttachments().get(0).setName("USER_MARK");

        design.setGeneratorConfig(design.getGeneratorConfig().withParameters(
            new TowerParameterSet(40.0, 13.0, 24.0, 1.0, 1.0, null, StructureDensity.MEDIUM)));
        TowerParametricEditor.recompile(
            design,
            null,
            TowerParameterProfiles.CLASSIC_DOUBLE_ARM_ID);

        assertEquals("USER_MARK", design.getAttachments().get(0).getName());
        assertEquals(40.0, design.getGeneratorConfig().parameters().height(), 0.01);
    }

    @Test
    void smartTowersPresetDoesNotAutoEnableExperimentalPerSiteHeight() {
        PowerLineFootprint line = new PowerLineFootprint(
            java.util.List.of(new com.plot.api.geometry.Vec2d(0, 0), new com.plot.api.geometry.Vec2d(80, 0)));
        PowerLineStylePresetCatalog.smartTowers().apply(line);

        assertTrue(line.hasParametricTowerConfig());
        assertFalse(line.isPerSiteParametricHeightEnabled());
    }

    @Test
    void defaultEquivalenceMatrixCoversMajorProfiles() {
        assertAttachmentTopology(
            TowerParametricDesignFactory.compileSmallLattice(TowerParameterSet.smallLatticeDefaults()),
            TowerFamilyDesignPresets.latticeSuspensionSmall());
        assertAttachmentTopology(
            TowerParametricDesignFactory.compileHeavy(TowerParameterSet.heavyDefaults()),
            TowerFamilyDesignPresets.hvTransmissionSuspension());
        assertAttachmentTopology(
            TowerParametricDesignFactory.compileMega(TowerParameterSet.megaDefaults()),
            TowerFamilyDesignPresets.megaLatticeSuspension());
        assertAttachmentTopology(
            TowerParametricDesignFactory.compilePortal(TowerParameterSet.portalDefaults()),
            TowerFamilyDesignPresets.industrialPortalSuspension());
        assertAttachmentTopology(
            TowerParametricDesignFactory.compileDrum(TowerParameterSet.drumDefaults()),
            TowerFamilyDesignPresets.heavyDoubleCircuitSuspension());
    }

    private static void assertAttachmentTopology(PoleDesign parametric, PoleDesign reference) {
        assertEquals(phaseAttachmentCount(reference), phaseAttachmentCount(parametric));
        assertEquals(reference.getAttachments().size(), parametric.getAttachments().size());
    }

    private static long phaseAttachmentCount(PoleDesign design) {
        return design.getAttachments().stream()
            .filter(TowerParametricStabilizationTest::isPhaseAttachment)
            .count();
    }

    private static boolean isPhaseAttachment(com.plot.plugin.powerline.design.ConductorAttachment attachment) {
        AttachmentRole role = attachment.getRole();
        return role == AttachmentRole.PHASE_A
            || role == AttachmentRole.PHASE_B
            || role == AttachmentRole.PHASE_C;
    }
}
