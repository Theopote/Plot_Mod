package com.plot.plugin.pattern.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.pattern.PatternGenerationIssue;
import com.plot.plugin.pattern.model.ImagePatternConfig;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.model.PatternSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternMaterialResolverFactoryTest {

    @TempDir
    Path pluginDataDir;

    @Test
    void imageNotImportedReportsIssue() {
        PatternFootprint footprint = sampleFootprint();
        footprint.setSource(PatternSource.IMAGE);

        PatternMaterialResolverFactory.Outcome outcome =
            PatternMaterialResolverFactory.forFootprint(footprint, pluginDataDir);

        assertFalse(outcome.canGenerate());
        assertEquals(PatternGenerationIssue.IMAGE_NOT_IMPORTED, outcome.issue());
    }

    @Test
    void customEmptyPaletteReportsIssue() throws IOException {
        PatternFootprint footprint = sampleFootprint();
        footprint.setSource(PatternSource.IMAGE);
        ImagePatternConfig image = footprint.getImagePattern();
        image.setImagePath("images/test.png");
        image.setImageWidth(1);
        image.setImageHeight(1);
        image.setPaletteBlocks(java.util.List.of());
        image.setMaterialMatchMode(ImagePatternConfig.MaterialMatchMode.CUSTOM);
        footprint.setImagePattern(image);

        Path imageFile = pluginDataDir.resolve("images/test.png");
        java.nio.file.Files.createDirectories(imageFile.getParent());
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", imageFile.toFile());

        PatternMaterialResolverFactory.Outcome outcome =
            PatternMaterialResolverFactory.forFootprint(footprint, pluginDataDir);

        assertFalse(outcome.canGenerate());
        assertEquals(PatternGenerationIssue.EMPTY_PALETTE, outcome.issue());
    }

    @Test
    void proceduralSourceBuildsResolver() {
        PatternFootprint footprint = sampleFootprint();
        PatternMaterialResolverFactory.Outcome outcome =
            PatternMaterialResolverFactory.forFootprint(footprint, pluginDataDir);

        assertTrue(outcome.canGenerate());
        assertEquals(PatternGenerationIssue.NONE, outcome.issue());
    }

    private static PatternFootprint sampleFootprint() {
        PatternFootprint footprint = new PatternFootprint(java.util.List.of(
            new Vec2d(0, 0),
            new Vec2d(4, 0),
            new Vec2d(4, 4),
            new Vec2d(0, 4)));
        footprint.setSource(PatternSource.PROCEDURAL);
        return footprint;
    }
}
