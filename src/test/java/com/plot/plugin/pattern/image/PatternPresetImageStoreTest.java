package com.plot.plugin.pattern.image;

import com.plot.plugin.pattern.model.ImagePatternConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternPresetImageStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void presetAssetSurvivesFootprintImageDeletion() throws IOException {
        Path source = tempDir.resolve("source.png");
        Files.write(source, new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47});
        Files.createDirectories(PatternImageStore.imagesDir(tempDir));
        String footprintRelative = "images/footprint-1.png";
        Files.copy(source, tempDir.resolve(footprintRelative));

        ImagePatternConfig config = new ImagePatternConfig();
        config.setImagePath(footprintRelative);
        config.setImageWidth(1);
        config.setImageHeight(1);

        String presetId = "preset-abc";
        String assetPath = PatternPresetImageStore.copyFootprintImageToPresetAsset(
            tempDir, presetId, config);
        assertTrue(assetPath.startsWith("preset-assets/"));
        assertTrue(Files.exists(tempDir.resolve(assetPath)));

        PatternImageStore.deleteImage(tempDir, footprintRelative);
        assertFalse(Files.exists(tempDir.resolve(footprintRelative)));
        assertTrue(Files.exists(tempDir.resolve(assetPath)));
    }

    @Test
    void deletePresetAssetRemovesFile() throws IOException {
        Path source = tempDir.resolve("source.png");
        Files.write(source, new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47});
        Files.createDirectories(PatternImageStore.imagesDir(tempDir));
        String footprintRelative = "images/fp.png";
        Files.copy(source, tempDir.resolve(footprintRelative));

        ImagePatternConfig config = new ImagePatternConfig();
        config.setImagePath(footprintRelative);
        config.setImageWidth(1);
        config.setImageHeight(1);

        String assetPath = PatternPresetImageStore.copyFootprintImageToPresetAsset(
            tempDir, "preset-del", config);
        PatternPresetImageStore.deletePresetAsset(tempDir, assetPath);
        assertFalse(Files.exists(tempDir.resolve(assetPath)));
    }
}
