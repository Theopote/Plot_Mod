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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        pattern.setCenterOverride(new Vec2d(5.0, 4.0));
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
        assertEquals(5.0, restoredFootprint.getPattern().getCenterOverride().x, 1e-6);
        assertEquals(4.0, restoredFootprint.getPattern().getCenterOverride().y, 1e-6);
    }

    @Test
    void jsonRoundTripPreservesRadialSectorCount() {
        PatternProject project = new PatternProject();
        PatternFootprint footprint = new PatternFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)));
        ProceduralPatternConfig pattern = new ProceduralPatternConfig();
        pattern.setType(ProceduralPatternConfig.PatternType.RADIAL);
        pattern.setRadialSectorCount(8);
        footprint.setPattern(pattern);
        project.addFootprint(footprint);

        PatternProject restored = PatternProject.fromJson(project.toJson());
        assertEquals(8, restored.getFootprint(footprint.getId()).getPattern().getRadialSectorCount());
    }

    @Test
    void legacyRadialTileSizeMigratesToSectorCount() {
        String json = """
            {
              "footprints": [{
                "id": "fp-radial",
                "outerPoints": [{"x":0,"y":0},{"x":10,"y":0},{"x":10,"y":10},{"x":0,"y":10}],
                "pattern": {"type":"RADIAL","materials":["a","b"],"tileSize":2.0}
              }]
            }
            """;

        PatternProject restored = PatternProject.fromJson(json);
        assertEquals(
            12,
            restored.getFootprint("fp-radial").getPattern().getRadialSectorCount());
    }

    @Test
    void jsonRoundTripPreservesOffsetDensityAndBorder() {
        PatternProject project = new PatternProject();
        PatternFootprint footprint = new PatternFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(8, 0),
            new Vec2d(8, 8),
            new Vec2d(0, 8)));
        ProceduralPatternConfig pattern = footprint.getPattern();
        pattern.setType(ProceduralPatternConfig.PatternType.DIAMOND);
        pattern.setOffset(new Vec2d(4.0, 7.0));
        pattern.setDensity(1.8);
        pattern.setAngleDegrees(45.0);
        footprint.setPattern(pattern);

        PatternBorderConfig border = new PatternBorderConfig();
        border.setEnabled(true);
        border.setBorderWidth(2.0);
        border.setOuterBorder(true);
        border.setBorderMaterial("minecraft:andesite");
        footprint.setBorderConfig(border);
        project.addFootprint(footprint);

        PatternProject restored = PatternProject.fromJson(project.toJson());
        PatternFootprint restoredFootprint = restored.getFootprint(footprint.getId());
        assertNotNull(restoredFootprint);
        assertEquals(4.0, restoredFootprint.getPattern().getOffset().x, 1e-6);
        assertEquals(7.0, restoredFootprint.getPattern().getOffset().y, 1e-6);
        assertEquals(1.8, restoredFootprint.getPattern().getDensity(), 1e-6);
        assertTrue(restoredFootprint.getBorderConfig().isEnabled());
        assertEquals(2.0, restoredFootprint.getBorderConfig().getBorderWidth(), 1e-6);
        assertEquals("minecraft:andesite", restoredFootprint.getBorderConfig().getPrimaryBorderMaterial());
    }

    @Test
    void loadsLegacyBorderMaterialsList() {
        String json = """
            {
              "schemaVersion": 1,
              "footprints": [{
                "id": "fp-1",
                "name": "Test",
                "outerPoints": [{"x":0,"y":0},{"x":4,"y":0},{"x":4,"y":4},{"x":0,"y":4}],
                "source": "PROCEDURAL",
                "pattern": {"type":"CHECKERBOARD","materials":["a","b"],"tileSize":1.0},
                "border": {
                  "style": "NONE",
                  "borderMaterials": ["minecraft:brick"],
                  "borderWidth": 1.5,
                  "innerBorder": false,
                  "outerBorder": true,
                  "cornerRadius": 2.0,
                  "enabled": true
                }
              }]
            }
            """;
        PatternProject project = PatternProject.fromJson(json);
        PatternBorderConfig border = project.getFootprint("fp-1").getBorderConfig();
        assertTrue(border.isEnabled());
        assertEquals("minecraft:brick", border.getBorderMaterial());
        assertEquals(1.5, border.getBorderWidth(), 1e-6);
    }

    @Test
    void jsonRoundTripPreservesHoles() {
        PatternProject project = new PatternProject();
        PatternFootprint footprint = new PatternFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)));
        footprint.setHoles(List.of(List.of(
            new Vec2d(3, 3),
            new Vec2d(7, 3),
            new Vec2d(7, 7),
            new Vec2d(3, 7))));
        project.addFootprint(footprint);

        PatternProject restored = PatternProject.fromJson(project.toJson());
        PatternFootprint restoredFootprint = restored.getFootprint(footprint.getId());
        assertNotNull(restoredFootprint);
        assertEquals(1, restoredFootprint.getHoles().size());
        assertEquals(4, restoredFootprint.getHoles().getFirst().size());
        assertTrue(restoredFootprint.computeArea() < 100.0);
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
