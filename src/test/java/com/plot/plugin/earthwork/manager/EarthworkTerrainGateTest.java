package com.plot.plugin.earthwork.manager;

import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 施工地形门禁：有世界时无法校验必须拒绝。 */
class EarthworkTerrainGateTest {

    @Test
    void absentWorldSkipsWhenNoComparison() {
        assertNull(EarthworkBuildManager.rejectIfTerrainUnsafe(false, null));
    }

    @Test
    void absentWorldStillRejectsExplicitChangedComparison() {
        assertNotNull(EarthworkBuildManager.rejectIfTerrainUnsafe(
            false, new TerrainSnapshot.ComparisonResult(false, 3, 10)));
    }

    @Test
    void absentWorldAllowsUnchangedComparison() {
        assertNull(EarthworkBuildManager.rejectIfTerrainUnsafe(
            false, new TerrainSnapshot.ComparisonResult(true, 0, 10)));
    }

    @Test
    void presentWorldRejectsUnavailableComparison() {
        assertNotNull(EarthworkBuildManager.rejectIfTerrainUnsafe(true, null));
    }

    @Test
    void presentWorldRejectsChangedTerrain() {
        assertNotNull(EarthworkBuildManager.rejectIfTerrainUnsafe(
            true, new TerrainSnapshot.ComparisonResult(false, 3, 10)));
    }

    @Test
    void presentWorldAllowsUnchangedTerrain() {
        assertNull(EarthworkBuildManager.rejectIfTerrainUnsafe(
            true, new TerrainSnapshot.ComparisonResult(true, 0, 10)));
    }
}
