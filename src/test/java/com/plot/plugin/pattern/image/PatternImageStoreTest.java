package com.plot.plugin.pattern.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternImageStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void importReplacingDeletesPreviousExtension() throws IOException {
        String footprintId = "fp-1";
        Path jpgSource = writeImage(tempDir.resolve("logo.jpg"), "jpg");
        Path pngSource = writeImage(tempDir.resolve("logo.png"), "png");

        PatternImageStore.ImportedImage first = PatternImageStore.importReplacing(
            tempDir, footprintId, jpgSource, null);
        Path oldFile = tempDir.resolve(first.relativePath());
        assertTrue(Files.exists(oldFile));

        PatternImageStore.ImportedImage second = PatternImageStore.importReplacing(
            tempDir, footprintId, pngSource, first.relativePath());

        assertTrue(Files.exists(tempDir.resolve(second.relativePath())));
        assertFalse(Files.exists(oldFile));
    }

    @Test
    void rejectsUnknownExtensionInsteadOfRenamingToPng() throws IOException {
        Path source = tempDir.resolve("logo.xyz");
        writeImage(source, "png");

        assertThrows(IOException.class, () -> PatternImageStore.importImage(
            tempDir, "fp-1", source));
        assertFalse(Files.exists(tempDir.resolve("images/fp-1.png")));
        assertFalse(Files.exists(tempDir.resolve("images/fp-1.xyz")));
    }

    private static Path writeImage(Path file, String format) throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, format, file.toFile());
        return file;
    }
}
