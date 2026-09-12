package com.plot.plugin.powerline.style;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.design.parametric.TowerParameterProfiles;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerLineProject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineStyleParametricIntegrationTest {

    @Test
    void transmissionPresetAppliesParametricConfigToFootprint() {
        PowerLineFootprint line = line();
        PowerLineStylePresetCatalog.classicLattice().apply(line);

        assertTrue(line.hasParametricTowerConfig());
        assertEquals(
            TowerParameterProfiles.CLASSIC_DOUBLE_ARM_ID,
            line.getParametricTowerConfig().profileId());
        assertEquals(36.0, line.getParametricTowerConfig().parameters().height(), 0.01);
    }

    @Test
    void woodPresetDoesNotApplyParametricConfig() {
        PowerLineFootprint line = line();
        PowerLineStylePresetCatalog.classicWood().apply(line);

        assertFalse(line.hasParametricTowerConfig());
    }

    @Test
    void compactLatticeUsesSmallLatticeProfile() {
        PowerLineStylePreset preset = PowerLineStylePresetCatalog.compactLattice();
        assertNotNull(preset.getDefinition().getParametricConfig());
        assertEquals(
            TowerParameterProfiles.SMALL_LATTICE_ID,
            preset.getDefinition().getParametricConfig().profileId());

        PowerLineFootprint line = line();
        preset.apply(line);
        assertEquals(
            TowerParameterProfiles.SMALL_LATTICE_ID,
            line.getParametricTowerConfig().profileId());
    }

    @Test
    void styleApplicatorReplacesLegacyDesignWithParametricCompile() {
        PoleDesign legacy = PowerLineStylePreset.resolveRepresentativeDesign(woodLine());
        assertNotNull(legacy);
        assertFalse(legacy.isParametricMode());

        TowerGeneratorConfig styleConfig = PowerLineStylePresetCatalog.classicLattice()
            .getDefinition()
            .getParametricConfig();
        PoleDesign applied = ParametricStyleTowerApplicator.apply(legacy, styleConfig);

        assertTrue(applied.isParametricMode());
        assertEquals(TowerParameterProfiles.CLASSIC_DOUBLE_ARM_ID, applied.getGeneratorConfig().profileId());
        assertTrue(applied.hasTowerStructure());
    }

    @Test
    void parametricConfigRoundTripsThroughProjectJson() {
        PowerLineProject project = new PowerLineProject();
        PowerLineFootprint line = line();
        PowerLineStylePresetCatalog.tripleArmTower().apply(line);
        line.setParametricTowerConfig(line.getParametricTowerConfig().withParameters(
            new TowerParameterSet(
                52.0,
                16.0,
                29.0,
                1.1,
                0.95,
                List.of(1.05, 1.0),
                TowerParameterSet.tripleArmDefaults().density())));
        project.addLine(line);

        PowerLineProject restored = PowerLineProject.fromJson(project.toJson());
        PowerLineFootprint restoredLine = restored.getLine(line.getId());
        assertNotNull(restoredLine);
        assertTrue(restoredLine.hasParametricTowerConfig());
        assertEquals(52.0, restoredLine.getParametricTowerConfig().parameters().height(), 0.01);
        assertEquals(2, restoredLine.getParametricTowerConfig().parameters().armLevelScales().size());
    }

    @Test
    void representativeDesignUsesParametricCompileForTransmissionStyle() {
        PowerLineFootprint line = line();
        PowerLineStylePresetCatalog.megaLattice().apply(line);

        PoleDesign representative = PowerLineStylePreset.resolveRepresentativeDesign(line);
        assertNotNull(representative);
        assertTrue(representative.isParametricMode());
        assertEquals(TowerParameterProfiles.MEGA_ID, representative.getGeneratorConfig().profileId());
    }

    @Test
    void towerFamilyMapsToExpectedProfile() {
        TowerGeneratorConfig config = PowerLineStyleParametricCatalog.forTowerFamilyId(
            TowerFamily.MONSTER_PYLON_ID);
        assertNotNull(config);
        assertEquals(TowerParameterProfiles.UHV_ID, config.profileId());
    }

    private static PowerLineFootprint line() {
        return new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(80, 0)));
    }

    private static PowerLineFootprint woodLine() {
        PowerLineFootprint line = line();
        PowerLineStylePresetCatalog.classicWood().apply(line);
        return line;
    }
}
