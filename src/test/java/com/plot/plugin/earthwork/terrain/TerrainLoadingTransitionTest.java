package com.plot.plugin.earthwork.terrain;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TerrainLoadingTransitionTest {
    private TerrainSnapshot.Column column(boolean loaded, int height, String block) {
        return new TerrainSnapshot.Column(new Vec2d(.5, .5), 0, 0, height, block, loaded);
    }

    @Test void unloadingAtY64InvalidatesPreviewEvenWhenHeightMatchesFallback() {
        var snapshot = TerrainSnapshot.forColumns(List.of(column(true, 64, "minecraft:stone")));
        var result = snapshot.compareWithCurrentTerrain("test", previous -> column(false, 64, ""));
        assertTrue(result.terrainChanged());
        assertFalse(result.matches());
        assertEquals(1, result.groundElevationChangedColumns());
    }

    @Test void newlyLoadedTerrainAlsoRequiresRecalculation() {
        var snapshot = TerrainSnapshot.forColumns(List.of(column(false, 64, "")));
        assertTrue(snapshot.compareWithCurrentTerrain("test",
            previous -> column(true, 64, "minecraft:stone")).terrainChanged());
    }

    @Test void stillUnloadedColumnsAreNotComparedAsKnownGround() {
        var snapshot = TerrainSnapshot.forColumns(List.of(column(false, 64, "")));
        assertTrue(snapshot.compareWithCurrentTerrain("test",
            previous -> column(false, 0, "")).matches());
    }

    @Test void cosmeticChangesRemainSoftWhileElevationChangesBlock() {
        var snapshot = TerrainSnapshot.forColumns(List.of(column(true, 64, "minecraft:grass_block")));
        var cosmetic = snapshot.compareWithCurrentTerrain("test",
            previous -> column(true, 64, "minecraft:dirt"));
        assertTrue(cosmetic.hasCosmeticSurfaceDrift());
        assertFalse(cosmetic.terrainChanged());
        assertTrue(snapshot.compareWithCurrentTerrain("test",
            previous -> column(true, 65, "minecraft:dirt")).terrainChanged());
    }
}
