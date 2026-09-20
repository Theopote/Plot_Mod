package com.plot.plugin.powerline;

import com.plot.plugin.powerline.terrain.TerrainFitService;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.test.world.IdentityCoordinateService;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import com.plot.core.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TerrainFitService 端到端：生成 + 碰撞检测 + 自动修正。 */
class TerrainFitServiceIntegrationTest {

    @Test
    void classicWoodLineDetectsCollisionOverRollingHill() {
        PowerLineFootprint line = TerrainTestFixtures.rollingHillLine();
        TerrainSampler terrain = TerrainTestFixtures.rollingHill(64, 74, 30.0, 8.0);
        PowerLineGenerationResult result = TerrainTestFixtures.generate(line, terrain);

        assertTrue(TerrainTestFixtures.analyze(result, terrain).hasVisualConflicts());
        assertTrue(TerrainTestFixtures.minimumClearance(result, terrain) < TerrainFitService.SAFETY_MARGIN_BLOCKS);
    }

    @Test
    void saggingSpanDetectsCollisionInValley() {
        PowerLineFootprint line = WireTestSupport.horizontalLine(60.0);
        PowerLineStylePresetCatalog.classicWood().apply(line);
        line.setMaxPoleSpacing(80.0);
        line.setSagRatio(0.28);

        TerrainSampler terrain = TerrainTestFixtures.valley(72, 66, 30.0, 18.0);
        PowerLineGenerationResult result = TerrainTestFixtures.generate(line, terrain);

        assertTrue(TerrainTestFixtures.analyze(result, terrain).hasVisualConflicts());
    }

    @Test
    void flatTerrainLinePassesWithoutTerrainIssues() {
        PowerLineFootprint line = WireTestSupport.horizontalLine(60.0);
        PowerLineStylePresetCatalog.classicWood().apply(line);
        line.setMaxPoleSpacing(80.0);
        line.setSagRatio(0.03);

        PowerLineGenerationResult result = TerrainTestFixtures.generate(
            line,
            TerrainTestFixtures.flatTerrain(64));

        assertFalse(TerrainTestFixtures.analyze(result, TerrainTestFixtures.flatTerrain(64)).hasVisualConflicts());
    }

    @Test
    void fixLoopInsertsPoleForDesignedLineOnRollingHill() {
        PowerLineFootprint line = TerrainTestFixtures.rollingHillLine();
        TerrainSampler terrain = TerrainTestFixtures.rollingHill(64, 74, 30.0, 8.0);
        int polesBefore = PowerPoleLayoutUtils.computePoleSites(line, IdentityCoordinateService.INSTANCE).size();

        assertTrue(TerrainTestFixtures.applyTerrainFix(line, terrain, 1));
        assertTrue(line.getLayoutConstraints().isEmpty());
        assertFalse(line.getDerivedLayout().autoLayoutConstraints().isEmpty());
        assertTrue(PowerPoleLayoutUtils.computePoleSites(line, IdentityCoordinateService.INSTANCE).size() > polesBefore);
    }

    @Test
    void fixLoopDoesNotMutateLineWhenAlreadyClear() {
        PowerLineFootprint line = WireTestSupport.horizontalLine(60.0);
        PowerLineStylePresetCatalog.classicWood().apply(line);
        line.setMaxPoleSpacing(80.0);
        line.setSagRatio(0.03);

        assertTrue(TerrainTestFixtures.applyTerrainFix(line, TerrainTestFixtures.flatTerrain(64), 2));
        assertTrue(line.getLayoutConstraints().isEmpty());
        assertTrue(line.getDerivedLayout().isEmpty());
    }

    @Test
    void fixLoopImprovesMinimumClearanceOnRollingHill() {
        PowerLineFootprint line = TerrainTestFixtures.rollingHillLine();
        TerrainSampler terrain = TerrainTestFixtures.rollingHill(64, 74, 30.0, 8.0);
        double before = TerrainTestFixtures.minimumClearance(TerrainTestFixtures.generate(line, terrain), terrain);

        assertTrue(TerrainTestFixtures.applyTerrainFix(line, terrain, 4));

        double after = TerrainTestFixtures.minimumClearance(TerrainTestFixtures.generate(line, terrain), terrain);
        assertTrue(after > before);
    }

    @Test
    void cliffCrossingCanFailClearanceWithoutMidPole() {
        PowerLineFootprint line = WireTestSupport.horizontalLine(40.0);
        PowerLineStylePresetCatalog.classicWood().apply(line);
        line.setMaxPoleSpacing(80.0);
        line.setSagRatio(0.10);

        TerrainSampler terrain = TerrainTestFixtures.cliff(64, 82, 20.0);
        PowerLineGenerationResult result = TerrainTestFixtures.generate(line, terrain);

        assertTrue(TerrainTestFixtures.analyze(result, terrain).hasVisualConflicts());
    }
}
