package com.plot.plugin.powerline;

import com.plot.plugin.powerline.engineering.TerrainAvoidance;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import com.plot.plugin.road.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Terrain Avoidance 端到端：生成 + 碰撞检测 + 自动修正。 */
class TerrainAvoidanceIntegrationTest {

    @Test
    void classicWoodLineDetectsCollisionOverRollingHill() {
        PowerLineFootprint line = TerrainTestFixtures.rollingHillLine();
        TerrainSampler terrain = TerrainTestFixtures.rollingHill(64, 74, 30.0, 8.0);
        PowerLineGenerationResult result = TerrainTestFixtures.generate(line, terrain);

        assertTrue(TerrainAvoidance.hasTerrainIssues(TerrainTestFixtures.analyze(result, terrain)));
        assertTrue(TerrainTestFixtures.minimumClearance(result, terrain) < TerrainAvoidance.SAFETY_MARGIN_BLOCKS);
    }

    @Test
    void saggingSpanDetectsCollisionInValley() {
        PowerLineFootprint line = WireTestSupport.horizontalLine(60.0);
        line.setMaxPoleSpacing(80.0);
        PowerLineStylePresetCatalog.classicWood().apply(line);
        line.setSagRatio(0.28);

        TerrainSampler terrain = TerrainTestFixtures.valley(72, 66, 30.0, 18.0);
        PowerLineGenerationResult result = TerrainTestFixtures.generate(line, terrain);

        assertTrue(TerrainAvoidance.hasTerrainIssues(TerrainTestFixtures.analyze(result, terrain)));
    }

    @Test
    void flatTerrainLinePassesWithoutTerrainIssues() {
        PowerLineFootprint line = WireTestSupport.horizontalLine(60.0);
        line.setMaxPoleSpacing(80.0);
        PowerLineStylePresetCatalog.classicWood().apply(line);
        line.setSagRatio(0.03);

        PowerLineGenerationResult result = TerrainTestFixtures.generate(
            line,
            TerrainTestFixtures.flatTerrain(64));

        assertFalse(TerrainAvoidance.hasTerrainIssues(
            TerrainTestFixtures.analyze(result, TerrainTestFixtures.flatTerrain(64))));
    }

    @Test
    void fixLoopInsertsPoleForDesignedLineOnRollingHill() {
        PowerLineFootprint line = TerrainTestFixtures.rollingHillLine();
        TerrainSampler terrain = TerrainTestFixtures.rollingHill(64, 74, 30.0, 8.0);
        int polesBefore = PowerPoleLayoutUtils.computePoleSites(line).size();

        assertTrue(TerrainTestFixtures.applyTerrainFix(line, terrain, 1));
        assertFalse(line.getLayoutConstraints().isEmpty());
        assertTrue(PowerPoleLayoutUtils.computePoleSites(line).size() > polesBefore);
    }

    @Test
    void fixLoopDoesNotMutateLineWhenAlreadyClear() {
        PowerLineFootprint line = WireTestSupport.horizontalLine(60.0);
        line.setMaxPoleSpacing(80.0);
        PowerLineStylePresetCatalog.classicWood().apply(line);
        line.setSagRatio(0.03);

        assertTrue(TerrainTestFixtures.applyTerrainFix(line, TerrainTestFixtures.flatTerrain(64), 2));
        assertTrue(line.getLayoutConstraints().isEmpty());
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
        line.setMaxPoleSpacing(80.0);
        PowerLineStylePresetCatalog.classicWood().apply(line);
        line.setSagRatio(0.10);

        TerrainSampler terrain = TerrainTestFixtures.cliff(64, 82, 20.0);
        PowerLineGenerationResult result = TerrainTestFixtures.generate(line, terrain);

        assertTrue(TerrainAvoidance.hasTerrainIssues(TerrainTestFixtures.analyze(result, terrain)));
    }
}
