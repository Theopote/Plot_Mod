package com.plot.plugin.pattern.image;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.pattern.model.ImagePatternConfig;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.model.PatternProject;
import com.plot.plugin.pattern.model.PatternProjectHistory;
import com.plot.plugin.pattern.model.PatternSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternImageStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void importCreatesImmutableAssetPerImport() throws IOException {
        String footprintId = "fp-1";
        Path jpgSource = writeImage(tempDir.resolve("logo.jpg"), "jpg");
        Path pngSource = writeImage(tempDir.resolve("logo.png"), "png");

        PatternImageStore.ImportedImage first = PatternImageStore.importImage(
            tempDir, footprintId, jpgSource);
        Path oldFile = tempDir.resolve(first.relativePath());
        assertTrue(Files.exists(oldFile));
        assertTrue(first.relativePath().startsWith("images/fp-1/"));

        PatternImageStore.ImportedImage second = PatternImageStore.importImage(
            tempDir, footprintId, pngSource);

        assertTrue(Files.exists(tempDir.resolve(second.relativePath())));
        assertTrue(Files.exists(oldFile));
        assertNotEquals(first.relativePath(), second.relativePath());
    }

    @Test
    void undoCanRestorePreviousImageAsset() throws IOException {
        String footprintId = "fp-undo";
        Path firstSource = writeImage(tempDir.resolve("first.png"), "png");
        Path secondSource = writeImage(tempDir.resolve("second.png"), "png");

        PatternProject project = new PatternProject();
        PatternFootprint footprint = new PatternFootprint(footprintId, List.of(
            new Vec2d(0, 0),
            new Vec2d(4, 0),
            new Vec2d(4, 4),
            new Vec2d(0, 4)));
        footprint.setSource(PatternSource.IMAGE);
        project.addFootprint(footprint);

        ImagePatternConfig imagePattern = footprint.getImagePattern();
        PatternImageStore.ImportedImage first = PatternImageStore.importImage(
            tempDir, footprintId, firstSource);
        first.applyTo(imagePattern);
        footprint.setImagePattern(imagePattern);

        PatternProjectHistory history = new PatternProjectHistory();
        history.push(PatternProject.fromJson(project.toJson()));

        PatternImageStore.ImportedImage second = PatternImageStore.importImage(
            tempDir, footprintId, secondSource);
        second.applyTo(imagePattern);
        footprint.setImagePattern(imagePattern);
        assertEquals(second.relativePath(), footprint.getImagePattern().getImagePath());

        PatternProject restored = history.undo(project);
        String restoredPath = restored.getFootprint(footprintId).getImagePattern().getImagePath();
        assertEquals(first.relativePath(), restoredPath);
        assertTrue(Files.exists(tempDir.resolve(restoredPath)));
        assertTrue(PatternImageStore.loadRaster(tempDir, restored.getFootprint(footprintId).getImagePattern()).isPresent());
    }

    @Test
    void rejectsUnknownExtensionInsteadOfRenamingToPng() throws IOException {
        Path source = tempDir.resolve("logo.xyz");
        writeImage(source, "png");

        assertThrows(IOException.class, () -> PatternImageStore.importImage(
            tempDir, "fp-1", source));
        assertFalse(Files.exists(tempDir.resolve("images/fp-1.png")));
        assertFalse(Files.exists(tempDir.resolve("images/fp-1/logo.xyz")));
    }

    private static Path writeImage(Path file, String format) throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, format, file.toFile());
        return file;
    }
}
