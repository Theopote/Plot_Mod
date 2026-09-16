package com.plot.plugin.pattern.model;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.pattern.model.PatternSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PatternProjectTest {

    @Test
    void jsonRoundTripPreservesAllFields() {
        PatternProject project = new PatternProject();
        PatternFootprint footprint = new PatternFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 8),
            new Vec2d(0, 8)
        ));
        footprint.setName("Plaza");
        ProceduralPatternConfig pattern = new ProceduralPatternConfig();
        pattern.setType(ProceduralPatternConfig.PatternType.STRIPES);
        pattern.setMaterials(List.of("minecraft:stone", "minecraft:quartz_block", "minecraft:deepslate"));
        pattern.setTileSize(3.0);
        pattern.setAngleDegrees(45.0);
        pattern.setMosaicPrimaryRatio(0.6);
        footprint.setPattern(pattern);
        project.addFootprint(footprint);

        PatternProject restored = PatternProject.fromJson(project.toJson());
        PatternFootprint restoredFootprint = restored.getFootprint(footprint.getId());
        assertNotNull(restoredFootprint);
        assertEquals("Plaza", restoredFootprint.getName());
        assertEquals(4, restoredFootprint.getOuterPoints().size());
        assertEquals(ProceduralPatternConfig.PatternType.STRIPES, restoredFootprint.getPattern().getType());
        assertEquals(3, restoredFootprint.getPattern().getMaterials().size());
        assertEquals(3.0, restoredFootprint.getPattern().getTileSize(), 1e-6);
        assertEquals(45.0, restoredFootprint.getPattern().getAngleDegrees(), 1e-6);
        assertEquals(0.6, restoredFootprint.getPattern().getMosaicPrimaryRatio(), 1e-6);
    }

    @Test
    void jsonRoundTripPreservesImagePattern() {
        PatternProject project = new PatternProject();
        PatternFootprint footprint = new PatternFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(6, 0),
            new Vec2d(6, 4)
        ));
        footprint.setSource(PatternSource.IMAGE);
        ImagePatternConfig imagePattern = footprint.getImagePattern();
        imagePattern.setImagePath("images/test-footprint.png");
        imagePattern.setImageWidth(64);
        imagePattern.setImageHeight(32);
        imagePattern.setFitMode(ImagePatternConfig.FitMode.CONTAIN);
        imagePattern.setTileScale(2.0);
        imagePattern.setAlphaThreshold(200);
        imagePattern.setPaletteBlocks(List.of("minecraft:white_wool", "minecraft:black_wool"));
        footprint.setImagePattern(imagePattern);
        project.addFootprint(footprint);

        PatternProject restored = PatternProject.fromJson(project.toJson());
        PatternFootprint restoredFootprint = restored.getFootprint(footprint.getId());
        assertNotNull(restoredFootprint);
        assertEquals(PatternSource.IMAGE, restoredFootprint.getSource());
        assertEquals("images/test-footprint.png", restoredFootprint.getImagePattern().getImagePath());
        assertEquals(64, restoredFootprint.getImagePattern().getImageWidth());
        assertEquals(ImagePatternConfig.FitMode.CONTAIN, restoredFootprint.getImagePattern().getFitMode());
        assertEquals(2.0, restoredFootprint.getImagePattern().getTileScale(), 1e-6);
        assertEquals(200, restoredFootprint.getImagePattern().getAlphaThreshold());
        assertEquals(2, restoredFootprint.getImagePattern().getPaletteBlocks().size());
    }

    @Test
    void saveAndLoadFromFile(@TempDir Path tempDir) throws IOException {
        PatternProject project = new PatternProject();
        PatternFootprint footprint = new PatternFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(4, 0),
            new Vec2d(4, 4)
        ));
        ProceduralPatternConfig pattern = footprint.getPattern();
        pattern.setType(ProceduralPatternConfig.PatternType.MOSAIC);
        footprint.setPattern(pattern);
        project.addFootprint(footprint);

        Path file = tempDir.resolve("pattern.json");
        project.saveTo(file);
        PatternProject loaded = PatternProject.loadFrom(file);
        assertEquals(1, loaded.getFootprintCount());
        assertEquals(
            ProceduralPatternConfig.PatternType.MOSAIC,
            loaded.getFootprints().values().iterator().next().getPattern().getType());
    }
}
