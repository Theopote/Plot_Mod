package com.plot.plugin.earthwork.model;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkProjectTest {

    @Test
    void jsonRoundTripPreservesAllFields() {
        EarthworkProject project = new EarthworkProject();
        GradingRegion region = new GradingRegion(List.of(
            new Vec2d(0, 0),
            new Vec2d(12, 0),
            new Vec2d(12, 10),
            new Vec2d(0, 10)
        ));
        region.setName("North Pad");
        region.setAutoBalance(false);
        region.setManualTargetElevation(68);
        region.setFillFactor(1.25f);
        region.setCutExposeMaterial("minecraft:sand");
        region.setFillMaterial("minecraft:grass_block");
        region.setGridSize(3);
        region.setSurfaceMode(GradingSurfaceMode.FIXED_SLOPE);
        region.setSlopeDirectionDegrees(90.0);
        region.setSlopePitchRatio(8);
        region.setSlopeAnchorCanvas(new Vec2d(6, 5));
        region.setSlopeAnchorElevation(70);
        region.setThreePointControl(0, new Vec2d(0, 0), 60);
        region.setThreePointControl(1, new Vec2d(12, 0), 64);
        region.setThreePointControl(2, new Vec2d(0, 10), 62);
        region.setFitSlopeBalanceCutFill(false);
        project.addRegion(region);

        EarthworkProject restored = EarthworkProject.fromJson(project.toJson());
        GradingRegion restoredRegion = restored.getRegion(region.getId());
        assertNotNull(restoredRegion);
        assertEquals("North Pad", restoredRegion.getName());
        assertEquals(false, restoredRegion.isAutoBalance());
        assertEquals(68, restoredRegion.getManualTargetElevation());
        assertEquals(1.25f, restoredRegion.getFillFactor(), 1e-6f);
        assertEquals("minecraft:sand", restoredRegion.getCutExposeMaterial());
        assertEquals("minecraft:grass_block", restoredRegion.getFillMaterial());
        assertEquals(3, restoredRegion.getGridSize());
        assertEquals(GradingSurfaceMode.FIXED_SLOPE, restoredRegion.getSurfaceMode());
        assertEquals(90.0, restoredRegion.getSlopeDirectionDegrees(), 1e-6);
        assertEquals(8, restoredRegion.getSlopePitchRatio());
        assertEquals(70, restoredRegion.getSlopeAnchorElevation());
        assertEquals(6.0, restoredRegion.getSlopeAnchorCanvas().x, 1e-6);
        assertEquals(5.0, restoredRegion.getSlopeAnchorCanvas().y, 1e-6);
        assertEquals(60, restoredRegion.getThreePointElevation(0));
        assertEquals(64, restoredRegion.getThreePointElevation(1));
        assertEquals(62, restoredRegion.getThreePointElevation(2));
        assertEquals(false, restoredRegion.isFitSlopeBalanceCutFill());
        assertEquals(4, restoredRegion.getOuterPoints().size());
        assertTrue(restoredRegion.computeArea() > 0.0);
    }

    @Test
    void corruptJsonThrowsInsteadOfSilentEmptyProject() {
        assertThrows(IllegalArgumentException.class, () -> EarthworkProject.fromJson("{not-valid-json"));
    }

    @Test
    void loadFromCorruptFileThrowsIoException(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("broken.json");
        Files.writeString(file, "{broken");
        assertThrows(IOException.class, () -> EarthworkProject.loadFrom(file));
    }

    @Test
    void saveToIsAtomicAndRoundTrips(@TempDir Path dir) throws IOException {
        EarthworkProject project = new EarthworkProject();
        GradingRegion region = new GradingRegion(List.of(
            new Vec2d(0, 0),
            new Vec2d(8, 0),
            new Vec2d(8, 6),
            new Vec2d(0, 6)
        ));
        region.setName("Pad");
        project.addRegion(region);

        Path file = dir.resolve("earthwork.json");
        project.saveTo(file);
        assertTrue(Files.exists(file));
        assertFalse(Files.exists(dir.resolve("earthwork.json.tmp")));

        EarthworkProject loaded = EarthworkProject.loadFrom(file);
        assertEquals(1, loaded.getRegionCount());
        assertEquals("Pad", loaded.getRegion(region.getId()).getName());
    }
}
