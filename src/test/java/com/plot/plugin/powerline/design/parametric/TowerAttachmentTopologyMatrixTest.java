package com.plot.plugin.powerline.design.parametric;

import com.plot.plugin.powerline.design.AttachmentRole;
import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.design.ConductorArrangement;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import com.plot.plugin.powerline.equipment.InsulatorAssemblyCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Stabilization v1.1：全 Profile 导线拓扑 + 绝缘子策略。 */
class TowerAttachmentTopologyMatrixTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("topologyCases")
    void profileAttachmentTopologyMatchesLegacy(
            String label,
            PoleDesign compiled,
            int expectedPhaseCount,
            int expectedTotalAttachments) {
        assertEquals(expectedPhaseCount, phaseCount(compiled), label + " phase count");
        assertEquals(expectedTotalAttachments, compiled.getAttachments().size(), label + " total attachments");
    }

    @Test
    void classicUsesBundledThreePhaseAndTwinTopWires() {
        PoleDesign compiled = TowerParametricDesignFactory.compileClassicDoubleArm(TowerParameterSet.classicDefaults());
        assertEquals(3, phaseCount(compiled));
        assertEquals(2, topWireCount(compiled));
        assertEquals(5, compiled.getAttachments().size());
        assertTrue(allPhaseInsulatorLength(compiled, 2));
    }

    @Test
    void cupUsesHeavyLatticeLengthWithoutMegaPhaseAssemblies() {
        PoleDesign compiled = TowerParametricDesignFactory.compileCup(TowerParameterSet.cupDefaults());
        assertTrue(allPhaseInsulatorLength(compiled, 3));
        assertFalse(anyPhaseHasAssembly(compiled, InsulatorAssemblyCatalog.TWIN_STRING_ID));
    }

    @Test
    void heavyUsesMegaInsulatorAssembliesOnPhaseConductors() {
        PoleDesign compiled = TowerParametricDesignFactory.compileHeavy(TowerParameterSet.heavyDefaults());
        PoleDesign reference = TowerFamilyDesignPresets.hvTransmissionSuspension();
        assertTrue(anyPhaseHasAssembly(compiled, InsulatorAssemblyCatalog.TWIN_STRING_ID));
        assertEquals(
            reference.getAttachments().stream().filter(TowerAttachmentTopologyMatrixTest::isPhase)
                .mapToInt(ConductorAttachment::getInsulatorLength).max().orElse(0),
            compiled.getAttachments().stream().filter(TowerAttachmentTopologyMatrixTest::isPhase)
                .mapToInt(ConductorAttachment::getInsulatorLength).max().orElse(0));
    }

    @Test
    void megaDeckUsesMegaInsulatorAssemblies() {
        PoleDesign compiled = TowerParametricDesignFactory.compileMega(TowerParameterSet.megaDefaults());
        assertEquals(6, phaseCount(compiled));
        assertTrue(anyPhaseHasAssembly(compiled, InsulatorAssemblyCatalog.TWIN_STRING_ID));
    }

    @Test
    void uhvDeckHasTwelvePhaseConductors() {
        PoleDesign compiled = TowerParametricDesignFactory.compileUhv(TowerParameterSet.uhvDefaults());
        assertEquals(12, phaseCount(compiled));
        assertEquals(2, topWireCount(compiled));
        assertTrue(anyPhaseHasAssembly(compiled, InsulatorAssemblyCatalog.TWIN_STRING_ID));
    }

    @Test
    void portalUsesSixPhaseDoubleCircuit() {
        PoleDesign compiled = TowerParametricDesignFactory.compilePortal(TowerParameterSet.portalDefaults());
        assertEquals(
            ConductorArrangement.heavyDoubleCircuit().phaseConductorCount(),
            phaseCount(compiled));
    }

    @Test
    void modernHvGlassUsesThreeHorizontalSinglePhaseDeck() {
        PoleDesign compiled = TowerParametricDesignFactory.compileModernHvGlass(
            TowerParameterSet.modernHvGlassDefaults());
        assertEquals(3, phaseCount(compiled));
        assertEquals(3, compiled.getAttachments().size());
    }

    @Test
    void compilerAppliesInsulatorDefaultsToSameAttachmentList() {
        PoleDesign compiled = TowerParametricDesignFactory.compileMega(TowerParameterSet.megaDefaults());
        ConductorAttachment phase = compiled.getAttachments().stream()
            .filter(a -> a.getRole() == AttachmentRole.PHASE_A)
            .findFirst()
            .orElseThrow();
        assertNotNull(phase.getInsulatorAssemblyId());
        assertFalse(phase.getInsulatorAssemblyId().isBlank());
    }

    static Stream<Arguments> topologyCases() {
        return Stream.of(
            Arguments.of(
                "Small",
                TowerParametricDesignFactory.compileSmallLattice(TowerParameterSet.smallLatticeDefaults()),
                3,
                TowerFamilyDesignPresets.latticeSuspensionSmall().getAttachments().size()),
            Arguments.of(
                "Classic",
                TowerParametricDesignFactory.compileClassicDoubleArm(TowerParameterSet.classicDefaults()),
                3,
                TowerFamilyDesignPresets.latticeSuspension().getAttachments().size()),
            Arguments.of(
                "Triple",
                TowerParametricDesignFactory.compileTripleArm(TowerParameterSet.tripleArmDefaults()),
                ConductorArrangement.doubleCircuitThreeDeck().phaseConductorCount(),
                8),
            Arguments.of(
                "Cup",
                TowerParametricDesignFactory.compileCup(TowerParameterSet.cupDefaults()),
                3,
                TowerFamilyDesignPresets.cupTowerSuspension().getAttachments().size()),
            Arguments.of(
                "Heavy",
                TowerParametricDesignFactory.compileHeavy(TowerParameterSet.heavyDefaults()),
                3,
                TowerFamilyDesignPresets.hvTransmissionSuspension().getAttachments().size()),
            Arguments.of(
                "Mega",
                TowerParametricDesignFactory.compileMega(TowerParameterSet.megaDefaults()),
                ConductorArrangement.megaThreeDeck().phaseConductorCount(),
                8),
            Arguments.of(
                "Portal",
                TowerParametricDesignFactory.compilePortal(TowerParameterSet.portalDefaults()),
                ConductorArrangement.heavyDoubleCircuit().phaseConductorCount(),
                TowerFamilyDesignPresets.industrialPortalSuspension().getAttachments().size()),
            Arguments.of(
                "Drum",
                TowerParametricDesignFactory.compileDrum(TowerParameterSet.drumDefaults()),
                ConductorArrangement.doubleCircuitDrum().phaseConductorCount(),
                8),
            Arguments.of(
                "UHV",
                TowerParametricDesignFactory.compileUhv(TowerParameterSet.uhvDefaults()),
                ConductorArrangement.uhvThreeDeck().phaseConductorCount(),
                14),
            Arguments.of(
                "Steampunk",
                TowerParametricDesignFactory.compileSteampunk(TowerParameterSet.steampunkDefaults()),
                3,
                5),
            Arguments.of(
                "Modern HV Glass",
                TowerParametricDesignFactory.compileModernHvGlass(TowerParameterSet.modernHvGlassDefaults()),
                3,
                3));
    }

    private static long phaseCount(PoleDesign design) {
        return design.getAttachments().stream().filter(TowerAttachmentTopologyMatrixTest::isPhase).count();
    }

    private static long topWireCount(PoleDesign design) {
        return design.getAttachments().stream()
            .filter(a -> a.getRole() == AttachmentRole.TOP_WIRE)
            .count();
    }

    private static boolean isPhase(ConductorAttachment attachment) {
        AttachmentRole role = attachment.getRole();
        return role == AttachmentRole.PHASE_A
            || role == AttachmentRole.PHASE_B
            || role == AttachmentRole.PHASE_C;
    }

    private static boolean allPhaseInsulatorLength(PoleDesign design, int length) {
        return design.getAttachments().stream()
            .filter(TowerAttachmentTopologyMatrixTest::isPhase)
            .allMatch(a -> a.getInsulatorLength() == length);
    }

    private static boolean anyPhaseHasAssembly(PoleDesign design, String assemblyId) {
        return design.getAttachments().stream()
            .filter(TowerAttachmentTopologyMatrixTest::isPhase)
            .anyMatch(a -> assemblyId.equals(a.getInsulatorAssemblyId()));
    }
}
