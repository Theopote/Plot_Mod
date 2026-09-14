package com.plot.plugin.powerline.style;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.family.PoleDesignAssignmentResolver;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import com.plot.plugin.powerline.design.family.TowerFamilyResolver;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.equipment.InsulatorType;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.model.TowerRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PL-PRESET-S6 P1-3：Family 角色解析 + Style 参数化 applicator 端到端一致性。 */
class PowerLineFamilyParametricIntegrationTest {
    private final PoleDesignResolver designResolver = new PoleDesignResolver(new PowerLineDesignProject());
    private final PoleDesignAssignmentResolver assignmentResolver = new PoleDesignAssignmentResolver(
        designResolver,
        new TowerFamilyResolver());

    static Stream<PowerLineStylePreset> towerFamilyPresets() {
        return PowerLineStylePresetCatalog.defaultPresets().stream()
            .filter(preset -> preset.getTowerFamilyId() != null && !preset.getTowerFamilyId().isBlank());
    }

    @ParameterizedTest
    @MethodSource("towerFamilyPresets")
    void suspensionAndAngleRolesStayDistinctWithoutTuning(PowerLineStylePreset preset) {
        PowerLineFootprint line = familyLine(preset.getTowerFamilyId());
        preset.apply(line);
        TowerGeneratorConfig styleConfig = line.getParametricTowerConfig();

        PoleDesign suspension = resolveRole(line, TowerRole.SUSPENSION, null);
        PoleDesign angle = resolveRole(line, TowerRole.ANGLE, null);

        PoleDesign appliedSuspension = ParametricStyleTowerApplicator.apply(suspension, styleConfig, null, line);
        PoleDesign appliedAngle = ParametricStyleTowerApplicator.apply(angle, styleConfig, null, line);

        assertNotEquals(
            appliedSuspension.getId(),
            appliedAngle.getId(),
            preset.getId() + " should keep distinct role designs");
        assertTrue(
            appliedSuspension.getAttachments().stream().anyMatch(a -> a.getInsulatorType() != appliedAngle.getAttachments().getFirst().getInsulatorType())
                || appliedSuspension.getTowerStructure().maxHeight() != appliedAngle.getTowerStructure().maxHeight(),
            preset.getId() + " should preserve role-specific geometry or attachment identity");
    }

    @Test
    void smartTowersPreserveGradedRoleHeightsWithoutTuning() {
        PowerLineFootprint line = familyLine(TowerFamily.GRADED_LATTICE_3_PHASE_ID);
        PowerLineStylePresetCatalog.smartTowers().apply(line);
        TowerGeneratorConfig styleConfig = line.getParametricTowerConfig();

        PoleDesign small = resolveRole(
            line,
            TowerRole.SUSPENSION,
            TowerFamilyDesignPresets.LATTICE_SUSPENSION_SMALL_ID);
        PoleDesign medium = resolveRole(
            line,
            TowerRole.SPECIAL,
            TowerFamilyDesignPresets.LATTICE_SUSPENSION_MEDIUM_ID);
        PoleDesign tall = resolveRole(
            line,
            TowerRole.DEAD_END,
            TowerFamilyDesignPresets.LATTICE_SUSPENSION_TALL_ID);

        double smallHeight = small.getTowerStructure().maxHeight();
        double mediumHeight = medium.getTowerStructure().maxHeight();
        double tallHeight = tall.getTowerStructure().maxHeight();
        assertTrue(smallHeight < mediumHeight, "graded small should be shorter than medium");
        assertTrue(mediumHeight < tallHeight, "graded medium should be shorter than tall");

        PoleDesign appliedSmall = ParametricStyleTowerApplicator.apply(small, styleConfig, null, line);
        PoleDesign appliedMedium = ParametricStyleTowerApplicator.apply(medium, styleConfig, null, line);
        PoleDesign appliedTall = ParametricStyleTowerApplicator.apply(tall, styleConfig, null, line);

        assertEquals(smallHeight, appliedSmall.getTowerStructure().maxHeight(), 0.01);
        assertEquals(mediumHeight, appliedMedium.getTowerStructure().maxHeight(), 0.01);
        assertEquals(tallHeight, appliedTall.getTowerStructure().maxHeight(), 0.01);
        assertNotEquals(
            appliedSmall.getTowerStructure().maxHeight(),
            appliedTall.getTowerStructure().maxHeight(),
            0.5);
    }

    @Test
    void classicLatticePreservesRoleInsulatorsWithoutTuning() {
        PowerLineFootprint line = familyLine(TowerFamily.STANDARD_LATTICE_3_PHASE_ID);
        PowerLineStylePresetCatalog.classicLattice().apply(line);
        TowerGeneratorConfig styleConfig = line.getParametricTowerConfig();

        PoleDesign suspension = resolveRole(
            line,
            TowerRole.SUSPENSION,
            TowerFamilyDesignPresets.LATTICE_SUSPENSION_ID);
        PoleDesign angle = resolveRole(
            line,
            TowerRole.ANGLE,
            TowerFamilyDesignPresets.LATTICE_ANGLE_ID);

        PoleDesign appliedSuspension = ParametricStyleTowerApplicator.apply(suspension, styleConfig, null, line);
        PoleDesign appliedAngle = ParametricStyleTowerApplicator.apply(angle, styleConfig, null, line);

        assertEquals(InsulatorType.SUSPENSION, primaryInsulator(appliedSuspension));
        assertEquals(InsulatorType.STRAIN, primaryInsulator(appliedAngle));
        assertNotEquals(
            appliedSuspension.getTowerStructure().maxHeight(),
            0.0,
            0.01);
    }

    @Test
    void smartTowersApplyHeightTuneRelativeToEachRoleBaseline() {
        PowerLineFootprint line = familyLine(TowerFamily.GRADED_LATTICE_3_PHASE_ID);
        PowerLineStylePresetCatalog.smartTowers().apply(line);

        PowerLineQuickTunePolicy.applyParametricPoleHeightBand(
            line,
            PowerLineStylePresetCatalog.smartTowers(),
            PowerLineQuickTunePolicy.PoleHeightBand.TALL);

        TowerGeneratorConfig tuned = line.getParametricTowerConfig();
        PoleDesign small = resolveRole(
            line,
            TowerRole.SUSPENSION,
            TowerFamilyDesignPresets.LATTICE_SUSPENSION_SMALL_ID);
        PoleDesign tall = resolveRole(
            line,
            TowerRole.DEAD_END,
            TowerFamilyDesignPresets.LATTICE_SUSPENSION_TALL_ID);

        double smallBaseline = small.getTowerStructure().maxHeight();
        double tallBaseline = tall.getTowerStructure().maxHeight();

        PoleDesign appliedSmall = ParametricStyleTowerApplicator.apply(small, tuned, null, line);
        PoleDesign appliedTall = ParametricStyleTowerApplicator.apply(tall, tuned, null, line);

        assertTrue(appliedSmall.getTowerStructure().maxHeight() > smallBaseline);
        assertTrue(appliedTall.getTowerStructure().maxHeight() > tallBaseline);
        assertTrue(
            appliedSmall.getTowerStructure().maxHeight() < appliedTall.getTowerStructure().maxHeight(),
            "tuned graded roles should stay ordered by height");
    }

    private PoleDesign resolveRole(PowerLineFootprint line, TowerRole role, String expectedDesignId) {
        PowerPoleSite site = new PowerPoleSite(new Vec2d(10, 0));
        site.setRole(role);
        site.setRoleAutoAssigned(false);
        PoleDesignAssignmentResolver.AssignmentResult result = assignmentResolver.resolve(site, line);
        assertNotNull(result.design(), "missing design for role " + role);
        if (expectedDesignId != null) {
            assertEquals(expectedDesignId, result.resolvedDesignId());
        }
        return result.design();
    }

    private static PowerLineFootprint familyLine(String familyId) {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(80, 0)));
        line.setTowerFamilyId(familyId);
        line.setSagRatio(0.0);
        line.setMaxPoleSpacing(100.0);
        return line;
    }

    private static InsulatorType primaryInsulator(PoleDesign design) {
        return design.getAttachments().stream()
            .filter(attachment -> attachment.isEnabled())
            .map(attachment -> attachment.getInsulatorType())
            .findFirst()
            .orElseThrow();
    }
}
